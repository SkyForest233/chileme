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
# occ <ERE 正则> <路径…> —— 出现次数口径。
# ⚠️ 必须带 --untracked：git grep **默认只搜已跟踪文件**，新建但还没 git add 的文件对它是隐形的。
#   2026-09-18 做 #5a 时当场撞上：新建 ChiliMeApp.kt 后本行报「Application 子类 0 个」，
#   而下面那条用 glob 走文件系统的守卫报 1 ⇒ 同一件事两个答案。开发中（add 之前）量到的数会偏低，
#   而偏低正好等于「新写的东西没被算进去」，最难察觉。--untracked 仍然尊重 .gitignore，不会把 build/ 算进来。
occ() { local pat="$1"; shift; git grep --untracked -o -E "$pat" -- "$@" 2>/dev/null | wc -l | tr -d ' '; }
# lines <文件/目录> —— wc -l 口径
lines() { find "$@" -name '*.kt' 2>/dev/null | xargs wc -l 2>/dev/null | tail -1 | awk '{print $1}'; }
nfiles() { find "$@" -name '*.kt' 2>/dev/null | wc -l | tr -d ' '; }

row() { printf '  %-34s %10s   %s\n' "$1" "$2" "$3"; }

echo '════ 规模与结构（行数一律 wc -l）════'
row '屏幕目录 ui/screens（不含 *State.kt）' "$(nfiles "$SCREENS" -not -name '*State.kt') 文件 / $(find "$SCREENS" -name '*.kt' -not -name '*State.kt' | xargs wc -l | tail -1 | awk '{print $1}') 行" 'ui/screens/*.kt 排除 *State.kt；⚠️ #10a 起这里还含弹窗/body 文件，文件数增加不等于新增屏幕'
row 'App 级组件层' "$(nfiles "$APPLAYER") 文件 / $(lines "$APPLAYER") 行" 'ui/components/app/*.kt'
DATADIR=app/src/main/java/com/agon/app/data
row 'FoodRepository.kt（#5c 后的核心）' "$(wc -l < $DATADIR/FoodRepository.kt | tr -d ' ') 行" '只剩 19 个 key + 19 条对外读取流 + 7 个解码包装 + 放弃损坏数据入口 = 9 个类级函数'
row '#5c 拆出的领域文件' "$(python3 - <<'PY'
import io, os
D = 'app/src/main/java/com/agon/app/data'
NEW = ['RepositoryCore.kt', 'FoodItems.kt', 'FoodArchive.kt', 'FoodConsumption.kt',
       'FoodBackup.kt', 'FoodSettings.kt', 'FoodCredentials.kt']
have = [f for f in NEW if os.path.exists(os.path.join(D, f))]
print('%d 个 / %d 行' % (len(have), sum(sum(1 for _ in io.open(os.path.join(D, f), encoding='utf-8')) for f in have)))
PY
)" '底座 + 库存 + 归档 + 消耗 + 备份 + 设置 + 凭据（少一个就是被误删或误改名）'
row 'data/ 最大文件（判据 < 400 行）' "$(python3 - <<'PY'
import glob, io, os
sizes = sorted(((sum(1 for _ in io.open(f, encoding='utf-8')), os.path.basename(f))
                for f in glob.glob('app/src/main/java/com/agon/app/data/*.kt')), reverse=True)
over = ['%s %d' % (n, c) for c, n in sizes if c > 400]
print((('%s %d 行%s' % (sizes[0][1], sizes[0][0],
                       (' => X 超 400 的有 ' + ', '.join(over)) if over else ' OK 全部 < 400')) if sizes else 'X 找不到源码（glob 空集）—— 本脚本会 cd 到自己所在目录的上一级，请在仓库根目录跑'))
PY
)" '#5 验收③在 data 层的落点；⚠️ UI/VM 层仍有文件超 400 行（个数与清单刻意不写死在这里，见下方「全清单」那行），不属 #5 范围（见 ROADMAP 全项验收③ 与 #10）'
row '屏幕目录最大文件（#10 判据 < 400 行）' "$(python3 - <<'PY'
import glob, io, os
sizes = sorted(((sum(1 for _ in io.open(f, encoding='utf-8')), os.path.basename(f))
                for f in glob.glob('app/src/main/java/com/agon/app/ui/screens/*.kt')
                if not f.endswith('State.kt')), reverse=True)
over = ['%s %d' % (n, c) for c, n in sizes if c > 400]
print((('%s %d 行%s' % (sizes[0][1], sizes[0][0],
                       (' => X 超 400 的有 ' + ', '.join(over)) if over else ' OK 全部 < 400')) if sizes else 'X 找不到源码（glob 空集）—— 本脚本会 cd 到自己所在目录的上一级，请在仓库根目录跑'))
PY
)" '#10 验收①的一半；口径同上面「屏幕目录」那行（排除 *State.kt，含弹窗/body 文件）'
row 'viewmodel/ 最大文件（#10 判据 < 400 行）' "$(python3 - <<'PY'
import glob, io, os
sizes = sorted(((sum(1 for _ in io.open(f, encoding='utf-8')), os.path.basename(f))
                for f in glob.glob('app/src/main/java/com/agon/app/viewmodel/*.kt')), reverse=True)
over = ['%s %d' % (n, c) for c, n in sizes if c > 400]
print((('%s %d 行%s' % (sizes[0][1], sizes[0][0],
                       (' => X 超 400 的有 ' + ', '.join(over)) if over else ' OK 全部 < 400')) if sizes else 'X 找不到源码（glob 空集）—— 本脚本会 cd 到自己所在目录的上一级，请在仓库根目录跑'))
PY
)" '#10 验收①的另一半；#6 的 20 处 stateIn( 也在这个目录里'
row '主代码超 400 行的文件（全清单）' "$(python3 - <<'PY'
import glob, io
sizes = sorted(((sum(1 for _ in io.open(f, encoding='utf-8')), f)
                for f in glob.glob('app/src/main/**/*.kt', recursive=True)), reverse=True)
over = ['%s %d' % (f.replace('app/src/main/java/com/agon/app/', ''), c) for c, f in sizes if c > 400]
print((('%d 个%s' % (len(over), ('：' + ' · '.join(over)) if over else ' OK 全仓无一超 400')) if sizes else 'X 找不到源码（glob 空集）—— 本脚本会 cd 到自己所在目录的上一级，请在仓库根目录跑'))
PY
)" 'ROADMAP #5 验收③ 与 #10 的证据都指这行 —— 别再往文档里抄行数（抄过的 668 已腐烂成核查第 18 处）'
row '*State.kt 状态容器' "$(ls "$SCREENS"/*State.kt | wc -l | tr -d ' ') 个" '路线图 #6 的基础'
row 'Application 子类' "$(occ 'class[[:space:]]+[A-Za-z]*[[:space:]]*:[[:space:]]*Application\b' "$MAIN") 个" '#5a 起 = 1（`ChiliMeApp` 持有 `AppContainer`）；0 = 无 DI 容器'
row '依赖构造点（目标：只在容器里 1 处）' "$(python3 - <<'PY' 2>/dev/null || echo '需 python3'
import glob, io, re
MAN = 'app/src/main/AndroidManifest.xml'
CONTAINER = 'ChiliMeApp.kt'
PAT = r'FoodRepository\('
bad = []
# --- 阳性对照：先证明这条正则抓得到「现场构造」那个形状，否则下面的计数毫无意义 ---
probe = 'private val repo = FoodRepository(application)'
if len(re.findall(PAT, probe)) != 1:
    bad.append('probe')
    print('     ✗ 阳性对照失败：正则没抓到已知样本 ⇒ 本项计数不可信')

def code_only(path):
    # 只按行滤掉注释/KDoc：本仓这些文件里没有会被朴素剥离误伤的 "*/*" 那种字面量
    return '\n'.join(l for l in io.open(path, encoding='utf-8').read().split('\n')
                     if not (l.strip().startswith('*') or l.strip().startswith('//')
                             or l.strip().startswith('/*')))

sites = []
for f in sorted(glob.glob('app/src/main/**/*.kt', recursive=True)):
    n = len(re.findall(PAT, code_only(f)))
    if n:
        sites.append((f.split('com/agon/app/')[-1], n))
off = ['%s ×%d' % (f, n) for f, n in sites if f != CONTAINER]
if off:
    bad.append('sites')
    print('     ✗ 容器之外还在现场构造仓库：%s' % '、'.join(off))
    print('       （验收②「依赖只有一个构造点」：仓库应只由 %s 里的 AppContainer 建，VM 从容器取。'
          '多一个构造点就多一份独立的损坏状态与解码缓存）' % CONTAINER)

# --- 容器必须真的挂上 Manifest：少了 android:name，(application as ChiliMeApp) 会在运行时
#     ClassCastException，而**编译器与单测都发现不了**（单测里没人构造 VM）⇒ 只能靠这条守 ---
apps = []
for f in glob.glob('app/src/main/**/*.kt', recursive=True):
    apps += re.findall(r'class\s+(\w+)\s*:\s*Application\b', io.open(f, encoding='utf-8').read())
man = io.open(MAN, encoding='utf-8').read()
if len(apps) != 1:
    bad.append('appclass')
    print('     ✗ Application 子类应恰好 1 个，实测 %d 个：%s' % (len(apps), apps))
elif 'android:name=".%s"' % apps[0] not in man:
    bad.append('manifest')
    print('     ✗ AndroidManifest.xml 里没有 android:name=".%s" ⇒ 容器根本不会被创建，'
          'VM 取仓库时 ClassCastException（编译器与单测都发现不了）' % apps[0])
if 'class AppContainer' not in code_only('app/src/main/java/com/agon/app/' + CONTAINER):
    bad.append('container')
    print('     ✗ %s 里没有 class AppContainer ⇒ 容器不见了' % CONTAINER)

tot = sum(n for _, n in sites)
if bad:
    print('%d 处；%d 项 ✗ ⇒ 本项不通过' % (tot, len(bad)))
else:
    print('%d 处 ✓ 全在 %s；对照通过（正则抓得到样本）；Manifest 已挂 android:name=".%s"'
          % (tot, CONTAINER, apps[0]))
PY
)" '#5a 的验收②。两个方向都反证过：把构造搬回 VM ⇒ 报「容器之外还在现场构造」；删掉 Manifest 的 android:name ⇒ 报运行时 ClassCastException 风险'
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
row 'strings.xml 条目' "$(grep -c '<string' app/src/main/res/values/strings.xml | tr -d ' ') 条" '与上面「含中文的字符串字面量」那行对比即待办 #7 的规模（别在这里写死数字，它会腐烂）'

echo
echo '════ 路线图 #4/#5/#6/#8 的证据（作用域 app/src/main，括号内为含测试）════'
row 'Channel<' "$(occ 'Channel<' "$MAIN") 处（$(occ 'Channel<' "$SRC")）" '#4：0 = 无统一错误模型'
row 'UiEvent' "$(occ '\bUiEvent\b' "$MAIN") 处（$(occ '\bUiEvent\b' "$SRC")）" '#4'
row 'java.time now() 直接调用' "$(occ 'LocalDate\.now|LocalDateTime\.now|LocalTime\.now' "$MAIN") 处（$(occ 'LocalDate\.now|LocalDateTime\.now|LocalTime\.now' "$SRC")）" '#5：时间不可注入 ⇒ 跨零点逻辑无法单测'
row '  其中 LocalDate.now()' "$(occ 'LocalDate\.now\(\)' "$MAIN") 处（$(occ 'LocalDate\.now\(\)' "$SRC")）" '⚠️ 曾把 34（三个 now 工厂合计）当成 LocalDate.now() 的数写进文档，故单列一行'
row '  数据层+VM 函数体硬调 now()（#5b：0）' "$(python3 - <<'PY' 2>/dev/null || echo '需 python3'
import glob, io, re

# 两条正则：空括号 = 没把时钟传进去（硬调）；(clock) = 已注入。
# 只扫「代码行」（按行滤掉注释/KDoc）：本仓 KDoc 里刻意写着 `LocalDate.now()` 讲解设计，
# 那些是文档不是调用点 —— 但要注意 doc-metrics 顶部那条 now() 计数**含注释**，两条口径不同。
HARD = r'Local(?:Date|Time|DateTime)\.now\(\)'
INJ = r'Local(?:Date|Time|DateTime)\.now\(clock\)'
DIRS = ['app/src/main/java/com/agon/app/data', 'app/src/main/java/com/agon/app/viewmodel']
bad = []

# --- 阳性对照：先证明两条正则各自抓得到已知形状，否则下面的「0 处」毫无意义 ---
probe_hard = 'val today = LocalDate.now().toEpochDay()'
probe_inj = 'val today = LocalDate.now(clock).toEpochDay()'
if len(re.findall(HARD, probe_hard)) != 1 or re.findall(INJ, probe_hard):
    bad.append('probe-hard')
    print('     ✗ 阳性对照失败：硬调正则没抓到已知样本（或误抓注入样本）⇒ 本项计数不可信')
if len(re.findall(INJ, probe_inj)) != 1 or re.findall(HARD, probe_inj):
    bad.append('probe-inj')
    print('     ✗ 阳性对照失败：注入正则没抓到已知样本（或误抓硬调样本）⇒ 本项计数不可信')

# --- 豁免清单：'<该行的可识别片段>': '<它为什么不算「函数体硬调」>' ---
# 登记在此的两类都是**刻意保留**的：默认参数（生产调用点一律显式传注入的今天）与
# 便捷属性委托（可测路径是同名的 `*At(today)` 纯函数，UI 走 LocalToday）。
ALLOW = {
    'today: LocalDate = LocalDate.now()':
        'CsvExport 的便捷默认参数；仓库的导出入口一律显式传注入的今天',
    'exportedEpochDay: Long = LocalDate.now()':
        'BackupData 的默认参数（供缺字段的老备份反序列化），生产由调用方给值',
    'get() = daysLeftAt(LocalDate.now())':
        '便捷属性委托；可测路径是 daysLeftAt(today) 纯函数（见 FoodModels 的 KDoc）',
    'get() = freshnessAt(LocalDate.now())':
        '便捷属性委托；可测路径是 freshnessAt(today)',
    'get() = elapsedRatioAt(LocalDate.now())':
        '便捷属性委托；可测路径是 elapsedRatioAt(today)',
    'get() = remainingTextAt(LocalDate.now())':
        '便捷属性委托；可测路径是 remainingTextAt(today)',
    'statusForAt(LocalDate.now(), categoryThresholds)':
        '便捷包装 statusFor 的委托；可测路径是 statusForAt(today, …)',
}

def is_comment(line):
    t = line.strip()
    return t.startswith('*') or t.startswith('//') or t.startswith('/*')


def code_only(path):
    # 只按行滤掉注释/KDoc：本仓这些文件里没有会被朴素剥离误伤的 "*/\*" 那种字面量
    return '\n'.join(l for l in io.open(path, encoding='utf-8').read().split('\n')
                     if not is_comment(l))


hard, inj, used = [], 0, set()
for d in DIRS:
    for f in sorted(glob.glob(d + '/**/*.kt', recursive=True)):
        # ⚠️ 行号一律用**原文件**的行号（跳过注释行但不重排）：早先这里 enumerate 的是
        # 剥完注释的文本，报出来的行号比真实行号小几行（CsvExport 报 9、实际 13），
        # 拿它去翻源码会翻到无关的行 —— 守卫指错地方比不指更坏。
        for ln, line in enumerate(io.open(f, encoding='utf-8').read().split('\n'), 1):
            if is_comment(line):
                continue
            n = len(re.findall(HARD, line))
            inj += len(re.findall(INJ, line))
            if not n:
                continue
            where = '%s:%d' % (f.split('com/agon/app/')[-1], ln)
            key = next((k for k in ALLOW if k in line), None)
            if key:
                used.add(key)
                print('     （已豁免 %s —— %s）' % (where, ALLOW[key]))
            else:
                hard.append(where)
                print('     ✗ %s 仍在直接向系统要时间（没把时钟传进去）' % where)

for k in ALLOW:
    if k not in used:
        print('     ⚠ 豁免清单里这条已不在源码中，请复核后删除：%s' % k)

# --- 机制是否还在：防止「0 处硬调」是靠把取时间的代码整个删掉换来的 ---
app = 'app/src/main/java/com/agon/app/ChiliMeApp.kt'
vm = 'app/src/main/java/com/agon/app/viewmodel/AppViewModel.kt'
if 'FoodRepository(context, clock)' not in code_only(app):
    bad.append('wiring-repo')
    print('     ✗ 容器没把时钟交给仓库（%s 里找不到 FoodRepository(context, clock)）' % app)
if 'container.clock' not in code_only(vm):
    bad.append('wiring-vm')
    print('     ✗ VM 没从容器取时钟（%s 里找不到 container.clock）' % vm)
if inj == 0:
    bad.append('no-injection')
    print('     ✗ 数据层与 VM 里一处 now(clock) 都没有 ⇒ 时钟注入整套不见了')

if hard:
    bad.append('hard')
if bad:
    print('%d 处硬调、%d 处已注入；%d 项 ✗ ⇒ 本项不通过' % (len(hard), inj, len(bad)))
else:
    print('%d 处 ✓（%d 处已注入时钟；豁免 %d 处，对照通过）' % (len(hard), inj, len(used)))
PY
)" '#5b 验收：数据层与 VM 的取时间必须走注入的时钟；UI 侧刻意保留系统时钟（界面「现在几点」没有值得测的跨零点逻辑，而把时钟穿进 Compose 要多传好几层）'
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
row '文档点名的文件通配符指向空集' "$(python3 - <<'PY' 2>/dev/null || echo '需 python3'
import glob, io, re
DOCS = ['CLAUDE.md', 'README.md', 'docs/ARCHITECTURE.md', 'docs/DESIGN_SPEC.md',
        'docs/REQUIREMENTS.md', 'docs/WORKFLOW.md', 'docs/MIUIX_UPGRADE.md',
        'docs/ROADMAP.md', 'devlog/INDEX.md']
EXT = r'(?:kt|kts|xml|yml|yaml|toml|sh|md|pro|properties|json)'
HIST = re.compile(r'已删除|已全部删除|此前|原名|不要再|别照|取代|已于|作废|历史|快照|拆成|删除|双胞胎|已合并|→ 0|8 对')
tot = dead = exempt = 0
for d in DOCS:
    try:
        lines = io.open(d, encoding='utf-8').read().split('\n')
    except OSError:
        continue
    for i, line in enumerate(lines, 1):
        for pat in re.findall(r'`([A-Za-z0-9_./\-*]*\*[A-Za-z0-9_./\-*]*\.%s)`' % EXT, line):
            hits = glob.glob(pat, recursive=True) or glob.glob('**/' + pat, recursive=True)
            tot += 1
            if hits:
                continue
            if HIST.search(line):      # 明说是历史/已删除 ⇒ 豁免，但计数
                exempt += 1
                continue
            dead += 1
            print('     ✗ %s:%d  `%s` 匹配 0 个文件' % (d, i, pat))
print('%d 个通配符：指向空集 %d 处，历史提及豁免 %d 处' % (tot, dead, exempt))
PY
)" '目标「指向空集 0 处」。2026-09-17 核查发现 `MIUIX_UPGRADE.md` 的文件索引表指着 `ui/screens/Miuix*.kt`「各页 Miuix 实现」，而那批文件 09-16 已全删 ⇒ 升级手册会让人系统性漏改 77% 的调用点。行内出现「已删除/此前/取代/原名」等字样视为**历史提及**并豁免（只计数不报警），否则报 ✗'
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
# 认两种写法：「N run：X 绿 Y 红」与「N run —— X 绿 · Y 红 · Z 被( concurrency )取消」
PAT = re.compile(r'(\d+)\s*run\s*[：:—-]+\s*(\d+)\s*绿\s*[·、,，]?\s*(\d+)\s*红'
                 r'(?:\s*[·、,，]?\s*(\d+)\s*被[^。\n]{0,24}取消)?')
bad = tot = 0
for f in sorted(glob.glob('**/*.md', recursive=True)):
    if f.startswith('.git/') or f.startswith('.claude/'):
        continue
    for m in PAT.finditer(io.open(f, encoding='utf-8').read()):
        r, g, b, cx = int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4) or 0)
        tot += 1
        if g + b + cx != r:
            bad += 1
            print('     ✗ %s: %d run ≠ %d 绿 + %d 红 + %d 取消' % (f, r, g, b, cx))
print('%d 处，不自洽 %d 处' % (tot, bad))
PY
)" '本日曾两次写出「14 绿 3 红 / 14 run」这类算不平的计数，故固化成检查。也认「N run —— X 绿 · Y 红 · Z 被取消」这种三段写法（concurrency 取消是第三类结局，漏了它会误报）'
row '可空 StateFlow 型一次性事件（目标 0）' "$(python3 - <<'PY' 2>/dev/null || echo '需 python3'
import io, re
VM = 'app/src/main/java/com/agon/app/viewmodel/AppViewModel.kt'
UIE = 'app/src/main/java/com/agon/app/viewmodel/UiEvent.kt'
src = io.open(VM, encoding='utf-8').read()
PAT = r'private val (_\w+) = MutableStateFlow<[^>]*\?>\(null\)'
# --- 阳性对照：先证明这条正则抓得到已知样本，否则下面的「0」毫无意义 ---
probe = 'private val _probe = MutableStateFlow<String?>(null)'
if len(re.findall(PAT, probe)) != 1:
    print('     ✗ 阳性对照失败：正则没抓到已知样本 ⇒ 本项的「0 个」不可信')
events = re.findall(PAT, src)
bad = []  # 汇总行必须看这个列表，不能自己重新算 —— 否则会出现「上面一堆 ✗、下面还写 ✓」
# --- 豁免清单：'<字段名>': '<它为什么不是一次性事件>'。当前为空（#4a 之后一个都不剩）---
ALLOW = {}
for e in events:
    why = ALLOW.get(e)
    if why:
        print('     （已豁免 %s —— %s）' % (e, why))
    else:
        bad.append(e)
        print('     ✗ %s 用「可空 StateFlow」建模：若它是一次性 UI 事件，请改用 UiEvent + Channel' % e)
        print('       （见 viewmodel/UiEvent.kt 与 #4a；确实是非事件型状态就往本守卫的 ALLOW 里登记理由）')
# --- 替代机制是否还在（防止「0 个」是因为整套机制被删了）---
if 'sealed interface UiEvent' not in io.open(UIE, encoding='utf-8').read():
    bad.append('UiEvent')
    print('     ✗ UiEvent.kt 里没有 sealed interface UiEvent ⇒ 替代机制不见了')
# 期望值从 UiSurface 枚举**反推**，不写死数字：落点数 = 队列数 = 对外 Flow 数。
# 写死的话每加一个落点就得回来改守卫（今天 #4c 加了 Settings 就撞上）；反推则能抓住两种真错误 ——
# 「有落点没队列」（事件发出去没人收，提示静默消失）与「有队列没落点」（多出来的队列永远空着）。
m = re.search(r'enum class UiSurface \{([^}]*)\}', io.open(UIE, encoding='utf-8').read())
surfaces = [x.strip() for x in m.group(1).split(',') if x.strip()] if m else []
if not surfaces:
    bad.append('UiSurface')
    print('     ✗ 在 UiEvent.kt 里找不到 enum class UiSurface { … } ⇒ 反推不出期望值')
ch = src.count('Channel<UiEvent>')
raf = src.count('receiveAsFlow()')
if surfaces and ch != len(surfaces):
    bad.append('Channel')
    print('     ✗ Channel<UiEvent> 实测 %d 个、落点 %d 个（%s）—— 应一一对应'
          % (ch, len(surfaces), ' / '.join(surfaces)))
if surfaces and raf != len(surfaces):
    bad.append('receiveAsFlow')
    print('     ✗ receiveAsFlow() 实测 %d 个、落点 %d 个 —— 每条队列应有一个对外 Flow'
          % (raf, len(surfaces)))
if len(re.findall(PAT, probe)) != 1:
    bad.append('probe')
if bad:
    print('%d 个；另有 %d 处 ✗（见上）⇒ 本项不通过' % (len(events), len(bad)))
else:
    print('0 个 ✓ 对照通过（正则抓得到样本）；替代机制在位：UiEvent + %d 条 Channel + %d 个 receiveAsFlow'
          '（与 UiSurface 的 %d 个落点一一对应）' % (ch, raf, len(surfaces)))
PY
)" '目标「0 个」。这是 ROADMAP #4「守卫交接」那次交接的产物：#4a 之前这里放的是临时守卫「一次性事件必须有 consume 配对」（2026-09-18 核查发现本仓用「可空 StateFlow + 手工 consume」建模 4 个一次性事件，4/4 都记得清空 ⇒ 当时并没有重放 bug，靠纪律不靠机制）；4a 把 4 个全改成 `UiEvent` + `Channel`（接收即出队）之后，守卫换成「**禁止再出现**，超出即 ✗，豁免须登记理由」，同一次提交完成、不留两套。自带阳性对照（内嵌样本）与替代机制在场检查（`Channel` / `receiveAsFlow` 计数）—— 因为「0 个」这种结果若不先证明探测真的在工作，就没有意义（见本脚本头部第 3 条硬约定）。'
row '文档写死的关键数 vs 实测（不符即 ✗）' "$(python3 - <<'PY' 2>/dev/null || echo '需 python3'
import glob, io, re
MAIN = 'app/src/main'
DOCS = ['CLAUDE.md', 'README.md', 'docs/ARCHITECTURE.md', 'docs/DESIGN_SPEC.md',
        'docs/REQUIREMENTS.md', 'docs/WORKFLOW.md', 'docs/MIUIX_UPGRADE.md',
        'docs/ROADMAP.md', 'devlog/INDEX.md']
kt = {}
for f in glob.glob(MAIN + '/**/*.kt', recursive=True):
    kt[f] = io.open(f, encoding='utf-8').read()
def occ(pat):
    n = 0
    for t in kt.values():
        n += len(re.findall(pat, t))
    return n
def wcl(p):
    return sum(1 for _ in io.open(p, encoding='utf-8'))
REPO = MAIN + '/java/com/agon/app/data/FoodRepository.kt'
VMF = MAIN + '/java/com/agon/app/viewmodel/AppViewModel.kt'

# ⚠️ 这份 strip_comments 是上面「测试」段那份的副本（两个 heredoc 各自独立，无法共享定义）。
# 改一份必须改另一份 —— 否则「单测数」与「中文字面量」两条会各用一套剥注释逻辑，
# 数字对不上还查不出原因（09-18 加第 21 条比对时特意留这行提醒）。
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

def cn_literals():
    n = 0
    for t in kt.values():
        s = strip_comments(t)
        for m in re.finditer(r'"""(.*?)"""|"((?:[^"\\]|\\.)*)"', s, re.S):
            c = m.group(1) if m.group(1) is not None else m.group(2)
            if re.search(r'[\u4e00-\u9fff]', c or ''):
                n += 1
    return n
# (指标, 文档里提取数值的正则, 实测值)
ITEMS = [
    # ⚠️ 数字**两边**都要吃掉星号：早先只写了前面的 \*{0,2}，于是 "`FoodRepository.kt` **950** 行"
    # 这种（数字后面也有 `**`）根本匹配不上 ⇒ 同一次运行里 devlog/INDEX 的 950 被抓到、ROADMAP 的 950 漏网，
    # 汇总还报「17 处比对、不符 1 处」，看着像只有一处要改（#5b 当场撞上）。5c 拆完这个数还会再变，别再让它漏。
    ('FoodRepository.kt 行数', r'`FoodRepository\.kt`\s*\*{0,2}([\d,]+)\*{0,2}\s*行', wcl(REPO)),
    ('FoodRepository 类级函数', r'\*\*(\d+)\*\*\s*个类级函数',
     len(re.findall(r'^    (?:private |internal |suspend |override )*fun ', io.open(REPO, encoding='utf-8').read(), re.M))),
    ('corruptedKeys 出现次数', r'`corruptedKeys`\s*被引用\s*\*\*(\d+)\*\*\s*处', occ(r'corruptedKeys')),
    ('stateIn( 出现次数', r'`stateIn\(`\s*\*\*(\d+)\*\*\s*处', occ(r'stateIn\(')),
    ('WhileSubscribed 出现次数', r'`WhileSubscribed`\s*\*\*(\d+)\*\*\s*处', occ(r'WhileSubscribed')),
    ('Channel< 出现次数', r'`Channel<`\s*\*\*(\d+)\*\*\s*处', occ(r'Channel<')),
    ('Result< 出现次数', r'`Result<`\s*\*{0,2}仅?\s*\*{0,2}(\d+)\*\*\s*处', occ(r'Result<')),
    ('now() 直接调用', r'`now\(\)`\s*\*\*(\d+)\*\*\s*处',
     occ(r'LocalDate\.now|LocalDateTime\.now|LocalTime\.now')),
    ('contentDescription = null', r'`contentDescription = null`\s*\*\*(\d+)\*\*\s*处',
     occ(r'contentDescription\s*=\s*null')),
    ('strings.xml 条目', r'`strings\.xml`\s*\*\*(\d+)\*\*\s*条',
     len(re.findall(r'<string', io.open(MAIN + '/res/values/strings.xml', encoding='utf-8').read()))),
    ('Application 子类', r'`Application`\s*子类\s*\*\*(\d+)\*\*\s*个',
     occ(r'class\s+[A-Za-z]*\s*:\s*Application\b')),
    # 第 21 条（09-18 加）：ROADMAP #7 与 INDEX #7 的两种写法都吃 —— 原写 583、实测 576（核查第 19 处），
    # 腐烂了 1 天没人报警，因为这一项此前不在比对清单里。星号两侧都吃（与 FoodRepository 那条同一个教训）。
    ('含中文的字符串字面量', r'(?:含中文的字符串字面量|源码中文字面量)\s*\*{0,2}([\d,]+)\*{0,2}\s*处',
     cn_literals()),
]
bad = hits = 0
for d in DOCS:
    try:
        ls = io.open(d, encoding='utf-8').read().split('\n')
    except OSError:
        continue
    for i, line in enumerate(ls, 1):
        for label, pat, real in ITEMS:
            for m in re.finditer(pat, line):
                hits += 1
                doc = int(m.group(1).replace(',', ''))
                if doc != real:
                    bad += 1
                    print('     ✗ %s:%d %s 文档写 %d，实测 %d ⇒ 改文档或改成指针' % (d, i, label, doc, real))
print('%d 处写死的数被比对，不符 %d 处' % (hits, bad))
PY
)" '2026-09-18 核查的第 11/12/13 处都是这一类：**文档里的数或论述与代码不符，而没有任何东西会报警**（`4,204` 行、`37` 个角色、`6` 份批注、`没有任何页面消费它`、`跨零点无法单测`）。本行把「写死的关键数」逐个拿去和实测比。⚠️ 它**刻意与「指针优于数字」的原则相反**：不是鼓励写数字，而是让还留着的数字烂不掉 —— 报警的正确修法通常是**把数字换成指针**，而不是改数字。⚠️ **验收目标值要用散文写**（别写成 `**N** 个/处/行`），否则会被当成现状断言而误报 —— 本行上线当天就踩了一次（当时 ROADMAP 5a 的验收列写着「`Application` 子类 **1** 个」，那是**目标**、现状是 0，于是天天报不符；#5a 落地后目标变成了现状，这一处才自洽。**教训仍然成立**：目标值一律散文写）'
row 'devlog 文件数 / 总行数' "$(ls devlog/*.md | wc -l | tr -d ' ') 个 / $(wc -l devlog/*.md | tail -1 | awk '{print $1}') 行" '含 INDEX.md'
row 'docs/audits 报告数' "$(ls docs/audits/*.md | wc -l | tr -d ' ') 份" '历史审计报告，只加批注不改写'
row '含 2026-09-17 批注的报告' "$(grep -l '2026-09-17 状态批注\|2026-09-17 追加' docs/audits/*.md | wc -l | tr -d ' ') 份" '7 份新增顶部批注 + 3 份在既有批注上追加（md3-audit / chileme-review / fix-plan；fix-plan 两者都有 ⇒ 去重 9 份）'
row '顶部无任何状态批注的报告' "$(for f in docs/audits/*.md; do sed -n '2,12p' "$f" | grep -q '状态批注\|修复状态\|校订\|复核批注\|2026-09-17 追加' || echo "$f"; done | wc -l | tr -d ' ') 份" '目标是 0：读者应能一眼判断「这条还成立吗」'

echo
echo '════ 阳性对照（这两项证明上面的 grep 真在工作，零结果不是假的）════'
row '已知必然存在：@Composable' "$(occ '@Composable' "$MAIN") 处" '若这里也是 0，说明上面的零结果全部不可信'
row '已知必然不存在：ZZZ_NOT_A_SYMBOL' "$(occ 'ZZZ_NOT_A_SYMBOL' "$MAIN") 处" '必须是 0'
