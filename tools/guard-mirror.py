#!/usr/bin/env python3
"""源码文本守卫的本地镜像 —— 没有 JVM 也能查出「守卫指向的文件里已经没有它断言的东西」。

    python3 tools/guard-mirror.py            # 默认扫 app/src/test/java
    python3 tools/guard-mirror.py <测试目录>

为什么要有它（#5c 拆分期间用两次红的换来的）
----------------------------------------
本仓有一类测试不跑被测代码，而是**读主代码源文件、断言里面有没有某句字面串**
（`CorruptGuardTest` / `CompactConsumptionTest` / `UiEventTest` 等，因为没有 Robolectric，
这是唯一能把「某处逻辑不许被改回去」钉住的办法）。这类守卫有个先天弱点：
**代码一搬家，守卫就指着旧文件**，而它自己不会报错，只会在 CI 上红。

#5c-4 就是这样：`deleteConsumption` 搬去 `FoodConsumption.kt`，而 `CompactConsumptionTest`
仍读 `FoodRepository.kt` 断言 `if (!record.isDeletable())` ⇒ CI 红。当时只手工镜像了一个测试文件，
没想到别的文件也在读仓库源码。CI 一轮约 5 分钟，本地这几毫秒能省一整轮。

三条「别自己骗自己」的规则（都是踩过才加的）
------------------------------------------
1. **分极性**：`assertFalse(... contains("x"))` 与 `!src.contains("x")` 里的字面串本来就该找不到。
   不分极性会满屏假警报（第一版报了 3 个，2 个是反向断言）。注意 `repo!!.` 里的 `!!` 不是取反。
2. **按 `@Test` 分块建「变量 → 文件」映射**：同一个测试文件里 `val repo = read(...)` 会在不同测试
   函数指向不同文件（`CorruptGuardTest` 的 `src` 一会儿是 HomeScreen、一会儿是 AppViewModel）。
   文件级映射会让后一个赋值覆盖前一个 ⇒ 张冠李戴的假警报（第二版因此多报 3 处）。
3. **只对「变量确实是整份文件」的断言下结论**：`functionBody(...)` 截出的函数体、`UiEventTest`
   截出的事件类片段，拿整份文件比对必然假警报 ⇒ 这类明确列进「作用域受限，跳过」清单，
   由 CI 覆盖，**不假装查过**。

覆盖范围（诚实说明）
------------------
只镜像 `xxx.contains("字面串")` 这一种形状。**不覆盖**：
- `Regex("...").findAll(src).count()` 这类计数断言 —— #5c-7 就有一处（凭据写入的
  `if (enc != null)` 必须恰好 2 次），搬家时要**手工**跟着改读的文件；
- 字符串拼接后比对的、以及上面第 3 条跳过的作用域受限断言。
所以本工具报「0 个问题」**不等于** CI 一定绿，只等于「这一类最常见的搬家失配没有」。
退出码：有问题 1，干净 0（可接进 ci-gates.sh，但目前只在本地用）。
"""
import io
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else 'app/src/test/java'
MAIN_PREFIXES = ('app/src/main/java/', 'src/main/java/')


def readsrc(rel):
    """按测试里同样的两种工作目录假设去读主代码文件。"""
    for pre in MAIN_PREFIXES:
        if os.path.exists(pre + rel):
            return io.open(pre + rel, encoding='utf-8').read()
    return None


def unescape(s):
    return (s.replace('\\"', '"').replace('\\n', '\n').replace('\\t', '\t')
             .replace('\\$', '$').replace('\\\\', '\\'))


def whole_file_vars(text):
    """变量名 → 它持有的那份源文件相对路径（只收「整份文件」的，截过片段的收不进来）。"""
    out = {}
    for m in re.finditer(r'val\s+(\w+)\s*=\s*read\("([^"]+\.kt)"\)', text):
        out[m.group(1)] = m.group(2)
    for _ in range(3):                      # 传递：val src = repo!! / val s = src ?: return
        for m in re.finditer(r'val\s+(\w+)\s*=\s*(\w+)\s*(?:!!|\?:|[.]|\s*$)', text, re.M):
            if m.group(2) in out and m.group(1) not in out:
                out[m.group(1)] = out[m.group(2)]
    return out


def is_negated(head):
    """这个 contains(...) 处在反向断言里吗。"""
    if re.search(r'!\s*(?:\w+\s*\.\s*)*$', head):     # `!src.` / `!body.`（`repo!!.` 不算：!! 后没有 ! 收尾）
        return True
    last = None
    for m in re.finditer(r'assert(True|False)\s*\(', head):
        last = m
    return bool(last and last.group(1) == 'False')


def check_chunk(chunk, wf, problems, skipped, fn):
    """一个 @Test 块内的所有 contains(...) 断言。"""
    for m in re.finditer(r'contains\(\s*\n?\s*"((?:[^"\\]|\\.)+)"', chunk):
        lit, head = unescape(m.group(1)), chunk[:m.start()]
        recv = re.search(r'(\w+)\s*(?:!!|\?)?\s*\.\s*$', head)
        inline = re.search(r'read\("([^"]+\.kt)"\)\s*(?:!!|\?)?\s*\.\s*$', head)
        target = inline.group(1) if inline else (wf.get(recv.group(1)) if recv else None)
        if target is None:
            skipped.append('%s: 作用域受限的断言（%s.contains(%r…)）'
                           % (fn, (recv.group(1) if recv else '?'), lit[:40]))
            continue
        src = readsrc(target)
        hit = bool(src and lit in src)
        neg = is_negated(head)
        if not neg and not hit:
            problems.append('正向断言的字面串在 %s 里找不到: %r' % (target.split('/')[-1], lit[:70]))
        if neg and hit:
            problems.append('反向断言的字面串在 %s 里**出现了**（守卫已腐烂）: %r'
                            % (target.split('/')[-1], lit[:70]))


def main():
    bad_files, skipped, scanned = 0, [], 0
    for dp, _, fns in os.walk(ROOT):
        for fn in sorted(fns):
            if not fn.endswith('.kt'):
                continue
            p = os.path.join(dp, fn)
            t = io.open(p, encoding='utf-8').read()
            if 'read("' not in t:
                continue
            scanned += 1
            problems = []
            for chunk in re.split(r'(?=^\s*@Test\b)', t, flags=re.M):
                wf = whole_file_vars(chunk)
                for x in set(wf.values()):
                    if readsrc(x) is None:
                        problems.append('读的源文件不存在: %s' % x)
                check_chunk(chunk, wf, problems, skipped, fn)
            if problems:
                bad_files += 1
                print('── %s' % p.replace(ROOT + '/', ''))
                for q in problems:
                    print('   ✗ %s' % q)
    print('\n源码文本守卫镜像：%d 个测试文件读主代码；有问题 %d 个；作用域受限跳过 %d 处（由 CI 覆盖）'
          % (scanned, bad_files, len(skipped)))
    for s in sorted(set(skipped)):
        print('   · 跳过 %s' % s)
    return 1 if bad_files else 0


if __name__ == '__main__':
    sys.exit(main())
