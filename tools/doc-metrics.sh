#!/usr/bin/env bash
# ============================================================================
# 文档口径脚本：把散落在 docs/ 与 devlog/ 里的那些数字，收敛成**一份可复跑的测量**
#
# 用法：
#   bash tools/doc-metrics.sh              # 打印全部指标
#   bash tools/doc-metrics.sh | grep 单测   # 只看某几项
#
# 为什么有这个脚本（2026-09-17 文档审计的结论）：
#   同一个数字曾在 README / DESIGN_SPEC / detekt.yml / INDEX / 审计报告里各存一份手抄值，
#   改一处忘三处，于是互相矛盾（单测数在 91→116→117→119→120→121 之间被反复加注修正；
#   `corruptedKeys` 一处写 32、实测 26；`LocalDate.now()` 一处写 34、实测 26）。
#   手抄值不可能不漂移 ⇒ 文档里只写「实测 N 处（口径见 tools/doc-metrics.sh）」，
#   要复核就跑本脚本，不要再手抄第二遍。
#
# 三条硬约定（改本脚本时不要破坏）：
#   1. **每个数字都带口径**：作用域（app/src/main 还是含测试）、计数单位（行数还是出现次数）、
#      行数一律按 `wc -l`（= 换行符个数；用 Python 的 len(read().split('\n')) 会每文件多 1）。
#   2. **计数用 `git grep -o | wc -l`（出现次数），不用 `-c`（命中行数）** —— 一行里出现两次时
#      两者不同，文档里写的「N 处」指的是出现次数。
#   3. **零结果必须做阳性对照**：末尾那两项（已知必然存在 / 已知必然不存在）用来证明
#      grep 真的在工作。曾因 `git grep -E` 是 POSIX ERE（不支持 `(?!...)` 与 `\s`）、
#      且正则报错被 `2>/dev/null` 吞掉，得到过一个**毫无意义的「零命中」**。
#
# 依赖：git + grep + awk（必选）；python3（仅「单测数」「中文字面量」两项需要，
#       因为它们必须剥离注释/字符串后才准 —— 原始 grep 会把注释里的 @Test 也算进去）。
# ============================================================================
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

MAIN='app/src/main'
SRC='app/src'
SCREENS='app/src/main/java/com/agon/app/ui/screens'
APPLAYER='app/src/main/java/com/agon/app/ui/components/app'

# occ <正则> <作用域...> —— 出现次数（不是命中行数）
occ() { local pat="$1"; shift; git grep -o -E "$pat" -- "$@" 2>/dev/null | wc -l | tr -d ' '; }
# lines <文件/目录> —— wc -l 口径
lines() { find "$@" -name '*.kt' 2>/dev/null | xargs wc -l 2>/dev/null | tail -1 | awk '{print $1}'; }
nfiles() { find "$@" -name '*.kt' 2>/dev/null | wc -l | tr -d ' '; }

row() { printf '  %-34s %10s   %s\n' "$1" "$2" "$3"; }

echo '════ 规模与结构（行数一律 wc -l）════'
row '屏幕本体（不含 *State.kt）' "$(nfiles "$SCREENS" -not -name '*State.kt') 文件 / $(find "$SCREENS" -name '*.kt' -not -name '*State.kt' | xargs wc -l | tail -1 | awk '{print $1}') 行" 'ui/screens/*.kt 排除 *State.kt'
row 'App 级组件层' "$(nfiles "$APPLAYER") 文件 / $(lines "$APPLAYER") 行" 'ui/components/app/*.kt'
row 'FoodRepository.kt' "$(wc -l < app/src/main/java/com/agon/app/data/FoodRepository.kt | tr -d ' ') 行" '路线图 #5 的拆分对象'
row '*State.kt 状态容器' "$(ls "$SCREENS"/*State.kt | wc -l | tr -d ' ') 个" '路线图 #6 的基础'
row 'Application 子类' "$(occ 'class[[:space:]]+[A-Za-z]*[[:space:]]*:[[:space:]]*Application\b' "$MAIN") 个" '0 = 无 DI 容器（路线图 #5）'
row 'UI 测试' "$(find app/src/androidTest -name '*.kt' 2>/dev/null | wc -l | tr -d ' ') 个" 'androidTest 目录存在与否'

echo
echo '════ 测试（需 python3：必须剥离注释，否则会把注释里的 @Test 算进去）════'
if command -v python3 >/dev/null 2>&1; then
  python3 - <<'PY'
import glob, io, re

def code_only(src):
    """剥掉注释与字符串内容（Kotlin 块注释可嵌套，故用深度计数而非正则）。"""
    out = []; i = 0; n = len(src)
    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            j = src.find('\n', i); i = n if j < 0 else j
        elif c == '/' and i + 1 < n and src[i + 1] == '*':
            d = 1; i += 2
            while i < n and d:
                if src.startswith('/*', i): d += 1; i += 2
                elif src.startswith('*/', i): d -= 1; i += 2
                else: i += 1
        elif c == '"':
            if src.startswith('"""', i):
                j = src.find('"""', i + 3); i = n if j < 0 else j + 3
            else:
                i += 1
                while i < n and src[i] != '"':
                    i += 2 if src[i] == '\\' else 1
                i += 1
            out.append('""')
        else:
            out.append(c); i += 1
    return ''.join(out)

def strip_comments(src):
    """只剥注释、保留字符串内容（用于数字面量）。"""
    out = []; i = 0; n = len(src)
    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            j = src.find('\n', i); i = n if j < 0 else j
        elif c == '/' and i + 1 < n and src[i + 1] == '*':
            d = 1; i += 2
            while i < n and d:
                if src.startswith('/*', i): d += 1; i += 2
                elif src.startswith('*/', i): d -= 1; i += 2
                else: i += 1
        elif c == '"':
            if src.startswith('"""', i):
                j = src.find('"""', i + 3); e = n if j < 0 else j + 3
            else:
                k = i + 1
                while k < n and src[k] != '"':
                    k += 2 if src[k] == '\\' else 1
                e = k + 1
            out.append(src[i:e]); i = e
        else:
            out.append(c); i += 1
    return ''.join(out)

files = glob.glob('app/src/test/**/*.kt', recursive=True)
real = sum(len(re.findall(r'@Test\b', code_only(io.open(f, encoding='utf-8').read()))) for f in files)
raw = sum(len(re.findall(r'@Test\b', io.open(f, encoding='utf-8').read())) for f in files)
print('  %-34s %10s   %s' % ('单测数（@Test，词法计数）', real, '剥注释后；原始 grep 会数出 %d（多的在注释里）' % raw))

cn = 0
for f in glob.glob('app/src/main/**/*.kt', recursive=True):
    s = strip_comments(io.open(f, encoding='utf-8').read())
    for m in re.finditer(r'"""(.*?)"""|"((?:[^"\\]|\\.)*)"', s, re.S):
        c = m.group(1) if m.group(1) is not None else m.group(2)
        if re.search(r'[\u4e00-\u9fff]', c or ''):
            cn += 1
print('  %-34s %10s   %s' % ('含中文的字符串字面量', '%d 处' % cn, '剥注释后提取字面量内容（不剥会因注释里的引号虚高）'))
PY
else
  echo '  （未安装 python3，跳过这两项 —— 用原始 grep 数会不准，故宁可不给）'
fi

echo
echo '════ 无障碍与资源化（作用域 app/src/main）════'
row 'contentDescription = null' "$(occ 'contentDescription[[:space:]]*=[[:space:]]*null' "$MAIN") 处" '待办 #7'
row 'Modifier.semantics 真调用' "$(occ '\.semantics[[:space:]]*[({]' "$MAIN") 处" '⚠️ 别用 \bsemantics\b 数：那会把 6 行 import 也算进来（曾因此得出 7 处的错值）'
row 'strings.xml 条目' "$(grep -c '<string' app/src/main/res/values/strings.xml | tr -d ' ') 条" '与上面 583 处中文字面量对比即待办 #7 的规模'

echo
echo '════ 路线图 #4/#5/#6/#8 的证据（作用域 app/src/main，括号内为含测试）════'
row 'Channel<' "$(occ 'Channel<' "$MAIN") 处（$(occ 'Channel<' "$SRC")）" '#4：0 = 无统一错误模型'
row 'UiEvent' "$(occ '\bUiEvent\b' "$MAIN") 处（$(occ '\bUiEvent\b' "$SRC")）" '#4'
row 'java.time now() 直接调用' "$(occ 'LocalDate\.now|LocalDateTime\.now|LocalTime\.now' "$MAIN") 处（$(occ 'LocalDate\.now|LocalDateTime\.now|LocalTime\.now' "$SRC")）" '#5：时间不可注入 ⇒ 跨零点逻辑无法单测'
row '  其中 LocalDate.now()' "$(occ 'LocalDate\.now\(\)' "$MAIN") 处（$(occ 'LocalDate\.now\(\)' "$SRC")）" '⚠️ 曾把 34（三个 now 工厂合计）当成 LocalDate.now() 的数写进文档，故单列一行'
row 'stateIn(' "$(occ 'stateIn\(' "$MAIN") 处" '#6'
row 'WhileSubscribed' "$(occ 'WhileSubscribed' "$MAIN") 处" '#6：0 = 后台仍在算'
row 'corruptedKeys' "$(occ 'corruptedKeys' "$MAIN") 处（$(occ 'corruptedKeys' "$SRC")）" '#8：被引用很多但没有任何页面消费它'

echo
echo '════ Miuix 合规（2026-08-20 审查报告的追踪项，作用域 app/src/main）════'
row 'MaterialTheme.colorScheme' "$(occ 'MaterialTheme\.colorScheme' "$MAIN") 处" '桥接取色，仍是主要方式'
row 'MiuixTheme.colorScheme' "$(occ 'MiuixTheme\.colorScheme' "$MAIN") 处" '语义 token 取色'
row '显式 fontSize = N.sp' "$(occ 'fontSize[[:space:]]*=[[:space:]]*[0-9]+\.sp' "$MAIN") 处" '字体层级未走 token 的残留'
row 'MiuixTheme.textStyles' "$(occ 'MiuixTheme\.textStyles' "$MAIN") 处" '审查报告当时为 0'
row 'AppTextScale（自建档位表）' "$(occ 'AppTextScale' "$MAIN") 处" '09-16 起的跨主题语义字号档位'
row 'MiuixIcons' "$(occ 'MiuixIcons' "$MAIN") 处" '审查报告当时为 0'
row 'material icons' "$(occ 'Icons\.(Rounded|Filled|Outlined|Default)' "$MAIN") 处" 'MD3 分支 + 两套图标库无对应关系的字形'
row 'buttonColorsPrimary' "$(occ 'buttonColorsPrimary' "$MAIN") 处" '主操作按钮（审查报告当时为 0）'
row 'textButtonColorsPrimary' "$(occ 'textButtonColorsPrimary' "$MAIN") 处" '弹窗主要动作蓝底白字，MiuixDialogContentTest 守卫'
row 'Modifier.border' "$(occ 'Modifier\.border' "$MAIN") 处" 'squircle 能力未用上的地方'

echo
echo '════ 技术栈（单一位点：gradle/libs.versions.toml 与 app/build.gradle.kts）════'
row 'Kotlin' "$(grep -oE 'kotlin = "[^"]+"' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)" 'libs.versions.toml'
row 'AGP' "$(grep -oE 'agp = "[^"]+"' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)" 'libs.versions.toml'
row 'composeBom' "$(grep -oE 'composeBom = "[^"]+"' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)" 'libs.versions.toml'
row 'miuix' "$(grep -oE 'miuix = "[^"]+"' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)" '上游 pinned tag，技能库证据基线'
row 'Gradle wrapper' "$(grep -oE 'gradle-[0-9.]+' gradle/wrapper/gradle-wrapper.properties | head -1 | sed 's/gradle-//')" 'gradle-wrapper.properties'
row 'compileSdk / minSdk / targetSdk' "$(grep -oE '(compileSdk|minSdk|targetSdk) = [0-9]+' app/build.gradle.kts | awk '{printf "%s ", $3}')" 'app/build.gradle.kts'
row 'detekt 模式' "$(grep -oE 'DETEKT_MODE="\$\{DETEKT_MODE:-[a-z]+\}"' tools/ci-gates.sh | grep -oE ':-[a-z]+' | tr -d ':-')" 'tools/ci-gates.sh（block = 真拦）'
row 'ktlint 模式' "$(grep -oE 'KTLINT_MODE="\$\{KTLINT_MODE:-[a-z]+\}"' tools/ci-gates.sh | grep -oE ':-[a-z]+' | tr -d ':-')" 'tools/ci-gates.sh'

echo
echo '════ 文档自身健康度 ════'
row 'markdown 表格断裂行' "$(python3 - <<'PY' 2>/dev/null || echo '需 python3'
import glob, io
bad = 0
for f in glob.glob('**/*.md', recursive=True):
    if f.startswith('.git/') or f.startswith('.claude/'):
        continue
    fence = False
    for line in io.open(f, encoding='utf-8').read().split('\n'):
        if line.strip().startswith('```'):
            fence = not fence
        elif not fence and line.strip().startswith('|') and not line.rstrip().endswith('|'):
            bad += 1
print('%d 处' % bad)
PY
)" '表格行不能跨物理行；跳过代码围栏内的 shell 管道'
row '同文件内重复的长句（>=40 字符）' "$(python3 - <<'PY' 2>/dev/null || echo '需 python3'
import glob, io, re
from collections import Counter
groups = 0
for f in glob.glob('**/*.md', recursive=True):
    if f.startswith('.git/') or f.startswith('.claude/'):
        continue
    c = Counter()
    for s in re.split(r'[。；\n|]', io.open(f, encoding='utf-8').read()):
        s = re.sub(r'\s+', '', s).lstrip('->*#0123456789. ')
        if len(s) >= 40:
            c[s] += 1
    dup = [(s, n) for s, n in c.items() if n > 1]
    if dup:
        groups += len(dup)
        for s, n in dup[:3]:
            print('     ✗ %s ×%d「%s…」' % (f, n, s[:60]))
print('%d 组' % groups)
PY
)" '替换/追加段落时最容易留下的残留（本日「单测数变化」就曾因此出现 2 份）。**现存的 1 组是刻意的、不要清**：`devlog/2026-09-16.md` §13 的「实际结果表」（`MainActivity.kt` 118 行）与路线图的「计划表」（同文件 ~120 行）用了同一句内容描述，是计划 vs 实际的对照 ⇒ 目标值是 1 组，多于 1 组才需要查'
row '计数句自洽性（N run = X 绿 + Y 红）' "$(python3 - <<'PY' 2>/dev/null || echo '需 python3'
import glob, io, re
bad = tot = 0
for f in glob.glob('**/*.md', recursive=True):
    if f.startswith('.git/') or f.startswith('.claude/'):
        continue
    for m in re.finditer(r'(\d+)\s*run[：:]\s*(\d+)\s*绿\s*(\d+)\s*红', io.open(f, encoding='utf-8').read()):
        r, g, b = map(int, m.groups()); tot += 1
        if g + b != r:
            bad += 1; print('     ✗ %s: %d run ≠ %d 绿 + %d 红' % (f, r, g, b))
print('%d 处，不自洽 %d 处' % (tot, bad))
PY
)" '本日曾两次写出「14 绿 3 红 / 14 run」这类算不平的计数，故固化成检查'
row 'devlog 文件数 / 总行数' "$(ls devlog/*.md | wc -l | tr -d ' ') 个 / $(wc -l devlog/*.md | tail -1 | awk '{print $1}') 行" '含 INDEX.md'
row 'docs/audits 报告数' "$(ls docs/audits/*.md | wc -l | tr -d ' ') 份" '历史审计报告，只加批注不改写'
row '含 2026-09-17 批注的报告' "$(grep -l '2026-09-17 状态批注\|2026-09-17 追加' docs/audits/*.md | wc -l | tr -d ' ') 份" '7 份新增顶部批注 + 3 份在既有批注上追加（md3-audit / chileme-review / fix-plan；fix-plan 两者都有 ⇒ 去重 9 份）'
row '顶部无任何状态批注的报告' "$(for f in docs/audits/*.md; do sed -n '2,12p' "$f" | grep -q '状态批注\|修复状态\|校订\|复核批注\|2026-09-17 追加' || echo "$f"; done | wc -l | tr -d ' ') 份" '目标是 0：读者应能一眼判断「这条还成立吗」'

echo
echo '════ 阳性对照（这两项证明上面的 grep 真在工作，零结果不是假的）════'
row '已知必然存在：@Composable' "$(occ '@Composable' "$MAIN") 处" '若这里也是 0，说明上面的零结果全部不可信'
row '已知必然不存在：ZZZ_NOT_A_SYMBOL' "$(occ 'ZZZ_NOT_A_SYMBOL' "$MAIN") 处" '必须是 0'
