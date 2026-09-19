package com.agon.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * IME（软键盘）避让的静态守卫。
 *
 * 背景（2026-09-15 修复）：manifest 里的 `adjustResize` 在 targetSdk 35+ 的 edge-to-edge 下
 * 已不再缩窗口——键盘只是「叠」在窗口上，必须由 Compose 自己消费 `WindowInsets.ime`。
 * 漏掉的后果是底部的保存按钮、列表末尾、「撤销」提示条被键盘盖住且点不到，
 * 而这类问题没有任何编译期报错，只能靠静态检查兜住：
 *
 * 1. 含输入框的屏幕：`Scaffold(modifier = Modifier.imePadding())`（整屏缩到键盘之上）；
 * 2. App 级浮层（不在任何 Scaffold 内）：`.navigationBarsPadding().imePadding()`
 *    （两段式等价于旧的 `navigationBarsWithImePadding()` = max(导航栏, 键盘)，不会叠加）；
 * 3. MD3 弹窗是独立浮动窗口，必须 `DialogProperties(decorFitsSystemWindows = false)`
 *    才会把 IME inset 透给内容，**且拿到 inset 之后必须真的避让**（`imePadding()` 或
 *    `stickyImePadding()`，两者都认，见下）；Miuix 的 `WindowDialog` 由库内部处理
 *    （见 `MiuixDialog` 的 KDoc），不在守卫范围内。
 *
 * **2026-09-17 起第 2、3 条都认两种写法**：带输入框的 MD3 弹窗从 `Modifier.imePadding()` 换成了
 * `stickyImePadding()`（`ui/components/app/AppIme.kt`）—— 焦点在弹窗内两个输入框之间切换时输入法会重启、
 * IME inset 瞬时归零，居中弹窗跟着上下坠一下（用户真机报告），粘性避让让它一动不动。
 * 该助手内部自己读 `WindowInsets.ime` 算 padding，源码里不再出现 `.imePadding()` 字样，
 * 所以**按字样点数的第 2 条必须把两种写法都算上**，否则一次正确的改动会让计数从 4 掉到 3 而误报
 * （与 2026-09-16 把第 2 条从「比大小」改成「直接查底栏」是同一条教训：代理指标要跟被约束物一一对应）。
 *
 * 第 3 条的清单是**按文件点名**的，所以「带输入框的 MD3 弹窗」新出现在哪个文件，就必须把那个文件加进来：
 * 2026-09-16 就是这样漏掉了 `MainActivity.kt` 的批量「移动存放位置」弹窗（有 `OutlinedTextField`，
 * 却两个属性都没写），键盘会盖住「确定移动」按钮。该弹窗同日拆分后落在 `AppDialogs.kt`，
 * 2026-09-17 又搬去 `ui/components/app/AppBatchMoveDialog.kt`（两处清单同步改；漏改会直接红，见下）。
 *
 * **2026-09-17 起清单里的文件不存在 = 直接红**（见 [assertAllListedFilesExist]）。此前 `read()` 对不存在的
 * 文件返回 null、各条断言再用 `mapNotNull` 悄悄丢掉，于是「文件搬走了而清单没跟着改」的后果是守卫**静默少覆盖**
 * —— 第 3 条尤其危险：文件少了，`missing` / `noAvoidance` 在更小的集合上算，断言照常全绿。这与本仓 detekt 曾经
 * 「`--config` 没配 `--build-upon-default-config`、报告恒为 0 条」是同一类失效（那次之后 `tools/ci-gates.sh`
 * 加了 `detekt_selftest`：每次门禁先验「规则确实在跑」，命中 0 条直接判红）。所以本文件也补两道网：
 * 清单存在性断言 + [canary 点数与谓词逻辑本身没坏]。
 *
 * 若确实有屏幕不需要（例如页面内没有输入框、或弹窗走 Miuix 实现），
 * 请在下方列表里改，并说明理由——不要为了过测试而加无意义的 `imePadding()`。
 */
class ImeHandlingTest {

    /** Gradle 的测试工作目录是模块目录（app/），IDE 也可能用仓库根目录，两处都找一下。 */
    private val sourcePrefixes = listOf("src/main/java/", "app/src/main/java/")

    private fun read(relativePath: String): String? =
        sourcePrefixes
            .map { File(it + relativePath) }
            .firstOrNull { it.exists() }
            ?.readText()

    private fun fileExists(relativePath: String): Boolean = sourcePrefixes.any { File(it + relativePath).exists() }

    /**
     * 清单里的文件**必须存在**，缺一个就红。
     *
     * 为什么需要这条：[read] 对不存在的文件返回 null，三条守卫都用 `mapNotNull` / `associateWith` 接住，
     * 于是「文件搬走了而清单没同步」不会报错，只会让守卫**静默少覆盖**那个文件。第 1 条会把 null 算进
     * `missing`（响）、第 2 条会让 `imeCount` 从 4 掉到 3（响），但**第 3 条完全静默**：集合变小，
     * `missing` / `noAvoidance` 仍是空，断言全绿。
     *
     * 唯一放行的是「整棵源码树都找不到」（工作目录既不是 `app/` 也不是仓库根）—— 那是环境问题不是代码漂移，
     * 用 `assumeTrue` 跳过；只要清单里**任一**文件在，就说明环境是对的，缺的那个就是真漂移。
     */
    private fun assertAllListedFilesExist(paths: List<String>, what: String) {
        assumeTrue("找不到源码树（工作目录既不是 app/ 也不是仓库根？），跳过", paths.any(::fileExists))
        val gone = paths.filterNot(::fileExists)
        assertTrue(
            "$what 清单里有文件不存在：$gone —— 文件搬走了而清单没同步改？" +
                "请同步更新清单：漏改的后果是守卫静默少覆盖这些文件（第 3 条甚至照常全绿）。",
            gone.isEmpty(),
        )
    }

    /**
     * App 外壳（浮层 + 底栏）分散在哪几个文件里 —— 第 2 条守卫按这份清单**跨文件求和**。
     *
     * 2026-09-16 起 `MainActivity.kt` 按职责拆分，守卫从「读一个文件点数」改成「读一组文件求和」；
     * 搬动浮层时必须同步改这份清单 —— 2026-09-17 起清单里文件不存在会**直接红**
     * （见 [assertAllListedFilesExist]），不再像拆分中间态那样静默跳过。
     */
    private val chromeFiles = listOf(
        "com/agon/app/MainActivity.kt",   // 拆分前：Snackbar + 批量栏 + 弹窗 + 底栏全在这里；拆分后只剩 Activity 本体
        "com/agon/app/MainApp.kt",        // Snackbar 覆盖层
        "com/agon/app/BatchBars.kt",      // 悬浮 / 常驻两条批量操作栏
        // 批量「移动存放位置」弹窗（MD3 分支）：2026-09-17 由包根 AppDialogs.kt 搬进 App 级组件层
        "com/agon/app/ui/components/app/AppBatchMoveDialog.kt",
        "com/agon/app/NavChrome.kt",      // 4 套底栏：只有 navigationBarsPadding，**没有** imePadding
    )

    /** 其中「底栏实现」所在的文件：A 方案要求它们只避让导航栏、**不**避让键盘。 */
    private val navBarFiles = listOf("com/agon/app/NavChrome.kt")

    /**
     * 含输入框的屏幕清单。
     *
     * 2026-09-16 起双主题逐对合并（第三批 #3）：**合并后一个条目就覆盖两套主题**，
     * 因为 `imePadding()` 写在合并后那一份文件里。所以每合并一对，就把对应的 `Miuix*Screen.kt`
     * 条目删掉（文件已不存在，留着会让 `read()` 返回 null 而误报「缺少 imePadding」）。
     * 归档页、食品列表页已合并（各一份含两主题）；`MiuixArchiveScreen.kt` /
     * `MiuixFoodListScreen.kt` 条目随之删除（列表页那条原本就注着「合并后删掉这一条」）。
     *
     * 键盘避让仍由**屏幕自己**声明 `AppScaffold(modifier = Modifier.imePadding())`，
     * 组件层不无条件加 —— 没有输入框的屏幕不需要，且这样「哪一屏要避让」在屏幕文件里看得见。
     */
    private val imeScreens = listOf(
        "com/agon/app/ui/screens/EditFoodScreen.kt",        // 保存按钮在 bottomBar，最严重
        "com/agon/app/ui/screens/FoodListScreen.kt",        // 已合并双主题（2026-09-16 第 5 对）：搜索框 + 列表末尾
        "com/agon/app/ui/screens/ArchiveScreen.kt",         // 已合并双主题（2026-09-16 第 2 对）：搜索框
    )

    /**
     * 只留代码、去掉注释再点数。
     *
     * 文件头与 KDoc 里写「本文件不得出现 .imePadding()」这类**说明**是好事，但按原始文本点数会把它
     * 算成一处实现（拆分当天就踩到了：注释让计数从 4 变 5，还让底栏那条 assertFalse 直接误报）。
     *
     * ⚠️ **已知局限（2026-09-17 发现，尚未修）**：块注释正则不认字符串字面量。源码里出现 MIME 全通配符
     * （星号 + 斜杠 + 星号那种写法，`SettingsBackupDialogs.kt:106` 的 `arrayOf(...)` 第三个元素就是；
     * #10a-1 前它在 `SettingsScreen.kt:379`）时，
     * 其中的「斜杠星号」会被当成本函数的块注释开头，与后文第一个「星号斜杠」（往往是几百行外某段 KDoc 的结尾）
     * 配对，把中间的真实代码整段吞掉 —— 拆分前实测吞掉 379–976 行约 600 行，坚果云弹窗那处键盘避让正在其中。
     * 后果分两面：计数偏低会**假失败**（响，能发现），而 `assertFalse` 类断言会变**空转**（不响，危险）。
     * 目前只有 `SettingsBackupDialogs.kt` 命中，且它不在第 2 条的 chromeFiles / navBarFiles 清单里，
     * 故现有断言都还成立；第 3 条新增的那半因此改为匹配赋值形态、绕开本函数。
     * ℹ️ #10a-1 之后 MIME 字面量与那处键盘避让**已不在同一个文件**（避让在 `SettingsCloudDialogs.kt`），
     * 吞不到一起了；但第 3 条仍按赋值形态匹配、不依赖本函数 —— 万一将来又同文件也不会假失败。
     * 正解是让剥离也走词法状态机（`MiuixDialogContentTest` 里已有一份验证过的），登记在 devlog 待办。
     *
     * 本段刻意用中文描述那两个符号而不写出来：**Kotlin 的块注释可嵌套**，在 KDoc 里写「斜杠星号」会让本注释
     * 自己配不平、把后面整个文件吞掉（同一坑的反面已在 `docs/MIUIX_UPGRADE.md` §3 登记过）。
     */
    private fun String.codeOnly(): String =
        replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `含输入框的屏幕必须处理 IME inset`() {
        assertAllListedFilesExist(imeScreens, "含输入框的屏幕")
        val contents = imeScreens.associateWith(::read)

        val missing = contents.filterValues { it?.contains("imePadding()") != true }.keys
        assertTrue(
            "以下屏幕缺少 Modifier.imePadding()，键盘会盖住底部内容：$missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `App 级浮层必须同时避让导航栏与键盘`() {
        assertAllListedFilesExist(chromeFiles, "App 外壳（浮层 + 底栏）")
        val chrome = chromeFiles.mapNotNull(::read)
        val text = chrome.joinToString("\n").codeOnly()

        val imeCount = Regex("\\.imePadding\\(\\)").findAll(text).count() +
            Regex("stickyImePadding\\(").findAll(text).count()
        val navCount = Regex("\\.navigationBarsPadding\\(\\)").findAll(text).count()

        // 期望位点（新增/搬走浮层时同步改这里；若按路线图把 MainActivity.kt 拆成多个文件，
        // 本测试要改成「读一组文件求和」，否则计数会掉到 0 而误报）：
        //   键盘避让              ×4 = Snackbar 覆盖层 + 批量操作栏两条（悬浮/常驻）用 .imePadding()，
        //                            批量「移动存放位置」MD3 弹窗用 stickyImePadding()（2026-09-17 起）
        //   .navigationBarsPadding() ×4 = 上面前三处 + 悬浮胶囊导航（底栏只避让导航栏，不避让键盘）
        assertTrue(
            "App 级浮层应有 ≥4 处键盘避让（.imePadding() 或 stickyImePadding()：" +
                "Snackbar + 两条批量操作栏 + 移动位置弹窗），实际 $imeCount",
            imeCount >= 4,
        )
        assertTrue("原有 navigationBarsPadding() 不应被删（底栏仍在用），实际 $navCount", navCount >= 4)

        // A 方案（2026-09-15 产品决定）：底部导航栏不随键盘抬升。
        // 2026-09-16 之前这里用「imePadding 数 < navigationBarsPadding 数」间接表达，但那个代理指标会被
        // 任何一处新增浮层推翻（给 MD3 弹窗补键盘避让后两边都是 4，一次正确的修复反被判成违规）。
        // 现在直接对着「底栏实现所在的文件」断言，约束与被约束物一一对应。
        assertAllListedFilesExist(navBarFiles, "底栏实现")
        val navBars = navBarFiles.mapNotNull(::read)
        assertFalse(
            "底栏不应跟随键盘抬升（A 方案）：底栏实现里出现了 .imePadding()",
            navBars.any { it.codeOnly().contains(".imePadding()") },
        )
    }

    /**
     * 带输入框的 MD3 弹窗在哪些文件里 —— 第 3 条守卫按这份清单逐个点名。
     *
     * 清单外的文件不会被检查，这正是 2026-09-16 那个弹窗当初漏网的原因；反过来，清单里的文件搬走了
     * 而这里没同步，2026-09-17 起会**直接红**（见 [assertAllListedFilesExist]），不再静默少覆盖。
     */
    private val dialogFiles = listOf(
        // #10a-1（2026-09-18）：坚果云账号弹窗（账号 + 密码两个输入框）随弹窗区搬去同包新文件，
        // 条目跟着搬 —— 不搬就会红：SettingsScreen.kt 里已经没有 decorFitsSystemWindows 了。
        "com/agon/app/ui/screens/SettingsCloudDialogs.kt",
        // 分类名称 + Emoji、添加存放位置：2026-09-16 第 6 对把这两个弹窗搬进了组件层，条目跟着搬
        "com/agon/app/ui/components/app/AppFormDialog.kt",
        // 批量「移动存放位置」弹窗：2026-09-16 补入清单时它在 MainActivity.kt，同日拆分后落在 AppDialogs.kt，
        // 2026-09-17 搬去 ui/components/app/ 并改名 AppBatchMoveDialog.kt（本行与 chromeFiles 里那行同步改的）。
        "com/agon/app/ui/components/app/AppBatchMoveDialog.kt",
    )

    /** 第 3 条前半：MD3 弹窗是独立浮动窗口，不关这个开关 IME inset 根本传不进内容。 */
    private fun closesDecorFits(code: String): Boolean = code.contains("decorFitsSystemWindows = false")

    /**
     * 第 3 条后半：关掉 decorFits 只是「拿得到 inset」，还得**真的避让**。两种写法都认。
     *
     * 这里**刻意不用 codeOnly()**，而是匹配完整的赋值形态：`SettingsBackupDialogs.kt:106`（#10a-1 前是
     * `SettingsScreen.kt:379`）那个 MIME 数组里有一项
     * 是「星号斜杠星号」写法的全通配符，其中「斜杠星号」两字符会被 codeOnly() 的块注释正则当成注释起点，
     * 一路吞到 976 行 KDoc 的注释结尾为止 —— 拆分前那 600 行真实代码（含坚果云弹窗那处避让）在「只剩代码」的视图里
     * 根本不存在，断言就会假失败。赋值形态不会出现在散文里，所以直接匹配原文既精确又不依赖注释剥离
     * （该坑已记在 codeOnly() 的 KDoc 与 devlog 2026-09-17）。
     *
     * ⚠️ 本段也**刻意用中文描述那两个符号、不写出来**，理由与 codeOnly() 的 KDoc 同一条：Kotlin 块注释可嵌套，
     * 在 KDoc 里写出「斜杠星号」会让本注释自己配不平、把后面的真实代码整段吞掉。2026-09-17 把这段说明从
     * 行内注释搬进 KDoc 时**真的踩了一回**（搬进来时顺手保留了那个 MIME 数组字面量）；而且花括号配平检查
     * **抓不到**它 —— 词法状态到文件末尾又自己配平了，靠 canary 的 `@Test` 计数对不上（2 ≠ 4）才暴露。
     */
    private fun avoidsIme(code: String): Boolean =
        code.contains("modifier = stickyImePadding()") || code.contains("modifier = Modifier.imePadding()")

    @Test
    fun `带输入框的 MD3 弹窗必须关闭 decorFitsSystemWindows`() {
        assertAllListedFilesExist(dialogFiles, "带输入框的 MD3 弹窗")
        val contents = dialogFiles.mapNotNull { f -> read(f)?.let { f to it } }.toMap()

        val missing = contents.filterValues { !closesDecorFits(it) }.keys
        assertTrue(
            "以下文件的 MD3 弹窗没有关闭 decorFitsSystemWindows，IME inset 传不进来、底部按钮会被键盘盖住：$missing",
            missing.isEmpty(),
        )

        val noAvoidance = contents.filterValues { !avoidsIme(it) }.keys
        assertTrue(
            "以下文件的 MD3 弹窗拿到了 IME inset 却没做避让（.imePadding() 与 stickyImePadding() 都没有），" +
                "底部按钮仍会被键盘盖住：$noAvoidance",
            noAvoidance.isEmpty(),
        )
    }

    /**
     * canary：证明上面那些「点数 / 字符串匹配」的逻辑本身没坏。
     *
     * 这类静态守卫的失效方式不是报错，而是**静默**：计数正则少写一个转义、[codeOnly] 把真实代码当注释吞掉、
     * 或者判定串写得宽到什么都匹配 —— 断言就退化成永远为真的空转。本仓在 detekt 上吃过一次同样的亏
     * （配置没生效、报告恒为 0 条、门禁看着在跑其实没管），之后 `tools/ci-gates.sh` 才加了 `detekt_selftest`。
     * 所以这里对**合成的**好例/坏例各跑一遍，让「守卫还能区分对错」本身成为被测对象。
     */
    @Test
    fun `canary 点数与谓词逻辑本身没坏`() {
        // 好例：真实代码里的三种避让写法都必须被数到，各 1 次
        val code = "Box(Modifier.navigationBarsPadding().imePadding()) { Text(\"底栏\") }\n" +
            "Dialog { Column(modifier = stickyImePadding()) { OutlinedTextField(value = v) } }\n"
        assertEquals(1, Regex("\\.imePadding\\(\\)").findAll(code.codeOnly()).count())
        assertEquals(1, Regex("stickyImePadding\\(").findAll(code.codeOnly()).count())
        assertEquals(1, Regex("\\.navigationBarsPadding\\(\\)").findAll(code.codeOnly()).count())

        // 坏例：说明性注释里提到这些字样**不得**被算成一处实现
        //（2026-09-16 拆分当天就踩到：文件头注释让计数从 4 变 5，还让底栏那条 assertFalse 直接误报）
        val prose = "// 底栏不得出现 .imePadding()，也不得出现 stickyImePadding()\nval x = 1\n"
        assertEquals(0, Regex("\\.imePadding\\(\\)").findAll(prose.codeOnly()).count())
        assertEquals(0, Regex("stickyImePadding\\(").findAll(prose.codeOnly()).count())

        // 第 3 条的两个谓词：坏例必须判违规、好例必须放过（否则断言就是空转）
        val badDialog = "AlertDialog(properties = DialogProperties()) { OutlinedTextField(value = v) }"
        assertFalse(closesDecorFits(badDialog))
        assertFalse(avoidsIme(badDialog))
        val goodDialog = "AlertDialog(properties = DialogProperties(decorFitsSystemWindows = false)) {\n" +
            "    OutlinedTextField(value = v, onValueChange = {}, modifier = stickyImePadding())\n}"
        assertTrue(closesDecorFits(goodDialog))
        assertTrue(avoidsIme(goodDialog))
        // 另一种合规写法（MD3 的 imePadding）也要认，别只认粘性那种
        assertTrue(avoidsIme("OutlinedTextField(value = v, modifier = Modifier.imePadding())"))
    }
}
