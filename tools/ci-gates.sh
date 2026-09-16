#!/usr/bin/env bash
# ============================================================================
# CI 静态门禁：ktlint（风格）+ detekt（静态分析）
#
# 用法：
#   bash tools/ci-gates.sh                        # 各工具用默认模式（见下）
#   GATES_MODE=report bash tools/ci-gates.sh      # 全局只报告、不拦截（收敛规则时用）
#   DETEKT_MODE=block bash tools/ci-gates.sh      # 单独把 detekt 改成拦截
#   GATES_CACHE_DIR=/tmp/g bash tools/ci-gates.sh # 指定工具缓存目录
#
# 当前默认：ktlint = block（预检零违规，可直接拦），detekt = report
#   —— detekt 1.23.8 内置的 Kotlin 编译器是 2.0.21（本工程 2.4.10），且规则噪声需要据实际
#      报告收敛；先只报告，确认解析正常、噪声可控后再把 DETEKT_MODE 改成 block（一次改动，
#      不需要改 workflow）。见 devlog/2026-09-15.md §14。
#
# 为什么是「脚本 + 独立二进制」，而不是 Gradle 插件
# ------------------------------------------------
# 1. 这套工具链的 Gradle 插件对 AGP 9 / Gradle 9.7 / Kotlin 2.4 这种新组合支持滞后，
#    一旦插件在配置阶段报错，整个构建（含 release 打包）都会被拖下水；
# 2. 工具版本与校验和固定在**本文件**里，升级是一次显式、可 review 的改动；
# 3. 脚本可以在没有 Android SDK 的机器上跑，CI 与本地行为一致。
#
# 报告一律同时写文件（build/reports/gates/）+ 打印到 stdout：artifact 常常拿不到，
# 日志才是最后能看的地方（与 lint 的 textOutput=stdout 同样的理由）。
#
# 升级工具时记得同步改 .github/workflows/build.yml 里 actions/cache 的 key。
# ============================================================================
set -uo pipefail

KTLINT_VERSION="1.8.0"
# ktlint 官方 release 资产 `ktlint`（GraalVM 原生二进制，不需要 JVM）的 sha256
KTLINT_SHA256="a3fd620207d5c40da6ca789b95e7f823c54e854b7fade7f613e91096a3706d75"
KTLINT_URL="https://github.com/pinterest/ktlint/releases/download/${KTLINT_VERSION}/ktlint"

DETEKT_VERSION="1.23.8"
# detekt 从 Maven Central 取（同时取 .sha256 校验，比 GitHub release 更稳）
DETEKT_URL="https://repo1.maven.org/maven2/io/gitlab/arturbosch/detekt/detekt-cli/${DETEKT_VERSION}/detekt-cli-${DETEKT_VERSION}-all.jar"

# 模式：block = 有违规就退出码 1（CI 会红）；report = 只打印与写报告，不拦截。
# GATES_MODE 非空时覆盖下面两个工具的独立设置。
GATES_MODE="${GATES_MODE:-}"
KTLINT_MODE="${KTLINT_MODE:-block}"
DETEKT_MODE="${DETEKT_MODE:-report}"
[ -n "$GATES_MODE" ] && { KTLINT_MODE="$GATES_MODE"; DETEKT_MODE="$GATES_MODE"; }
CACHE_DIR="${GATES_CACHE_DIR:-$HOME/.cache/chileme-gates}"
REPORT_DIR="${GATES_REPORT_DIR:-build/reports/gates}"
SRC_DIR="${GATES_SRC_DIR:-app/src}"

KTLINT_BIN="$CACHE_DIR/ktlint-$KTLINT_VERSION"
DETEKT_JAR="$CACHE_DIR/detekt-cli-$DETEKT_VERSION-all.jar"

log()  { printf '\n── %s\n' "$*"; }
fail() { printf '::error::%s\n' "$*" >&2; exit 2; }

[ -d "$SRC_DIR" ] || fail "找不到源码目录 $SRC_DIR（请在仓库根目录运行）"
mkdir -p "$CACHE_DIR" "$REPORT_DIR"

# ---------------------------------------------------------------- 工具准备
prepare_ktlint() {
    [ -x "$KTLINT_BIN" ] && return 0
    log "下载 ktlint $KTLINT_VERSION（原生二进制，约 68 MB）"
    curl -fsSL --retry 3 --retry-delay 2 -o "$KTLINT_BIN.part" "$KTLINT_URL" \
        || fail "下载 ktlint 失败：$KTLINT_URL"
    echo "$KTLINT_SHA256  $KTLINT_BIN.part" | sha256sum -c --status - \
        || fail "ktlint 校验和不匹配（下载损坏或资产已被替换）"
    mv "$KTLINT_BIN.part" "$KTLINT_BIN"
    chmod +x "$KTLINT_BIN"
}

prepare_detekt() {
    [ -f "$DETEKT_JAR" ] && return 0
    command -v java >/dev/null 2>&1 || fail "detekt 需要 JVM（java 不在 PATH 上）"
    log "下载 detekt $DETEKT_VERSION（约 67 MB）"
    curl -fsSL --retry 3 --retry-delay 2 -o "$DETEKT_JAR.part" "$DETEKT_URL" \
        || fail "下载 detekt 失败：$DETEKT_URL"
    curl -fsSL --retry 3 --retry-delay 2 -o "$DETEKT_JAR.part.sha256" "$DETEKT_URL.sha256" \
        || fail "下载 detekt 校验和失败：$DETEKT_URL.sha256"
    local expected actual
    expected="$(tr -d ' \t\r\n' < "$DETEKT_JAR.part.sha256")"
    actual="$(sha256sum "$DETEKT_JAR.part" | cut -d' ' -f1)"
    [ -n "$expected" ] && [ "$expected" = "$actual" ] \
        || fail "detekt 校验和不匹配（期望 ${expected:-空}，实际 $actual）"
    mv "$DETEKT_JAR.part" "$DETEKT_JAR"
}

# ---------------------------------------------------------------- ktlint
# ---------------------------------------------------------------- annotations
# 把报告里的发现转成 GitHub check-run annotation（`::warning file=…::…`）。
#
# 为什么非做不可：CI 的**日志与 artifact** 都托管在 results-receiver.actions.githubusercontent.com /
# *.blob.core.windows.net，在受限网络里（本项目的开发沙箱就是）**下载不到**；而 annotation 走
# api.github.com，是 `gh api repos/OWNER/REPO/check-runs/<job-id>/annotations` 能读回来的唯一通道。
# report 模式下 detekt 的发现原本只存在于日志里 —— 对读不到日志的人等于没有。2026-09-16 收敛
# complexity 规则时就是靠这个把清单捞回来的。
#
# GitHub 每个级别最多保留 10 条 annotation，所以**按规则聚合**：一条 annotation = 一个规则 +
# 计数 + 前若干个位点（截断到 800 字符），外加一条汇总 notice。要看完整清单仍得靠 artifact。
emit_annotations() {
    local tool="$1" report="$2"
    [ -s "$report" ] || return 0
    awk -v tool="$tool" '
        {
            if ($0 !~ /\.kt:[0-9]+:/) next
            split($0, p, ":")
            path = p[1]; line = p[2]
            i = index(path, "app/src"); if (i > 1) path = substr(path, i)   # detekt 可能给绝对路径
            rule = "unknown"
            if (match($0, /\(([a-z-]+:)?[A-Za-z0-9_-]+\)/)) {          # ktlint plain: (standard:rule-id)
                rule = substr($0, RSTART, RLENGTH); gsub(/[()]/, "", rule); sub(/^[a-z-]+:/, "", rule)
            } else if (match($0, /\.kt:[0-9]+(:[0-9]+)?: [A-Za-z][A-Za-z0-9]* - /)) {   # detekt txt: Rule - msg
                rule = substr($0, RSTART, RLENGTH)
                sub(/^\.kt:[0-9]+(:[0-9]+)?: /, "", rule); sub(/ - $/, "", rule)
            }
            cnt[rule]++; if (cnt[rule] == 1) nrules++; total++
            if (length(loc[rule]) < 800) loc[rule] = loc[rule] (loc[rule] == "" ? "" : "; ") path ":" line
        }
        END {
            if (!total) exit
            printf "::notice title=%s 汇总::%d 条发现，涉及 %d 个规则（逐规则见后续 warning）\n", tool, total, nrules
            shown = 0
            for (r in cnt) {
                if (shown >= 8) {
                    printf "::notice title=%s 其余规则::还有 %d 个规则未逐条列出，完整清单见 artifact\n", tool, nrules - 8
                    break
                }
                msg = loc[r]; gsub(/%/, "%25", msg)
                printf "::warning file=tools/ci-gates.sh,line=1,title=%s %s (%d 处)::%s\n", tool, r, cnt[r], msg
                shown++
            }
        }
    ' "$report"
}

run_ktlint() {
    # 防呆（2026-09-15 实测教训）：.editorconfig 缺失时 ktlint 会用**默认全量规则集**跑，
    # 于是报出上百条纯格式违规、门禁一路飘红，而日志里根本看不出「只是少了个配置文件」。
    if [ ! -f .editorconfig ]; then
        echo "::error::找不到 .editorconfig —— ktlint 会用默认全量规则集跑出上百条格式违规。"
        echo "          门禁三件套必须同时在目标分支上：tools/ci-gates.sh / .editorconfig / detekt.yml（见 docs/WORKFLOW.md §3）。"
        # 用 90 作为「本脚本前置检查失败」的哨兵码：detekt 自己的 exit=2 含义是
        # 「发现问题数超过 maxIssues」（正常判红），不能混用（2026-09-15 实测踩到）。
        return 90
    fi
    prepare_ktlint
    log "ktlint $KTLINT_VERSION（规则见 .editorconfig）"
    local files=()
    while IFS= read -r f; do files+=("$f"); done < <(find "$SRC_DIR" -name '*.kt' | LC_ALL=C sort)
    if [ "${#files[@]}" -eq 0 ]; then
        echo "（没有找到 .kt 文件，跳过）"
        return 0
    fi
    "$KTLINT_BIN" \
        --relative \
        --reporter=plain \
        --reporter="checkstyle,output=$REPORT_DIR/ktlint-checkstyle.xml" \
        "${files[@]}" 2>&1 | tee "$REPORT_DIR/ktlint.txt"
    local status="${PIPESTATUS[0]}"
    if [ "$status" -ge 2 ]; then
        echo "::error::ktlint 内部错误（exit=$status）—— 多半是源码解析失败或 .editorconfig 写错，见上面的输出"
    fi
    emit_annotations "ktlint" "$REPORT_DIR/ktlint.txt"
    return "$status"
}

# ---------------------------------------------------------------- detekt
run_detekt() {
    # 同上：detekt.yml 缺失时 detekt CLI 会直接抛 ExistingPathConverter 异常（栈里看不出原因）。
    if [ ! -f detekt.yml ]; then
        echo "::error::找不到 detekt.yml —— 门禁三件套必须同时在目标分支上（见 docs/WORKFLOW.md §3）。"
        return 90
    fi
    prepare_detekt
    log "detekt $DETEKT_VERSION（规则见 detekt.yml）"
    java -jar "$DETEKT_JAR" \
        --input "$SRC_DIR/main/java,$SRC_DIR/test/java" \
        --config detekt.yml \
        --parallel \
        --report "txt:$REPORT_DIR/detekt.txt" \
        --report "xml:$REPORT_DIR/detekt.xml" \
        --report "html:$REPORT_DIR/detekt.html" \
        2>&1 | tee "$REPORT_DIR/detekt-console.txt"
    local status="${PIPESTATUS[0]}"
    if [ -f "$REPORT_DIR/detekt.txt" ]; then
        echo
        echo "--- detekt 报告（txt）---"
        cat "$REPORT_DIR/detekt.txt"
    fi
    emit_annotations "detekt" "$REPORT_DIR/detekt.txt"
    return "$status"
}

# ---------------------------------------------------------------- 主流程
ktlint_status=0
detekt_status=0
run_ktlint  || ktlint_status=$?
run_detekt  || detekt_status=$?

log "汇总"
echo "ktlint: exit=$ktlint_status（mode=$KTLINT_MODE）"
echo "detekt: exit=$detekt_status（mode=$DETEKT_MODE）"
echo "报告目录: $REPORT_DIR（ktlint.txt / ktlint-checkstyle.xml / detekt.txt / detekt.xml / detekt.html）"

# exit=90 = 本脚本自己的前置检查失败（缺配置文件）；exit=3 = detekt 配置无效（键名写错等）。
# 这两种都不是「代码有问题」，日志里必须说清楚，否则会像 2026-09-15 那样误导排查方向。
# 注意：**detekt 的 exit=2 是「发现问题数超过 maxIssues」**（正常的判红），不要当成配置错误。
if [ "$ktlint_status" -eq 90 ] || [ "$detekt_status" -eq 90 ] || [ "$detekt_status" -eq 3 ]; then
    echo "::error::静态门禁自身的配置有问题（不是代码问题）：请检查 tools/ci-gates.sh / .editorconfig / detekt.yml 是否齐全且键名正确（见 docs/WORKFLOW.md §3）"
fi

blocked=0
if [ "$ktlint_status" -ne 0 ] && [ "$KTLINT_MODE" = "block" ]; then blocked=1; fi
if [ "$detekt_status" -ne 0 ] && [ "$DETEKT_MODE" = "block" ]; then blocked=1; fi

if [ "$ktlint_status" -ne 0 ] || [ "$detekt_status" -ne 0 ]; then
    [ "$blocked" -eq 0 ] && echo "::warning::静态门禁发现问题，但当前模式为 report，本次不拦截"
fi

if [ "$blocked" -ne 0 ]; then
    echo "::error::静态门禁未通过（ktlint=$ktlint_status/$KTLINT_MODE, detekt=$detekt_status/$DETEKT_MODE）"
    exit 1
fi

echo "静态门禁通过 ✓（ktlint=$KTLINT_MODE, detekt=$DETEKT_MODE）"
