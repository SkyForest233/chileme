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
# 当前默认：ktlint = block，detekt = block（2026-09-16 起，两个门禁都真拦）
#   —— detekt 此前一直是 report，且因为少了 --build-upon-default-config 而**空转**（报告恒为 0 条）。
#      修好之后第一次拿到真实清单：73 条发现 / 6 个规则，其中 71 条是体量指标（按 detekt.yml 里的
#      实测清单显式关闭并写明归口），2 条已改代码修掉 → 归零后才切 block。见 devlog/2026-09-16.md §14。
#
# ⚠️ 2026-09-16 实测发现（此前 detekt 一直是**空转**的）：`--config detekt.yml` 单独用时，
#   detekt **不会**把默认配置作为基线，未在本仓库配置里逐条列出的规则一律不激活 ——
#   于是 `style: active: true` / `complexity: active: true` 这些**规则集**开关看着开了，
#   底下的规则却一条都没跑（canary 文件里 4 处必然命中的违规报 0 条；去掉 --config 用默认
#   配置跑同一个文件则 4 条全中、exit=2）。修法是加 `--build-upon-default-config`：
#   默认配置作基线，本仓库的 detekt.yml 作为**覆盖层**。
#   为免再静默失效，下面加了 `detekt_selftest`：每次跑门禁都先拿一个含已知违规的临时文件
#   验一遍「规则确实在跑」，命中 0 条就 ::error:: 并判红（一个静默空转的门禁比没有门禁更糟）。
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
DETEKT_MODE="${DETEKT_MODE:-block}"
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
# 把工具**控制台输出的尾部**也做成一条 annotation。
# 2026-09-16 实测：detekt 开着 complexity 规则集却报 0 条发现（工程里明明有一个 863 行的函数），
# 而判断「是真没问题，还是根本没在分析」只能看它自己打印了什么 —— 日志下载不到，
# 于是把尾部 900 字节塞进 annotation。这条是**门禁自身健康度**的探针，比发现清单更要紧。
emit_console_tail() {
    local tool="$1" file="$2" exit_code="$3"
    if [ ! -s "$file" ]; then
        echo "::notice title=${tool} 控制台::（无输出；exit=${exit_code}）—— 工具可能没跑起来，检查上面的下载/前置步骤"
        return 0
    fi
    local lines bytes tail_text
    lines=$(wc -l < "$file" | tr -d ' ')
    bytes=$(wc -c < "$file" | tr -d ' ')
    tail_text=$(tail -c 900 "$file" | tr '\n\r' '  ' | sed 's/%/%25/g')
    echo "::notice title=${tool} 控制台尾部（exit=${exit_code}，共 ${lines} 行 / ${bytes} 字节）::${tail_text}"
}

emit_annotations() {
    local tool="$1" report="$2"
    [ -s "$report" ] || return 0
    # 一律解析**控制台输出**（格式有实测样本，比 txt 报告稳）：
    #   detekt : <绝对路径>.kt:<行>:<列>: <消息> [<RuleId>]        ← 规则名在行尾方括号里
    #   ktlint : <相对路径>.kt:<行>:<列>: (standard:<rule-id>) <消息>
    # 2026-09-16 踩过：先按 detekt 的 txt 报告（`path:RuleId - 实测/阈值 - [实体] at path:行:列 - Signature=…`）
    # 写解析，本地 gawk 跑得通、CI 的 mawk 却把 Signature 里的 `(String)`/`(Int)` 当成了规则名 ——
    # 换成「行尾 [RuleId]」这个唯一且稳定的锚点，两种 awk 都一致。
    awk -v tool="$tool" '
        {
            line = $0; sub(/[ \t\r]+$/, "", line)
            rule = ""; where = ""
            if (match(line, /\[[A-Za-z0-9]+\]$/)) {                       # detekt 控制台
                rule = substr(line, RSTART + 1, RLENGTH - 2)
            } else if (match(line, /^[^ ]*\.kt:[0-9]+:[0-9]+: \(([a-z-]+:)?[A-Za-z0-9_-]+\)/)) {   # ktlint plain
                seg = substr(line, RSTART, RLENGTH)
                if (match(seg, /\(([a-z-]+:)?[A-Za-z0-9_-]+\)$/)) {
                    rule = substr(seg, RSTART, RLENGTH); gsub(/[()]/, "", rule); sub(/^[a-z-]+:/, "", rule)
                }
            } else next
            if (match(line, /^[^ ]*\.kt:[0-9]+/)) {                       # 位点（去掉列号与绝对前缀）
                where = substr(line, RSTART, RLENGTH)
                k = index(where, "app/src"); if (k > 1) where = substr(where, k)
            }
            cnt[rule]++; if (cnt[rule] == 1) nrules++; total++
            if (length(loc[rule]) < 700) loc[rule] = loc[rule] (loc[rule] == "" ? "" : "; ") where
        }
        END {
            if (!total) exit
            summary = ""
            for (r in cnt) summary = summary (summary == "" ? "" : ", ") r "=" cnt[r]
            gsub(/%/, "%25", summary)
            printf "::notice title=%s 汇总::%d 条发现 / %d 个规则 —— %s\n", tool, total, nrules, summary
            shown = 0
            for (r in cnt) {          # GitHub 每级别最多留 10 条 annotation，故位点明细只列前 6 个规则
                if (shown >= 6) break
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
    emit_console_tail "ktlint" "$REPORT_DIR/ktlint.txt" "$status"
    return "$status"
}

# ---------------------------------------------------------------- detekt 自测
# 门禁自身的健康检查：造一个含**必然命中**违规的临时文件（放在报告目录下，不在 app/src 里，
# 因此不会被 ktlint 的主扫描收到），用与主运行完全相同的配置跑一遍。
# 命中 0 条 = 规则没在跑（配置/版本/参数问题）→ 判红，因为空转的门禁会给人虚假的安全感。
# 注意：自测文件里的 8 形参函数是给 LongParameterList 用的，那条规则若被关闭就只剩
# UnusedPrivateMember + EmptyCatchBlock 两类命中 —— 判据是「≥1 条」，不是固定条数。
# 2026-09-16 就是这么发现「--config 不带 --build-upon-default-config 时 detekt 一条规则都不激活」的。
detekt_selftest() {
    local dir="$REPORT_DIR/selftest"
    mkdir -p "$dir"
    cat > "$dir/DetektSelfTest.kt" <<'KT'
package gateselftest

private fun unusedPrivate(): Int = 42

fun eightParams(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int): Int =
    a + b + c + d + e + f + g + h

fun emptyCatch() {
    try {
        eightParams(1, 2, 3, 4, 5, 6, 7, 8)
    } catch (ignored: RuntimeException) {
    }
}
KT
    java -jar "$DETEKT_JAR" \
        --input "$dir" \
        --config detekt.yml \
        --build-upon-default-config \
        --report "txt:$dir/findings.txt" > "$dir/console.txt" 2>&1
    local st=$? hits=0
    [ -f "$dir/findings.txt" ] && hits=$(grep -c '\.kt:' "$dir/findings.txt")
    if [ "$hits" -eq 0 ]; then
        echo "::error title=detekt 自测失败（门禁空转）::自测文件含必然命中的违规（UnusedPrivateMember / EmptyCatchBlock；LongParameterList 已按 detekt.yml 关闭，不计入），detekt 却报 0 条（exit=$st）。多半是 --config 少了 --build-upon-default-config，或 detekt.yml 把对应规则集/规则关掉了。控制台尾部：$(tail -c 400 "$dir/console.txt" | tr '\n\r' '  ' | sed 's/%/%25/g')"
        return 91
    fi
    echo "::notice title=detekt 自测::命中 $hits 条已知违规（exit=$st）—— 规则确实在跑"
    return 0
}

# ---------------------------------------------------------------- detekt
run_detekt() {
    # 同上：detekt.yml 缺失时 detekt CLI 会直接抛 ExistingPathConverter 异常（栈里看不出原因）。
    if [ ! -f detekt.yml ]; then
        echo "::error::找不到 detekt.yml —— 门禁三件套必须同时在目标分支上（见 docs/WORKFLOW.md §3）。"
        return 90
    fi
    prepare_detekt
    detekt_selftest || return 91
    log "detekt $DETEKT_VERSION（detekt.yml 作为默认配置之上的覆盖层）"
    java -jar "$DETEKT_JAR" \
        --input "$SRC_DIR/main/java,$SRC_DIR/test/java" \
        --config detekt.yml \
        --build-upon-default-config \
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
    emit_annotations "detekt" "$REPORT_DIR/detekt-console.txt"


    emit_console_tail "detekt" "$REPORT_DIR/detekt-console.txt" "$status"
    if [ -f "$REPORT_DIR/detekt.txt" ]; then
        echo "::notice title=detekt 报告文件::$(wc -l < "$REPORT_DIR/detekt.txt" | tr -d ' ') 行 / $(wc -c < "$REPORT_DIR/detekt.txt" | tr -d ' ') 字节"
    else
        echo "::notice title=detekt 报告文件::（未生成 detekt.txt）"
    fi
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

# exit=91 = detekt 自测未命中已知违规（门禁空转，见 detekt_selftest）—— **无视 report/block 一律判红**。
# exit=90 = 本脚本自己的前置检查失败（缺配置文件）；exit=3 = detekt 配置无效（键名写错等）。
# 这两种都不是「代码有问题」，日志里必须说清楚，否则会像 2026-09-15 那样误导排查方向。
# 注意：**detekt 的 exit=2 是「发现问题数超过 maxIssues」**（正常的判红），不要当成配置错误。
if [ "$ktlint_status" -eq 90 ] || [ "$detekt_status" -eq 90 ] || [ "$detekt_status" -eq 3 ]; then
    echo "::error::静态门禁自身的配置有问题（不是代码问题）：请检查 tools/ci-gates.sh / .editorconfig / detekt.yml 是否齐全且键名正确（见 docs/WORKFLOW.md §3）"
fi
if [ "$detekt_status" -eq 91 ]; then
    echo "::error::detekt 自测未命中任何已知违规 —— 门禁在空转，本次判红（与 DETEKT_MODE 无关）"
fi

blocked=0
if [ "$ktlint_status" -ne 0 ] && [ "$KTLINT_MODE" = "block" ]; then blocked=1; fi
if [ "$detekt_status" -ne 0 ] && [ "$DETEKT_MODE" = "block" ]; then blocked=1; fi
[ "$detekt_status" -eq 91 ] && blocked=1        # 门禁空转：不看模式，一律红

if [ "$ktlint_status" -ne 0 ] || [ "$detekt_status" -ne 0 ]; then
    [ "$blocked" -eq 0 ] && echo "::warning::静态门禁发现问题，但当前模式为 report，本次不拦截"
fi

if [ "$blocked" -ne 0 ]; then
    echo "::error::静态门禁未通过（ktlint=$ktlint_status/$KTLINT_MODE, detekt=$detekt_status/$DETEKT_MODE）"
    exit 1
fi

echo "静态门禁通过 ✓（ktlint=$KTLINT_MODE, detekt=$DETEKT_MODE）"
