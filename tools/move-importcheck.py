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
- **#10a-2**（`befff9f` 的 5 个文件）：**今天**跑同一条命令点名 **43** 条 = 首轮的 41（入口 `launch` 1 +
  MD3 body 9 + 备份节 12 + Miuix body 7 + 小组件 12）+ 次轮的 2（入口 `getValue` / `setValue`）——
  判据 6 加进来之后，**两轮真红被同一次运行一起复现**。首轮当时记的是 41 条，与修复提交的 `+41 / −2`
  对上；那 2 条删除是搬完后**变成死 import** 的（入口的 `layout.padding`、Miuix body 的 `lazy.items`
  —— 都只剩「形参名/具名实参」用法）。
- **#10a-2 第二轮**（`83a38fe` 之后，入口仍红）：点名 `getValue` / `setValue` 2 条 —— 与 CI 那轮
  **唯一**一处报错（`SettingsScreen.kt:138:23`，两行 `e:` 是同一处的 getValue/setValue 两侧）逐字对上。
  判据 6 就是为这一条加的；加完后重跑，5 个文件 0 缺失。

判据（每条都对应踩过的坑）
--------------------------
1. **剥 import/package 行再算用量**：否则 `import …basic.Icon as MiuixIcon` 里的 `Icon`
   会自己证明自己"在用"（#10a-2 的自坑 #4，修红时在复核脚本里又踩了一次）。
2. **带 `as` 的只认别名**：简单名在该文件里不可用，而且会与别的同名符号撞
   （`…basic.Icon as MiuixIcon` 的 `Icon` vs `material3.Icon`）。
3. **大写/全大写名**：整词出现即算用量（`Icons.Rounded.Cloud`、`MiuixIcons.CloudFill`、
   `CLOUD_BACKUP_KEEP` 都是这个形状 —— 点号后面也要认，这正是 #10a-2 漏掉的一类）。
4. **小写名（扩展函数/属性）只认这四种形状**：`.name(` / `.name {` / `.name<`（点号后调用）、
   `数字.name`（`20.dp`）、裸 `name(` / `name {` / `name<`（直接调用）、裸 `name.`（**扩展属性当接收者用**，
   如 `viewModelScope.launch { }`）。最后这条是 #10b-1 开工前补的：漏了它，搬 `AppViewModel` 的函数就会丢
   `import androidx.lifecycle.viewModelScope`（去注释实测：VM 代码里 `viewModelScope` 共 **58** 处、
   其中 `viewModelScope.launch` **38** 处，全是这个形状；领域 1 那 9 个块里占 4 处），
   而本地四项检查照样全绿 ⇒ 又是只有 CI 能抓的那类。
   `state.items.size`、`items = listOf(…)` 这类**点号后的属性访问与具名实参**和同名扩展撞名，
   是主要假警源 ⇒ 放过。⚠️ 代价：点号后面既不带括号、也不当接收者用的小写扩展属性仍会漏。
5. **函数形参刻意不算本地名**：形参名与扩展撞名时（`fun f(padding: PaddingValues)` 里又调
   `Modifier.padding(…)`）算本地名会造成**漏报**，而漏报正是这次事故的方向 ⇒ 宁可多一眼假警。
   lambda 参（`{ padding -> }`）算本地名。
6. **委托算子（`getValue` / `setValue` / `provideDelegate`）走结构判据**：这类 import 的名字在代码里
   **从不以标识符出现** —— `var x by remember { mutableStateOf(…) }` 一个字母都没提 `setValue`，
   所以判据 3（整词）和判据 4（调用形）都看不见它，"整词都没出现 ⇒ 跳过"那道闸直接把它放走了。
   #10a-2 第二轮 CI 红就是这一条：`e: SettingsScreen.kt:138:23 Type 'MutableState<PendingImport?>'
   has no method 'setValue(…)', so it cannot serve as a delegate`。
   认法：代码里有属性委托 `val|var X by …`（排除 `by lazy` / `by Delegates.`，那两个不需要 runtime 的
   算子）⇒ 需要 `getValue`；其中出现 `var` ⇒ 另需 `setValue`。照旧受参照物约束：参照文件没 import 过
   就不报（避免对非 Compose 代码乱叫）。
   ⚠️ 其余算子约定名（`componentN` 解构 / `iterator` / `invoke` / `compareTo` …）**不覆盖**：本仓实测
   `git grep` 全部 `*.kt`，这类 import 只有 `runtime.getValue` 21 处、`runtime.setValue` 12 处，其余 0 处。
7. **带接收者的声明不压制同名 import**：`internal suspend fun AppViewModel.buildCsvExport()` 这条声明自己
   就叫 `buildCsvExport`，但它体内 `repo.buildCsvExport()` 要的是 `data/` 里那个**同名扩展**
   （`data/FoodBackup.kt:67`：`internal suspend fun FoodRepository.buildCsvExport()`）。把声明名当"本地名"
   去压制 import，就会一条都算不出来 ⇒ 落盘必红，而本地四项检查照样全绿。#10b-1 搬 `AppViewModel` 的备份
   领域时实测撞上 **4** 条：`buildBackupJson` / `buildCsvExport` / `previewBackup` / `importBackupJson`
   （VM 第 32–35 行都 import 着，新文件原先只算出 7 条 import、这 4 条全缺）。
   认法：声明名前面带接收者（`fun Receiver.name` / `val|var Receiver.name`）时，**不**算本地名。
   ⚠️ **同包声明那条路也一样**（2026-09-19 #10b-3 补的第 3 处）：`same_package_decls()` 原先把目录里
   所有顶层声明一律当「同包名 ⇒ 不用 import」，于是**同包另一个领域文件**里的
   `internal fun AppViewModel.restoreArchived` 会把 `com.agon.app.data.restoreArchived` 压掉。
   当场做了阳性/阴性对照复现：同目录放一个声明了同名接收者扩展的兄弟文件 ⇒ 目标文件明明缺那条 import，
   工具报「缺 0」；把兄弟文件移走 ⇒ 立刻报「缺 1」。这条若不修，**下一个领域必红**：#10b-4 要搬的
   `restoreArchivedSmart` 体内写的正是 `repo.restoreArchived(id)`，而 `restoreArchived` 那时已经被
   #10b-3 的 `AppViewModelArchiveUndo.kt` 声明成同包接收者扩展了。修法与 `local_decls` 同一条：
   带接收者的声明名从同包集合里减掉（自检对照 ⑪）。
   ⚠️ 代价：真被本地声明遮蔽的同名 import 会多报一条 —— 多一条不红 CI（本仓 `no-unused-imports` 没开、
   由 `kt-lexcheck` 的死 import 判据在本地兜），漏一条必红 ⇒ 取安全方向。

已知盲区：① 同包声明按**目录**近似（main/test 同包不同目录时失效，但那种情况下旧文件参照物照样兜住）；
   集合里已按判据 7 减掉「带接收者的声明名」，所以它只会**少压**、不会多压（安全方向）；
② 旧文件自己若有死 import，会被判成"新文件也该有"⇒ 报出来的每一条都要看一眼用法；
③ 新写的代码（不在旧文件里）用到的新符号，这条判据看不见；
④ 委托以外的算子约定名（判据 6 的 ⚠️）—— 本仓实测 0 处，所以是"已量过的空"，不是"没看过"；
⑤ 同名遮蔽只按词法判，判不准时一律取安全方向（判据 7 的 ⚠️）：宁可多报一条 import，也不漏一条。

用法
----
    # 核对（OLD = 搬家前那个文件的 git 引用；NEW = 搬家后的所有文件，含被改写的入口）
    python3 tools/move-importcheck.py --old 2035ab2:app/src/main/java/com/agon/app/ui/screens/SettingsScreen.kt \\
        --new app/src/main/java/com/agon/app/ui/screens/Settings{Screen,BodyMd3,BackupMd3,BodyMiuix,Md3Widgets}.kt
    # 把缺的补进去（按路径 ASCII 排序 = 本仓 import 块的**既有惯例**，不是门禁：
    # `.editorconfig` 里 `ktlint_standard = disabled`、只逐条开了 6 条，`import-ordering` 没开）
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
    # #10a-2（应点名 43 条 = 首轮 41 + 次轮 2）
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
# 判据 7：带接收者的声明（`fun Receiver.name` / `val|var Receiver.name`）**不算本地名** ——
# 它自己不遮蔽同名 import（体内 `repo.name()` 要的常常是别的包里那个同名扩展）。
RECEIVER_DECL_RE = re.compile(
    r'^\s*(?:(?:public|private|internal|protected|final|open|abstract|sealed|data|value|annotation'
    r'|enum|inline|noinline|crossinline|actual|expect|lateinit|const|operator|infix|suspend|companion|override)\s+)*'
    r'(?:fun|val|var)\s+(?:<[^>]*>\s*)?[\w$]+\.([\w$]+)', re.M)
TOPDECL_RE = re.compile(
    r'^(?:public |internal |private |protected )*'
    r'(?:(?:fun|class|object|interface|val|var|typealias)|(?:data|enum|sealed|annotation) class)'
    r'\s+(?:<[^>]*>\s*)?(?:[\w$]+\.)?([\w$]+)', re.M)
# 判据 6：属性委托 `val|var X by …`。排除 `by lazy` / `by Delegates.`（那两个用的是 kotlin 自带的算子，
# 不需要 runtime.getValue/setValue）。只认行首带修饰词也算，`class X : Y by z` 那种接口委托不会被误认。
DELEG_RE = re.compile(
    r'^[ \t]*(?:(?:private|internal|public|protected|override|lateinit|actual|expect|const)\s+)*'
    r'(val|var)\s+[\w$]+\s+by\s+(?!lazy\b|Delegates\b)', re.M)
OPERATOR_NAMES = ('getValue', 'setValue', 'provideDelegate')


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
                    or re.search(r'(?<![\w$.])%s\s*[(<{]' % re.escape(name), code)
                    or re.search(r'(?<![\w$.])%s\s*\.' % re.escape(name), code))
    return bool(re.search(r'(?<![\w$])%s\b' % re.escape(name), code))


def delegations(code):
    """属性委托的处数，以及其中 `var` 的处数（判据 6）。"""
    kinds = [m.group(1) for m in DELEG_RE.finditer(code)]
    return len(kinds), sum(1 for k in kinds if k == 'var')


def deleg_lines(code, limit=3):
    """报缺失时拿委托那一行当"用法"：算子名在代码里没有字面出现，contexts() 会是空的。"""
    return [' '.join(m.group(0).split())[:88] for m in list(DELEG_RE.finditer(code))[:limit]]


def operator_needed(name, n_deleg, n_var):
    """算子约定名要不要（判据 6）：有委托就要 getValue/provideDelegate，其中有 var 才要 setValue。"""
    if name == 'setValue':
        return n_var > 0
    return n_deleg > 0


def contexts(name, code, limit=3):
    out = []
    for line in code.split('\n'):
        if re.search(r'(?<![\w$])%s\b' % re.escape(name), line):
            out.append(' '.join(line.split())[:88])
            if len(out) >= limit:
                break
    return out


def local_decls(code):
    """本文件的声明名（顶层 + 局部 val/var + lambda 参；**不含函数形参**，见判据 5；
    **带接收者的声明名也不算**，见判据 7 —— 减法放在最后，免得被上面几条并回来）。"""
    d = set(DECL_RE.findall(code))
    d |= set(re.findall(r'(?m)^\s*(?:val|var)\s+([\w$]+)', code))
    d |= set(re.findall(r'\{[^{}]*?([\w$]+)\s*->', code))
    d |= set(re.findall(r'\bfor\s*\(([\w$]+)', code))
    d |= set(re.findall(r'\bcatch\s*\(([\w$]+)', code))
    d -= set(RECEIVER_DECL_RE.findall(code))
    return d


_PKG = {}


def same_package_decls(paths):
    """参照文件与新文件所在目录里所有 `.kt` 的顶层声明名（≈ 同包，不需要 import）。

    ⚠️ 判据 7 在这条路上**同样生效**（2026-09-19 #10b-3 补）：**带接收者**的顶层声明
    （`fun Receiver.name` / `val|var Receiver.name`）不算"同包名"。它自己不遮蔽别的包里的同名扩展 ——
    同包另一个文件体内写的 `repo.name()`，要的常常正是那条 `import`。减法放在最后（与 `local_decls` 同形）。
    """
    key = tuple(sorted(os.path.dirname(p) or '.' for p in paths))
    if key not in _PKG:
        names, recv = set(), set()
        for d in key:
            try:
                entries = sorted(os.listdir(d))
            except OSError:
                continue
            for f in entries:
                if not f.endswith('.kt'):
                    continue
                try:
                    text = io.open(os.path.join(d, f), encoding='utf-8').read()
                except (OSError, UnicodeDecodeError):
                    continue
                names |= set(TOPDECL_RE.findall(text))
                recv |= set(RECEIVER_DECL_RE.findall(text))
        _PKG[key] = names - recv
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
        n_deleg, n_var = delegations(code)
        for name, (path, alias) in old_imports.items():
            if name in decl or name in pkg:
                continue
            if name in OPERATOR_NAMES:        # 判据 6：这类名字在代码里根本不出现，只能按结构认
                if operator_needed(name, n_deleg, n_var):
                    needed[name] = (path, alias)
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
                shown = deleg_lines(code, 2) if n in OPERATOR_NAMES else contexts(n, code, 2)
                for c in shown:
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.agon.app.data.CLOUD_BACKUP_KEEP
import com.agon.app.data.buildCsvExport
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text as MiuixText

fun old() = Unit
'''

# (名字, 新文件内容, 期望点名的缺失 import[, 同目录还要放的兄弟文件 [(文件名, 内容), …]])
# 第 4 项是给对照 ⑪ 用的：判据 7 的「同包路径」必须有**另一个文件**才测得出来。
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
    # ⑤ var 属性委托：`by` 一个字母都不提 getValue/setValue，只有结构判据看得见（判据 6 的正面）
    ('delegation_var', '''package new

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun a() {
    var x by remember { mutableStateOf(0) }
    x = x + 1
}
''', ['getValue', 'setValue']),
    # ⑥ `by lazy` 用的是 kotlin 自带的算子 ⇒ 一条都不该报（判据 6 的反面，防假警）
    ('delegation_by_lazy', '''package new

val cached by lazy { 1 }

fun a(): Int = cached
''', []),
    # ⑦ 只读委托（val）⇒ 只该报 getValue，不该顺手把 setValue 也带上
    ('delegation_val_only', '''package new

fun a(state: MutableState<Int>): Int {
    val v by state
    return v
}
''', ['getValue']),
    # ⑧ 委托齐了 ⇒ 干净（证明判据 6 不会对正确的文件乱叫）
    ('delegation_complete', '''package new

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

fun a() {
    var x by remember { mutableStateOf(0) }
    x = x + 1
}
''', []),
    # ⑨ 小写扩展**属性当接收者用**（`viewModelScope.launch { }`）：判据 4 原来放过这个形状 ⇒ 会漏 import
    #    （#10b-1 开工前发现的；点号后的 `launch` 那条本来就能认，所以期望里两个名字都在）
    ('bare_property_chain', '''package new

fun a() {
    viewModelScope.launch { }
}
''', ['launch', 'viewModelScope']),
    # ⑩ 带接收者的声明 + 体内调同名扩展（判据 7 的正面）：声明名不许把 import 压掉
    #    （#10b-1 实测：漏了这条，新文件少 4 条 `com.agon.app.data.*`，落盘必红、本地全绿）
    ('receiver_decl_same_name', '''package new

internal suspend fun AppViewModel.buildCsvExport(): String = repo.buildCsvExport()
''', ['buildCsvExport']),
    # ⑪ 同包**另一个文件**里的带接收者声明，也不许把 import 压掉（判据 7 的同包路径 = #10b-3 实测的盲点）：
    #    Sibling.kt 声明 `AppViewModel.buildCsvExport`，目标文件体内要 `repo.buildCsvExport()` ⇒ 那条
    #    `com.agon.app.data.buildCsvExport` 必须报缺。修前这里报「缺 0」（同包集合把名字压掉了），
    #    而真实代价是下一轮 #10b-4 落盘必红（`restoreArchivedSmart` 要 `repo.restoreArchived(id)`）。
    ('sibling_receiver_decl_same_name', '''package new

internal suspend fun AppViewModel.buildCsvExportAll(): String = repo.buildCsvExport()
''', ['buildCsvExport'], [('Sibling.kt', '''package new

internal suspend fun AppViewModel.buildCsvExport(): String = repo.buildCsvExport()
''')]),
]


def selftest():
    tmp = tempfile.mkdtemp(prefix='move-importcheck-')
    old = os.path.join(tmp, 'Old.kt')
    io.open(old, 'w', encoding='utf-8').write(OLD_FIXTURE)
    bad = 0
    for c in CONTROLS:
        name, text, expect = c[0], c[1], c[2]
        siblings = c[3] if len(c) > 3 else []
        # 每个对照一个**独立子目录**：`_PKG` 按目录缓存「同包声明」，全塞进同一个目录的话，
        # 第一个对照之后缓存就定格了，后面写进去的文件（含对照自己的声明）根本不会被重扫 ——
        # 对照 ⑩ 当初就是这样**蒙对**的（2026-09-19 #10b-3 查同包那条路时才发现：它测的形状
        # 其实一直没被真正扫到）。分目录之后 ⑩ 才真的在测判据 7。
        d = os.path.join(tmp, name)
        os.makedirs(d, exist_ok=True)
        for fn, body in siblings:
            io.open(os.path.join(d, fn), 'w', encoding='utf-8').write(body)
        f = os.path.join(d, name + '.kt')
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
