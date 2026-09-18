#!/usr/bin/env python3
"""Kotlin 词法预检（**本地工具，不在 CI 里**）。

为什么需要它
------------
沙箱里**没有 JDK**：编译不了、跑不了单测、也跑不了 detekt（要 JVM）与 ktlint 的完整规则集，
CI 是唯一的编译器。一次 CI 约 4 分钟，而 concurrency 又会取消同分支上中间的 run
（`.github/workflows/build.yml:10-12`）⇒ 每个能被本地抓到的错，都值得在 push 前抓掉。
09-16/09-17 三轮里，CI 红过的 3 次中有 2 次（KDoc 配不平、`matchBrace` off-by-one）
**都属于本地可抓**，这条工具就是那两次教训的固化。

判据（每条都对应踩过的坑）
--------------------------
1. **配平**：跑完整份文件后必须回到最外层（brace/paren/bracket 全 0、模板栈空、末状态 = code）。
   Kotlin 的块注释**会嵌套**、字符串模板 `${…}` 里的表达式**是代码**、`"*/*"` 这种 MIME 通配符
   会被朴素正则当成块注释开头（`ImeHandlingTest.codeOnly()` 就因此吞掉过约 600 行真代码）
   ⇒ 必须走词法状态机，不能用正则剥注释。
2. **行尾空白 / 文件末尾恰好一个换行 / 连续空行 / `}` 前空行 / 冗余字符串模板**
   —— 正是 `.editorconfig` 里开着的 ktlint 那 6 条中的 5 条（第 6 条 no-wildcard-imports 用 grep 即可）。
3. **未使用的 import** —— ⚠️ **CI 现在没有这条门禁**：ktlint 的同类规则官方已弃用（见 `.editorconfig`），
   而 detekt 的 `UnusedImports` **默认 `active: false`**（1.23.8 的 default-detekt-config.yml:744 已核对），
   本仓也没显式打开 ⇒ `detekt.yml:158` 那句「这里兜底」是不成立的（2026-09-18 核查第 14 处）。
   所以这一条是本地唯一防线，比 CI 更严。

已知的两类"报了但不是错"
--------------------------
- **只在注释/KDoc 里出现的 import**：会报，但带「（只在注释/字符串里出现）」后缀。
  detekt 若开启是**不报**这类的（它把 KDoc 的 `[Name]` 引用算作用法）⇒ 看到后缀自行判断，通常是死 import 但不违规。
- **未使用 import 的判定按词法**：`by remember { … }` 隐式用到的 `getValue`/`setValue`、
  解构用到的 `componentN` 等运算符约定看不见调用点，已按 detekt 的做法白名单放行。

自检（`--selftest`）
--------------------
本仓的规矩是「零结果必须做阳性对照」——`tools/ci-gates.sh` 的 `detekt_selftest` 就是为此存在。
这条工具自己也可能被改坏（09-17 就发生过：插守卫时留了一行空 `row` 调用，`set -u` 下报错还吞掉后续指标），
所以把 12 个对照（1 份好文件 + 9 类必报 + 2 类必不报）内建成 `--selftest`，改完脚本先跑它。
"""
import io
import os
import re
import sys
import tempfile

def lex(src):
    """返回 (code_only_text, ok, detail)。注释与字符串内容被空格替换，**模板 ${…} 里的代码保留**
    （否则模板里用到的标识符会被误判成未使用 import）。"""
    out = []
    i, n = 0, len(src)
    paren = bracket = 0
    stack = []            # 'brace' | ('tmpl', 'str'|'raw')
    mode = 'code'         # code | line | block | str | raw | char
    block_depth = 0
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        if mode == 'code':
            if c == '/' and nxt == '/':
                mode, i = 'line', i + 2
                out.append('  ')
                continue
            if c == '/' and nxt == '*':
                mode, block_depth, i = 'block', 1, i + 2
                out.append('  ')
                continue
            if src.startswith('"""', i):
                mode, i = 'raw', i + 3
                out.append('   ')
                continue
            if c == '"':
                mode, i = 'str', i + 1
                out.append(' ')
                continue
            if c == "'":
                mode, i = 'char', i + 1
                out.append(' ')
                continue
            if c == '{':
                stack.append('brace')
            elif c == '}':
                if stack and stack[-1] == 'brace':
                    stack.pop()
                elif stack and isinstance(stack[-1], tuple) and stack[-1][0] == 'tmpl':
                    mode = stack.pop()[1]      # 模板结束，回到字符串
                    out.append(' ')
                    i += 1
                    continue
                else:
                    stack.append('extra}')     # 多出来的 } —— 记为不平衡
            elif c == '(':
                paren += 1
            elif c == ')':
                paren -= 1
            elif c == '[':
                bracket += 1
            elif c == ']':
                bracket -= 1
            out.append(c)
            i += 1
            continue
        if mode == 'line':
            if c == '\n':
                mode = 'code'
                out.append('\n')
            else:
                out.append(' ')
            i += 1
            continue
        if mode == 'block':
            if c == '/' and nxt == '*':
                block_depth += 1
                out.append('  ')
                i += 2
                continue
            if c == '*' and nxt == '/':
                block_depth -= 1
                out.append('  ')
                i += 2
                if block_depth == 0:
                    mode = 'code'
                continue
            out.append('\n' if c == '\n' else ' ')
            i += 1
            continue
        if mode in ('str', 'raw'):
            if mode == 'str' and c == '\\':
                out.append('  ')
                i += 2
                continue
            if mode == 'raw' and src.startswith('"""', i):
                mode, i = 'code', i + 3
                out.append('   ')
                continue
            if mode == 'str' and c == '"':
                mode, i = 'code', i + 1
                out.append(' ')
                continue
            if c == '$' and nxt == '{':
                stack.append(('tmpl', mode))   # 模板里的表达式是**代码**
                mode = 'code'
                out.append('  ')
                i += 2
                continue
            if c == '$' and (nxt.isalpha() or nxt == '_'):
                # `$name` 形式的简单模板：标识符本身算代码里的引用（保留），$ 号补空格
                j = i + 1
                ident = ''
                while j < n and (src[j].isalnum() or src[j] == '_'):
                    ident += src[j]
                    j += 1
                out.append(' ' + ident)
                i = j
                continue
            out.append('\n' if c == '\n' else ' ')
            i += 1
            continue
        if mode == 'char':
            if c == '\\':
                out.append('  ')
                i += 2
                continue
            if c == "'":
                mode = 'code'
            out.append(' ')
            i += 1
            continue
    braces = sum(1 for x in stack if x == 'brace') + sum(1 for x in stack if x == 'extra}')
    tmpls = sum(1 for x in stack if isinstance(x, tuple))
    ok = paren == 0 and bracket == 0 and not stack and mode == 'code'
    detail = f'未闭合 brace={braces} paren={paren} bracket={bracket} 模板={tmpls} 末状态={mode}'
    return ''.join(out), ok, detail


def check(path):
    src = io.open(path, encoding='utf-8').read()
    problems = []
    code, ok, detail = lex(src)
    if not ok:
        problems.append(f'配平失败：{detail}')

    lines = src.split('\n')
    if not src.endswith('\n') or src.endswith('\n\n'):
        problems.append('文件末尾不是恰好一个换行')
    for idx, ln in enumerate(lines[:-1], 1):
        if ln != ln.rstrip():
            problems.append(f'{idx}: 行尾空白')
    for idx in range(len(lines) - 2):
        if not lines[idx].strip() and not lines[idx + 1].strip():
            problems.append(f'{idx + 1}-{idx + 2}: 连续空行')
    for idx, ln in enumerate(lines[:-1], 1):
        if ln.strip() == '}' and idx >= 2 and not lines[idx - 2].strip():
            problems.append(f'{idx}: `}}` 前有空行')

    # 未使用 import（剥注释与字符串后找标识符）
    # ⚠️ 白名单：Kotlin 的运算符/委托约定是**隐式调用**，源码里不会出现同名标识符 ——
    #    `by remember { mutableStateOf(x) }` 用到 getValue/setValue，`val (a, b) = …` 用到 componentN。
    #    detekt 的 UnusedImports 同样对这类做了特判，故这里不能报。
    OPERATORS = {
        'getValue', 'setValue', 'provideDelegate', 'invoke', 'contains', 'iterator',
        'compareTo', 'plus', 'minus', 'times', 'div', 'rem', 'mod', 'unaryMinus', 'unaryPlus',
        'inc', 'dec', 'rangeTo', 'rangeUntil', 'get', 'set', 'plusAssign', 'minusAssign',
        'component1', 'component2', 'component3', 'component4', 'component5',
    }
    body = re.sub(r'^import .*$|^package .*$', '', code, flags=re.M)
    for m in re.finditer(r'^import\s+([\w.]+)(?:\s+as\s+(\w+))?', code, re.M):
        fq, alias = m.group(1), m.group(2)
        name = alias or fq.rsplit('.', 1)[-1]
        if name in OPERATORS:
            continue
        if not re.search(r'\b%s\b' % re.escape(name), body):
            no_imports = re.sub(r'^import .*$|^package .*$', '', src, flags=re.M)
            elsewhere = bool(re.search(r'\b%s\b' % re.escape(name), no_imports))
            problems.append(f'未使用的 import：{fq}' + ('（只在注释/字符串里出现）' if elsewhere else '（全文件再没出现）'))

    # 冗余字符串模板 "${simple}"
    for m in re.finditer(r'\$\{(\w+)\}(\w?)', src):
        if not m.group(2):      # 后一个字符是标识符字符 ⇒ 花括号是必需的，不报
            problems.append('冗余字符串模板：可写成 $%s' % m.group(1))
    return problems




# ------------------------------------------------------------------ 自检
GOOD = '''package x

import java.util.UUID

/** KDoc 里的 [UUID] 与 "引号" 都不该影响判定。 */
fun a(s: String, n: Int): String {
    val id = UUID.randomUUID()
    return "值：${id.toString()} 与 $s 与 ${n}天"
}
'''

# (文件名, 内容, 期望命中的关键词或 None = 期望干净)
CONTROLS = [
    ('good', GOOD, None),
    ('missing_brace', GOOD.replace(' 与 ${n}天"\n}\n', ' 与 ${n}天"\n'), '配平失败'),
    ('trailing_space', GOOD.replace('randomUUID()', 'randomUUID()   '), '行尾空白'),
    ('unused_import', GOOD.replace('import java.util.UUID',
                                   'import java.util.UUID\nimport java.util.concurrent.atomic.AtomicInteger'),
     '未使用的 import'),
    ('consecutive_blank', GOOD.replace('import java.util.UUID\n\n/**', 'import java.util.UUID\n\n\n/**'),
     '连续空行'),
    ('blank_before_rbrace', GOOD.replace('与 ${n}天"\n}', '与 ${n}天"\n\n}'), '`}` 前有空行'),
    ('no_final_newline', GOOD.rstrip('\n'), '文件末尾'),
    ('redundant_template', GOOD.replace('${id.toString()}', '${s}'), '冗余字符串模板'),
    ('unbalanced_paren', GOOD.replace('randomUUID()', 'randomUUID('), '配平失败'),
    ('comment_only_usage', GOOD.replace('    val id = UUID.randomUUID()\n', '    val id = "x"\n'),
     '未使用的 import'),
    # 两类**必不报**（各自独立隔离一种情形，不拿 GOOD 充数）：
    # ① import 只在 `$name` 形式的模板里被用到 —— 词法上它在字符串内，
    #    若把字符串内容一律抹成空格就会误报「未使用」（本仓真实案例：SettingsScreen 的 CLOUD_BACKUP_KEEP）
    ('template_only_usage', '''package x

import com.agon.app.data.CLOUD_BACKUP_KEEP

fun a() = "云端保留最近 $CLOUD_BACKUP_KEEP 次备份"
''', None),
    # ② 花括号是**必需**的：`}` 后紧跟标识符字符（中文也算 —— Kotlin 标识符允许 Unicode 字母），
    #    去掉花括号会改变含义，ktlint 也不报（本仓真实案例：FoodRepository 的 "${X}ms"、EditFoodScreen 的 "${d}天"）
    ('brace_required_before_word', '''package x

fun a(i: Int, n: Int, d: Int) = "第${i}_101 号、${n}天、${d / 30}个月、等${n}ms"
''', None),
]


def selftest():
    tmp = tempfile.mkdtemp(prefix='kt-lexcheck-')
    bad = 0
    for name, text, expect in CONTROLS:
        f = os.path.join(tmp, name + '.kt')
        io.open(f, 'w', encoding='utf-8').write(text)
        probs = check(f)
        hit = next((p for p in probs if expect and expect in p), None)
        if expect is None:
            ok = not probs
            detail = '干净' if ok else '；'.join(probs)[:70]
        else:
            ok = hit is not None
            detail = hit[:70] if hit else ('没报「%s」' % expect + ('（报了别的：%s）' % probs[0][:40] if probs else '（完全没报）'))
        print('  %s %-28s %s' % ('✓' if ok else '✗', name, detail))
        if not ok:
            bad += 1
    print('自检：%d 个对照，失败 %d 个' % (len(CONTROLS), bad))
    return 1 if bad else 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    targets = [a for a in sys.argv[1:] if not a.startswith('-')]
    if not targets:
        targets = sorted(
            os.path.join(r, f)
            for r, _, fs in os.walk('app/src')
            for f in fs if f.endswith('.kt')
        )
    bad = 0
    for p in targets:
        probs = check(p)
        if probs:
            bad += 1
            print('✗ %s' % p)
            for x in probs:
                print('    - %s' % x)
        else:
            print('✓ %s' % p)
    print('共 %d 份，有问题 %d 份' % (len(targets), bad))
    sys.exit(1 if bad else 0)
