#!/usr/bin/env python3
"""搬运后的 import 完整性核对（**搬家专用**，需要「搬家前那个文件」当参照物）。

为什么不是 `kt-lexcheck.py` 的一条判据
--------------------------------------
09-18 #10a-2 修红时我先把它做成了 kt-lexcheck 的「判据 5：用了别处 import 过的名字却没 import」，
语料库扫全仓。全仓一跑 **56/100 份报警、143 条**，全是三类**词法上不可判定**的撞名：

1. `list.map { }` / `flow.map { }`（stdlib 成员）vs 语料库里的 `kotlinx.coroutines.flow.map` —— 26 条；
2. `viewModel.changeQuantity(…)`（类成员）vs `com.agon.app.data.changeQuantity`（#5c 拆出来的同包扩展）；
   还包括 main/test **同包不同目录**（`app/src/test/…/data/` 用 `app/src/main/…/data/` 的 `FoodItem`）
   ⇒ 按目录近似「同包声明」在测试侧直接失效；
3. `AppRoute.Main.Home`（sealed 成员）vs `Icons.Rounded.Home`（图标 import）。

一份 56% 文件都报警的守卫等于没有守卫（还会把真警埋掉）⇒ 撤回。
**有参照物就不一样了**：搬走的代码全部来自那个旧文件，它当时能编译 ⇒ 它用到的每个名字都在它的
import 表里（或是同包/stdlib/局部）⇒ 「旧文件的 import 表 ∩ 新文件的用法」是**完备且低噪**的判据。

两次端到端对照（都命中当时的真实 CI 报错，复现命令见文件末）
--------------------------------------------------------------
- **#10a-1**（`c20458a` 的三个弹窗文件，别名 import 漏带）：点名 1 / 4 / 4 条，与修复提交 `2035ab2`
  的 `+1 / +4 / +4` 逐条对上，覆盖 CI 报的 24 处 `Unresolved reference`。
- **#10a-2**（`befff9f` 的 5 个文件）：点名 41 条（入口 1 + MD3 body 9 + 备份节 12 + Miuix body 7 +
  小组件 12），与修复提交的 `+41 / −2` 对上；那 2 条是搬完后**变成死 import** 的
  （入口的 `layout.padding`、Miuix body 的 `lazy.items` —— 都只剩「形参名/具名实参」用法）。

判据（每条都对应踩过的坑）
--------------------------
1. **剥 import/package 行再算用量**：否则 `import …basic.Icon as MiuixIcon` 里的 `Icon`
   会自己证明自己"在用"（#10a-2 的自坑 #4，修红时在复核脚本里又踩了一次）。
2. **带 `as` 的只认别名**：简单名在该文件里不可用，而且会与别的同名符号撞
   （`…basic.Icon as MiuixIcon` 的 `Icon` vs `material3.Icon`）。
3. **大写/全大写名**：整词出现即算用量（`Icons.Rounded.Cloud`、`MiuixIcons.CloudFill`、
   `CLOUD_BACKUP_KEEP` 都是这个形状 —— 点号后面也要认，这正是 #10a-2 漏掉的一类）。
4. **小写名（扩展函数/属性）只认调用形**：`.name(` / `.name {` / `.name<` / `数字.name` / 裸 `name(` /
   裸 `name {` / 裸 `name<`。`state.items.size`、`items = listOf(…)` 这类属性访问与具名实参和同名扩展
   撞名，是主要假警源 ⇒ 放过。⚠️ 代价：只有属性形写法的小写扩展（从不带括号调用的）会漏。
5. **函数形参刻意不算本地名**：形参名与扩展撞名时（`fun f(padding: PaddingValues)` 里又调
   `Modifier.padding(…)`）算本地名会造成**漏报**，而漏报正是这次事故的方向 ⇒ 宁可多一眼假警。
   lambda 参（`{ padding -> }`）算本地名。

已知盲区：① 同包声明按**目录**近似（main/test 同包不同目录时失效，但那种情况下旧文件参照物照样兜住）；
② 旧文件自己若有死 import，会被判成"新文件也该有"⇒ 报出来的每一条都要看一眼用法；
③ 新写的代码（不在旧文件里）用到的新符号，这条判据看不见。

用法
----
    # 核对（OLD = 搬家前那个文件的 git 引用；NEW = 搬家后的所有文件，含被改写的入口）
    python3 tools/move-importcheck.py --old 2035ab2:app/src/main/java/com/agon/app/ui/screens/SettingsScreen.kt \\
        --new app/src/main/java/com/agon/app/ui/screens/Settings{Screen,BodyMd3,BackupMd3,BodyMiuix,Md3Widgets}.kt
    # 把缺的补进去（按路径 ASCII 排序 = 本仓 ktlint import-ordering 的既有形状）
    python3 tools/move-importcheck.py --old … --new … --apply
    # 自检（合成对照，不依赖仓库现状）
    python3 tools/move-importcheck.py --selftest

退出码：有缺失 = 1，否则 0（可以直接挂在搬运脚本后面当门禁）。

复现两次端到端对照
------------------
    # #10a-1（应点名 1 / 4 / 4 条别名）
    mkdir -p /tmp/pc1 && for f in SettingsBackupDialogs SettingsCloudDialogs SettingsSnapshotDialogs; do
      git show c20458a:app/src/main/java/com/agon/app/ui/screens/$f.kt > /tmp/pc1/$f.kt; done
    python3 tools/move-importcheck.py --old 'c20458a^:app/src/main/java/com/agon/app/ui/screens/SettingsScreen.kt' \\
        --new /tmp/pc1/*.kt
    # #10a-2（应点名 41 条）
    mkdir -p /tmp/pc2 && cp app/src/main/java/com/agon/app/ui/screens/*.kt /tmp/pc2/
    for f in SettingsScreen SettingsBodyMd3 SettingsBackupMd3 SettingsBodyMiuix SettingsMd3Widgets; do
      git show befff9f:app/src/main/java/com/agon/app/ui/screens/$f.kt > /tmp/pc2/$f.kt; done
    python3 tools/move-importcheck.py --old 2035ab2:app/src/main/java/com/agon/app/ui/screens/SettingsScreen.kt \\
        --new /tmp/pc2/Settings{Screen,BodyMd3,BackupMd3,BodyMiuix,Md3Widgets}.kt
"""
import importlib.util
import io
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location('ktlex', os.path.join(HERE, 'kt-lexcheck.py'))
ktlex = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ktlex)          # 借用它的词法器（会嵌套的块注释、字符串模板、MIME 通配符都处理过）

IMPORT_RE = re.compile(r'^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$', re.M)
DECL_RE = re.compile(
    r'^\s*(?:(?:public|private|internal|protected|final|open|abstract|sealed|data|value|annotation'
    r'|enum|inline|noinline|crossinline|actual|expect|lateinit|const|operator|infix|suspend|companion|override)\s+)*'
    r'(?:fun|class|object|interface|val|var|typealias)\s+(?:<[^>]*>\s*)?(?:[\w$]+\.)?([\w$]+)', re.M)
TOPDECL_RE = re.compile(
    r'^(?:public |internal |private |protected )*'
    r'(?:(?:fun|class|object|interface|val|var|typealias)|(?:data|enum|sealed|annotation) class)'
    r'\s+(?:<[^>]*>\s*)?(?:[\w$]+\.)?([\w$]+)', re.M)


def usable_imports(text):
    """{可用名: (路径, 别名 or None)}；带 `as` 的只认别名（判据 2）。"""
    out = {}
    for m in IMPORT_RE.finditer(text):
        path, alias = m.group(1), m.group(2)
        out[alias or path.rsplit('.', 1)[-1]] = (path, alias)
    return out


def code_of(text):
    """剥注释与字符串（用 kt-lexcheck 的词法器），再剥掉 import/package 行本身（判据 1）。"""
    code, ok, detail = ktlex.lex(text)
    assert ok, f'词法配平失败：{detail}'
    return re.sub(r'^\s*(?:import|package)\s.*$', '', code, flags=re.M)


def used(name, code):
    """这个名字在 code 里算不算"用到"（判据 3 / 4）。"""
    if name[:1].islower():
        return bool(re.search(r'\.\s*%s\s*[(<{]' % re.escape(name), code)
                    or re.search(r'\d\s*\.\s*%s(?![\w$])' % re.escape(name), code)
                    or re.search(r'(?<![\w$.])%s\s*[(<{]' % re.escape(name), code))
    return bool(re.search(r'(?<![\w$])%s\b' % re.escape(name), code))


def contexts(name, code, limit=3):
    out = []
    for line in code.split('\n'):
        if re.search(r'(?<![\w$])%s\b' % re.escape(name), line):
            out.append(' '.join(line.split())[:88])
            if len(out) >= limit:
                break
    return out


def local_decls(code):
    """本文件的声明名（顶层 + 局部 val/var + lambda 参；**不含函数形参**，见判据 5）。"""
    d = set(DECL_RE.findall(code))
    d |= set(re.findall(r'(?m)^\s*(?:val|var)\s+([\w$]+)', code))
    d |= set(re.findall(r'\{[^{}]*?([\w$]+)\s*->', code))
    d |= set(re.findall(r'\bfor\s*\(([\w$]+)', code))
    d |= set(re.findall(r'\bcatch\s*\(([\w$]+)', code))
    return d


_PKG = {}


def same_package_decls(paths):
    """参照文件与新文件所在目录里所有 `.kt` 的顶层声明名（≈ 同包，不需要 import）。"""
    key = tuple(sorted(os.path.dirname(p) or '.' for p in paths))
    if key not in _PKG:
        names = set()
        for d in key:
            try:
                entries = sorted(os.listdir(d))
            except OSError:
                continue
            for f in entries:
                if not f.endswith('.kt'):
                    continue
                try:
                    names |= set(TOPDECL_RE.findall(io.open(os.path.join(d, f), encoding='utf-8').read()))
                except (OSError, UnicodeDecodeError):
                    continue
        _PKG[key] = names
    return _PKG[key]


def read_old(ref):
    if os.path.exists(ref):
        return io.open(ref, encoding='utf-8').read()
    r = subprocess.run(['git', 'show', ref], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(f'✗ 读不到参照文件 {ref}：{r.stderr.strip()[:120]}')
    return r.stdout


def analyse(old_ref, new_paths, verbose=True):
    old_imports = usable_imports(read_old(old_ref))
    pkg = same_package_decls(list(new_paths))
    if verbose:
        print(f'参照文件 {old_ref}：{len(old_imports)} 个可用名；同包顶层声明 {len(pkg)} 个\n')
    plan, total_missing = {}, 0
    for p in new_paths:
        text = io.open(p, encoding='utf-8').read()
        code = code_of(text)
        mine = usable_imports(text)
        decl = local_decls(code)
        needed, skipped = {}, []
        for name, (path, alias) in old_imports.items():
            if name in decl or name in pkg:
                continue
            if not re.search(r'(?<![\w$])%s\b' % re.escape(name), code):
                continue                      # 整词都没出现
            if not used(name, code):
                skipped.append(name)          # 只有属性形/具名实参形用法 ⇒ 不需要 import（判据 4）
                continue
            needed[name] = (path, alias)
        missing = sorted(set(needed) - set(mine))
        extra = sorted(set(mine) - set(needed))
        plan[p] = (text, mine, needed, missing, extra, skipped)
        total_missing += len(missing)
        if verbose:
            tag = '✗' if missing else '✓'
            print(f'{tag} {p}: 现有 {len(mine)} 条 · 参照物推出来应有 {len(needed)} 条'
                  f' · 缺 {len(missing)} · 疑似多余 {len(extra)} · 属性形跳过 {len(skipped)}')
            for n in missing:
                path, alias = needed[n]
                print(f'      ✗ 缺 {n:<22} ← {path}' + (f' as {alias}' if alias else ''))
                for c in contexts(n, code, 2):
                    print(f'           用法: {c}')
            for n in extra:
                print(f'      ? 疑似多余 {n:<16} ← {mine[n][0]}（旧文件 import 过、这里找不到调用形用法；'
                      f'⚠️ 也可能是形参/局部撞名造成的假警，删之前先看用法）')
            if skipped:
                print(f'      · 属性形跳过: {" ".join(sorted(skipped))}')
    return plan, total_missing


def apply(plan):
    for p, (text, mine, needed, missing, extra, _) in plan.items():
        if not missing:
            continue
        lines = text.split('\n')
        idx = [i for i, l in enumerate(lines) if l.startswith('import ')]
        assert idx == list(range(idx[0], idx[0] + len(idx))), f'{p}: import 块不连续，不敢改'
        merged = dict(mine)
        merged.update({n: needed[n] for n in missing})
        block = ['import ' + path + (f' as {alias}' if alias else '')
                 for path, alias in sorted(merged.values(), key=lambda pa: pa[0])]
        lines[idx[0]:idx[-1] + 1] = block
        out = '\n'.join(lines)
        assert out.count('\n') == text.count('\n') - len(idx) + len(block)
        assert out.endswith('\n') and not out.endswith('\n\n')
        io.open(p, 'w', encoding='utf-8').write(out)
        print(f'✓ {p}: 补进 {len(missing)} 条 import（{len(idx)} → {len(block)} 条）')


# ------------------------------------------------------------------ 自检
OLD_FIXTURE = '''package old

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agon.app.data.CLOUD_BACKUP_KEEP
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text as MiuixText

fun old() = Unit
'''

# (名字, 新文件内容, 期望点名的缺失 import)
CONTROLS = [
    # ① 四类真形状各一条：数字后的小写扩展属性、点号后的大写成员、全大写常量、别名
    ('all_four_shapes', '''package new

import androidx.compose.material.icons.Icons
import androidx.compose.ui.Modifier

fun a() {
    val m = Modifier.padding(20.dp)
    val i = Icons.Rounded.Cloud
    val k = CLOUD_BACKUP_KEEP
    MiuixText("标题")
}
''', ['padding', 'dp', 'Cloud', 'CLOUD_BACKUP_KEEP', 'MiuixText']),
    # ② 小写扩展的调用形：`.launch {`（尾随 lambda，没有括号）与裸调用 `items(…)`
    ('trailing_lambda_and_bare_call', '''package new

fun a(scope: S, list: L) {
    scope.launch { }
    items(list) { }
}
''', ['launch', 'items']),
    # ③ 只有属性形/具名实参形用法 ⇒ 一条都不该报（这条是判据 4 的反面，防的是假警）
    ('property_form_only', '''package new

fun a(state: S, n: Int): Int {
    val list = state.items
    return list.size + n
}
''', []),
    # ④ 全都 import 齐了 ⇒ 干净（证明本工具不会对正确的文件乱叫）
    ('complete', '''package new

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agon.app.data.CLOUD_BACKUP_KEEP
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text as MiuixText

fun a(scope: S) {
    val m = Modifier.padding(20.dp)
    val i = Icons.Rounded.Cloud
    val k = CLOUD_BACKUP_KEEP
    MiuixText("标题")
    scope.launch { }
    items(emptyList<Int>()) { }
}
''', []),
]


def selftest():
    tmp = tempfile.mkdtemp(prefix='move-importcheck-')
    old = os.path.join(tmp, 'Old.kt')
    io.open(old, 'w', encoding='utf-8').write(OLD_FIXTURE)
    bad = 0
    for name, text, expect in CONTROLS:
        f = os.path.join(tmp, name + '.kt')
        io.open(f, 'w', encoding='utf-8').write(text)
        plan, _ = analyse(old, [f], verbose=False)
        got = plan[f][3]
        ok = sorted(got) == sorted(expect)
        print('  %s %-30s 期望 %s · 实得 %s' % ('✓' if ok else '✗', name,
                                                sorted(expect) or '干净', sorted(got) or '干净'))
        if not ok:
            bad += 1
    print('自检：%d 个对照，失败 %d 个' % (len(CONTROLS), bad))
    return 1 if bad else 0


def main():
    args = sys.argv[1:]
    if '--selftest' in args:
        sys.exit(selftest())
    if '--old' not in args or '--new' not in args:
        sys.exit(__doc__.split('用法\n----\n')[1].split('退出码')[0].strip()
                 + '\n\n（或跑 --selftest）')
    old_ref = args[args.index('--old') + 1]
    rest = args[args.index('--new') + 1:]
    new_paths = [a for a in rest if not a.startswith('-')]
    assert new_paths, '--new 后面没有文件'
    plan, missing = analyse(old_ref, new_paths)
    if '--apply' in args:
        print()
        apply(plan)
    print(f'\n合计缺失 {missing} 条' + ('（--apply 已补）' if '--apply' in args and missing else ''))
    sys.exit(1 if missing else 0)


if __name__ == '__main__':
    main()
