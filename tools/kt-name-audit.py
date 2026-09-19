#!/usr/bin/env python3
"""大写名有没有来源（**本地工具，不在 CI 里**）—— 用来在没有编译器的沙箱里替 kotlinc 站一班岗。

为什么需要它
------------
本仓唯一的编译器是 GitHub Actions。`tools/kt-lexcheck.py` 管词法（配平 / 空白 / 未使用 import / 别名），
但它**不看名字能不能解析**。09-19 #11d② 就栽在这儿：把统计页的「环图 + 图例」区块抽成
`StatsCategorySection.kt` 时，新写了一个参数 `chartColors: List<Color>` —— 而**原文件里从来没有 `Color`
这个 import**（原来是 `val chartColors = remember { appChartColors() }`，类型靠推断）。所有「按原文件
import 表收敛 / 复制」的做法结构上都看不见这种**新引入的名字**，于是 CI 报
`Unresolved reference 'Color'.`，整批三刀的证据一起悬空。⇒ 搬运之后必须做一次「每个大写名从哪来」的清点。

判据
----
对目标文件**代码部分**（剥掉注释与字符串）里出现的每个首字母大写标识符（`T` 这类单字母除外、
`.` 之后的成员访问除外、import 行本身除外），要求它至少满足一条：
  1. 本文件自己的顶层声明；
  2. 同包（同一 `package`）任一源文件的顶层声明 —— 同包不需要 import，这正是「同包移动 import 零改动」的根据；
  3. 本文件 import 了它（简单名或 `as` 别名都算）；
  4. 本文件有通配 import（`import x.*`）—— 此时一律放行，本地无法展开；
  5. 在 Kotlin/Android 的生成物与惯用白名单里（`R` / `BuildConfig` / `it` / 注解 / 委托约定名等）。
都不满足就分两种报法：**别处有声明但没 import** ⇒ 报「缺 import（附建议的完整路径）」；**全仓都找不到** ⇒ 报
「未解析（可能拼错，或来自未 import 的外部库）」。后者正是 CI 那一行 `Unresolved reference` 的本地版。

自检（`--selftest`）
--------------------
工具本身也要能证明它会红：4 个对照（1 份必报「缺 import」、1 份必报「未解析」、2 份必不报）。
`Color` 那份就是 09-19 事故的最小复现。

已知边界（报错了先想这三条）
----------------------------
- 只看**大写开头**的名字：小写函数调用的解析问题（例如 `import x.foo` 丢了）它不管，那条仍归
  `tools/move-importcheck.py` 与 `kt-lexcheck.py` 判据 4。
- **嵌套 / 成员类型**（`ComposeNode` 里的 `Frame`、某类里的 `Walker`）不在顶层声明表里 ⇒ 现状：主代码 0 处、
  测试代码 2 处（`MiuixDialogContentTest`）—— 看到这两处可以直接放过，别去改工具去迁就它们。
- 源码根由文件路径倒推（`…/src/{main,test,androidTest}/java`）；test / androidTest 会自动并上 sibling 的 main，
  别的 source set（如 `app/src/debug`）不会 ⇒ 只在这些根里找声明，找不到就报「未解析」，看到这类报告先确认源集。
- **只看签名的类型位置**：默认值表达式（`x: Long = MAX_SNAPSHOTS`）、函数体里的成员访问（`Dispatchers.IO`）、
  枚举项（`EXPIRED`）都不参与 —— 它们不是"新引入的类型名"，硬查就是一片假警。
- 报法分两档：「缺 import」与「未解析」。后者也可能只是**枚举项/嵌套类型**（本仓没有这种写在签名里的，真出现时先看
  是不是外部库类型）。⇒ 它是**搬运前后的补充检查**，不替代 `kt-lexcheck.py` 与 CI。
- 白名单是硬编码的一小撮（见 `BUILTIN`）；扩了要同时扩自检的「必不报」对照。
"""
import os
import re
import sys

TOP_DECL = re.compile(
    r'^(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:public |internal |private |abstract |open |sealed |data |value |annotation |enum |const |suspend |inline |operator |tailrec )*'
    r'(?:class|interface|object|fun|val|var)\s+(?:<[^>]*>\s*)?(\w+)',
    re.M,
)
IMPORT = re.compile(r'^import\s+([\w.]+)(?:\s+as\s+(\w+))?', re.M)
PKG = re.compile(r'^package\s+([\w.]+)', re.M)
NAME = re.compile(r'(?<![\w.])([A-Z][A-Za-z0-9_]{1,})')
# 只看**函数签名**：参数表 + 返回类型。这是 09-19 事故的准确形状（新加的参数类型 `List<Color>`）。
# ⚠️ 不要把它扩成"整份文件的大写名都查"：`TAG` / `const val` / 枚举项 / `runCatching` 这类**成员级与作用域级**
#    名字不在顶层声明表里，全文件扫会在本仓刷出 150 条假警（写完当天实测）——噪声比无工具更坏。
SIG = re.compile(r'\bfun\s+(?:<[^>]*>\s*)?\w+\s*\((.*?)\)\s*(?::\s*([\w.<>,\s?]+?))?\s*(?:\{|=|->|$)', re.S)

BUILTIN = set("""
Unit Any Nothing String Int Long Boolean Float Double Byte Short Char Number List MutableList Set
MutableSet Map MutableMap Collection MutableCollection Iterable Iterator Sequence Pair Triple
ArrayList HashMap HashSet LinkedHashMap LinkedTreeMap LinkedHashSet Array BooleanArray ByteArray
CharArray DoubleArray FloatArray IntArray LongArray ShortArray ArrayDeque Comparable Comparator
Enum Exception Error RuntimeException IllegalStateException IllegalArgumentException
UnsupportedOperationException NullPointerException ClassNotFoundException IndexOutOfBoundsException
Result CharSequence StringBuilder Throwable AutoCloseable Suppress Volatile JvmStatic JvmField
JvmName Deprecated Function0 Function1 Function2 Runnable Math System Object
R BuildConfig
""".split())


def strip_noise(text):
    """剥块注释（Kotlin 块注释会嵌套）→ 行注释 → 字符串（含三引号与模板）。"""
    out, i, n, depth = [], 0, len(text), 0
    while i < n:
        c = text[i]
        two = text[i:i + 2]
        if depth == 0 and two == '/*':
            depth, i = 1, i + 2
            continue
        if depth > 0:
            # ⚠️ 逐字走：步长 2 会跨过错位一个字符的 `*/`（KDoc 每行开头的 ` * ` 就正好制造这种错位，
            #    09-19 写完当天就把整份文件当注释吃掉了）
            if two == '/*':
                depth += 1
                i += 2
                continue
            if two == '*/':
                depth -= 1
                i += 2
                continue
            i += 1
            continue
        if two == '//':
            j = text.find('\n', i)
            i = n if j < 0 else j
            continue
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            i = n if j < 0 else j + 3
            out.append('""')
            continue
        if c == '"':
            i += 1
            while i < n:
                if text[i] == '\\':
                    i += 2
                    continue
                if text[i] == '"':
                    i += 1
                    break
                if text[i] == '$' and i + 1 < n and text[i + 1] == '{':
                    d, i = 1, i + 2
                    while i < n and d:
                        if text[i] == '{':
                            d += 1
                        elif text[i] == '}':
                            d -= 1
                        i += 1
                    out.append(' ')
                    continue
                i += 1
            out.append('""')
            continue
        if c == "'":
            i += 1
            while i < n and text[i] != "'":
                i += 2 if text[i] == '\\' else 1
            i += 1
            out.append(' ')
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def source_root(path):
    """从文件路径倒推出源码根（`…/app/src/main/java` 这一级），用于收集全仓顶层声明。"""
    d = os.path.dirname(os.path.abspath(path))
    while d != os.path.dirname(d):
        if os.path.basename(d) in ('java', 'kotlin') and os.path.basename(os.path.dirname(d)) in ('main', 'test', 'androidTest'):
            return d
        d = os.path.dirname(d)
    return os.path.dirname(os.path.abspath(path))


def collect_decls(roots):
    """{名字: set(包名)} —— 全仓（限给定源码根）的顶层声明表。"""
    decls = {}
    for root in roots:
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = [x for x in dirnames if x not in ('build', '.git')]
            for fn in filenames:
                if not fn.endswith(('.kt', '.java')):
                    continue
                try:
                    src = open(os.path.join(dirpath, fn), encoding='utf-8').read()
                except OSError:
                    continue
                m = PKG.search(src)
                pkg = m.group(1) if m else ''
                for name in TOP_DECL.findall(strip_noise(src)):
                    decls.setdefault(name, set()).add(pkg)
    return decls


def audit(path, decls):
    src = open(path, encoding='utf-8').read()
    pkg = (PKG.search(src).group(1) if PKG.search(src) else '')
    imports = set()
    for m in IMPORT.finditer(src):
        imports.add(m.group(2) or m.group(1).split('.')[-1])
        if m.group(1).endswith('.*'):
            return []  # 通配 import：本地展不开，整体放行
    own = set(TOP_DECL.findall(strip_noise(src)))
    code = '\n'.join(l for l in strip_noise(src).split('\n') if not l.strip().startswith('import'))
    used = set()
    for m in SIG.finditer(code):
        for part in (m.group(1) or '', m.group(2) or ''):
            # `onOpenItem: (String) -> Unit` 这类形参：整段拿掉「名字: 」左边的形参名，只留类型
            for seg in part.split(','):
                seg = seg.split('=', 1)[0]          # 默认值是**表达式**，不是类型位置（`IO` / `Builder` / 枚举项都从这儿来）
                if ':' in seg:
                    seg = seg.split(':', 1)[1]
                for tok in re.findall(r'[A-Za-z_][\w.]*', seg):
                    used.add(tok.split('.')[0])   # 点号链只看**首段**：要 import 的是外层名字（`AppTextScale.Body` ⇒ `AppTextScale`）
    used = {n for n in used if re.match(r'^[A-Z]', n) and len(n) > 1}
    out = []
    for name in sorted(used):
        if name in imports or name in own or name in BUILTIN:
            continue
        pkgs = decls.get(name)
        if pkgs:
            if pkg in pkgs:
                continue  # 同包可见，不需 import
            out.append('%s: 别处有声明（%s）但本文件没 import' % (name, ', '.join(sorted(pkgs))))
        else:
            out.append('%s: 全仓找不到顶层声明 ⇒ 未解析（拼错 / 外部库没 import）' % name)
    return out


CONTROLS = [
    # (源码, 期望报的条数, 说明)
    ('package p\nimport a.b.List\ninternal fun f(x: List<Color>) {}\n', 1, '缺 Color import（09-19 事故的最小复现）'),
    ('package p\ninternal fun f(x: StatsUiState) {}\n', 0, '同包声明 ⇒ 不需要 import'),
    ('package p\nimport q.StatsUiState\ninternal fun f(x: StatsUiState) {}\n', 0, '跨包但已 import'),
    ('package p\ninternal fun f(x: NoSuchThingAtAll) {}\n', 1, '全仓没有 ⇒ 未解析'),
    ('package p\n/**\n * 多行 KDoc（每行开头有 ` * `）\n *\n * @param x 说明\n */\ninternal fun StatsProbe(x: Unit) {}\n', 0,
     'KDoc 的 ` * ` 不能把后面的声明吃掉（步长 2 的老 bug）'),
    ('package p\n/** /* 嵌套块注释 */ \u4e5f \u8981能配平 */\ninternal fun f(x: NoSuchThingAgain) {}\n', 1,
     '嵌套块注释结束后仍能看见未解析名'),
]


def selftest():
    bad = 0
    for src, expect, note in CONTROLS:
        d = os.path.abspath('build/kt-name-audit-selftest')
        os.makedirs(d, exist_ok=True)
        f = os.path.join(d, 'Case.kt')
        open(f, 'w', encoding='utf-8').write(src)
        decls = {'StatsUiState': {'p', 'q'}}
        got = len(audit(f, decls))
        ok = got == expect
        bad += 0 if ok else 1
        print('%s %-52s 期望报 %d 条，实报 %d 条' % ('✓' if ok else '✗', note, expect, got))
    print('自检：%d 个对照，失败 %d 个' % (len(CONTROLS), bad))
    return 1 if bad else 0


def main(argv):
    if '--selftest' in argv:
        return selftest()
    targets = [a for a in argv if not a.startswith('-')]
    if not targets:
        print(__doc__)
        print('用法：python3 tools/kt-name-audit.py 文件… | --selftest')
        return 2
    roots = {source_root(t) for t in targets}
    # test / androidTest 看得到 main 的声明 ⇒ 必须把 sibling 的 `src/main/java` 也收进来，
    # 否则测试里引用主代码的类型会全报「未解析」（写完当天实测 7 条假警）。
    for r in sorted(roots):
        for kind in ('test', 'androidTest'):
            if os.sep + 'src' + os.sep + kind + os.sep in r + os.sep:
                alt = r.replace(os.sep + 'src' + os.sep + kind + os.sep, os.sep + 'src' + os.sep + 'main' + os.sep)
                if os.path.isdir(alt):
                    roots.add(alt)
    decls = collect_decls(sorted(roots))
    bad = 0
    for t in targets:
        hits = audit(t, decls)
        for h in hits:
            print('%s: %s' % (t, h))
        bad += len(hits)
        print('%s %s（全仓顶层声明表 %d 个名字）%s' % ('✗' if hits else '✓', t, len(decls), '' if not hits else ' ⇒ %d 处' % len(hits)))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
