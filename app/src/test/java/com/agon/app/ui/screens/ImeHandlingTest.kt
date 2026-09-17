package com.agon.app.ui.screens

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
 * 却两个属性都没写），键盘会盖住「确定移动」按钮。若该弹窗按路线图搬去 `AppDialogs.kt`，清单同步改。
 *
 * 若确实有屏幕不需要（例如页面内没有输入框、或弹窗走 Miuix 实现），
 * 请在下方列表里改，并说明理由——不要为了过测试而加无意义的 `imePadding()`。
 */
class ImeHandlingTest {

    /** Gradle 的测试工作目录是模块目录（app/），IDE 也可能用仓库根目录，两处都找一下。 */
    private fun read(relativePath: String): String? =
        listOf("src/main/java/", "app/src/main/java/")
            .map { File(it + relativePath) }
            .firstOrNull { it.exists() }
            ?.readText()

    /**
     * App 外壳（浮层 + 底栏）分散在哪几个文件里 —— 第 2 条守卫按这份清单**跨文件求和**。
     *
     * 2026-09-16 起 `MainActivity.kt` 按职责拆分，守卫从「读一个文件点数」改成「读一组文件求和」；
     * 搬动浮层时必须同步改这份清单。清单里不存在的文件会被跳过（拆分分步做，中间态只有部分文件在）。
     */
    private val chromeFiles = listOf(
        "com/agon/app/MainActivity.kt",   // 拆分前：Snackbar + 批量栏 + 弹窗 + 底栏全在这里；拆分后只剩 Activity 本体
        "com/agon/app/MainApp.kt",        // Snackbar 覆盖层
        "com/agon/app/BatchBars.kt",      // 悬浮 / 常驻两条批量操作栏
        "com/agon/app/AppDialogs.kt",     // 批量「移动存放位置」弹窗（MD3 分支）
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
     * （星号 + 斜杠 + 星号那种写法，`SettingsScreen.kt:379` 的 `arrayOf(...)` 第三个元素就是）时，
     * 其中的「斜杠星号」会被当成本函数的块注释开头，与后文第一个「星号斜杠」（往往是几百行外某段 KDoc 的结尾）
     * 配对，把中间的真实代码整段吞掉 —— 实测吞掉 379–976 行约 600 行，坚果云弹窗那处键盘避让正在其中。
     * 后果分两面：计数偏低会**假失败**（响，能发现），而 `assertFalse` 类断言会变**空转**（不响，危险）。
     * 目前只有 `SettingsScreen.kt` 命中，且它不在第 2 条的 chromeFiles / navBarFiles 清单里，
     * 故现有断言都还成立；第 3 条新增的那半因此改为匹配赋值形态、绕开本函数。
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
        val contents = imeScreens.associateWith(::read)
        assumeTrue("找不到屏幕源码（非 Gradle 工作目录？），跳过", contents.values.any { it != null })

        val missing = contents.filterValues { it?.contains("imePadding()") != true }.keys
        assertTrue(
            "以下屏幕缺少 Modifier.imePadding()，键盘会盖住底部内容：$missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `App 级浮层必须同时避让导航栏与键盘`() {
        val chrome = chromeFiles.mapNotNull(::read)
        assumeTrue("找不到任何 App 外壳源码（非 Gradle 工作目录？），跳过", chrome.isNotEmpty())
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
        val navBars = navBarFiles.mapNotNull(::read)
        assertTrue(
            "找不到底栏实现 $navBarFiles —— 又被搬走了？请同步更新 navBarFiles",
            navBars.isNotEmpty(),
        )
        assertFalse(
            "底栏不应跟随键盘抬升（A 方案）：底栏实现里出现了 .imePadding()",
            navBars.any { it.codeOnly().contains(".imePadding()") },
        )
    }

    @Test
    fun `带输入框的 MD3 弹窗必须关闭 decorFitsSystemWindows`() {
        val files = listOf(
            "com/agon/app/ui/screens/SettingsScreen.kt",        // 坚果云账号 / 应用密码
            // 分类名称 + Emoji、添加存放位置：2026-09-16 第 6 对把这两个弹窗搬进了组件层，条目跟着搬
            "com/agon/app/ui/components/app/AppFormDialog.kt",
            // 批量「移动存放位置」弹窗：2026-09-16 补入清单时它在 MainActivity.kt，同日拆分后落在 AppDialogs.kt。
            // 弹窗再搬家就改这一行 —— 清单外的文件不会被检查，这正是它当初漏网的原因。
            "com/agon/app/AppDialogs.kt",
        )
        val contents = files.mapNotNull { f -> read(f)?.let { f to it } }.toMap()
        assumeTrue("找不到设置/管理页源码（非 Gradle 工作目录？），跳过", contents.isNotEmpty())

        // 清单里不存在的文件跳过（弹窗按路线图分步搬家，中间态只有一个宿主文件在）
        val missing = contents.filterValues { !it.contains("decorFitsSystemWindows = false") }.keys
        assertTrue(
            "以下文件的 MD3 弹窗没有关闭 decorFitsSystemWindows，IME inset 传不进来、底部按钮会被键盘盖住：$missing",
            missing.isEmpty(),
        )

        // 关掉 decorFits 只是「拿得到 inset」，还得真的避让。两种写法都认。
        //
        // 这里**刻意不用 codeOnly()**，而是匹配完整的赋值形态：`SettingsScreen.kt:379` 有
        // `arrayOf("application/json", "text/plain", "*/*")`，MIME 通配符里的 `/*` 会被 codeOnly()
        // 的块注释正则当成注释开头，一路吞到 976 行 KDoc 的 `*/` 为止 —— 600 行真实代码（含坚果云
        // 弹窗那处避让）在「只剩代码」的视图里根本不存在，断言就会假失败。赋值形态不会出现在散文里，
        // 所以直接匹配原文既精确又不依赖注释剥离（该坑已记在 codeOnly() 的 KDoc 与 devlog 2026-09-17）。
        val noAvoidance = contents.filterValues { code ->
            !code.contains("modifier = stickyImePadding()") && !code.contains("modifier = Modifier.imePadding()")
        }.keys
        assertTrue(
            "以下文件的 MD3 弹窗拿到了 IME inset 却没做避让（.imePadding() 与 stickyImePadding() 都没有），" +
                "底部按钮仍会被键盘盖住：$noAvoidance",
            noAvoidance.isEmpty(),
        )
    }
}
