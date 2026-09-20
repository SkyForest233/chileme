package com.agon.app.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 撤销条 / 提示条的**文案与落位**守卫（路线图 #4b，2026-09-18）。
 *
 * 为什么值得钉：#4a 把一次性事件改成 `Channel` 之后，「提示出现在哪、写的是什么字」就成了纯搬运的**唯一判据** ——
 * 重构验收要求行为不变，而这类改动既不编译失败也不崩，只会让用户看见**另一句话**或**另一个位置的条**。
 * 真机复测当然能看出来，但一轮要过 4 条提示 × 2 主题；先把文案与落位钉在 CI 上，真机那一轮只需确认
 * 「看起来对」，不必逐字比对。
 *
 * 工程约束（与 `CorruptGuardTest` / `ScreenParityTest` / `UiEventTest` 同一套路）：本仓没有 Robolectric
 * 也没有 kotlin-reflect，`SnackbarHostState` 更是纯 Android 类型 ⇒ 结构性约束只能**读源码文本**断言。
 *
 * ⚠️ 两个吃过亏的细节，改这个文件时别踩：
 * 1. **子串陷阱**：`SnackbarResult.ActionPerformed` 是 `MiuixSnackbarResult.ActionPerformed` 的子串，
 *    朴素计数会把 Miuix 那行也算进 MD3 的账（写这个文件时真的踩到了）⇒ 断言一律用**带接收者的整行片段**。
 * 2. **期望值必须是量出来的**：本文件每个数字都来自实测（`git grep -o | wc -l` 或等价脚本），
 *    不是「我觉得应该是几」。写死一个猜的数，守卫要么第一天就红，要么更糟 —— 第一天就绿但钉错了东西。
 */
class SnackbarCopyTest {

    // 相对 `src/main/java/` 的路径。分布断言用这些常量当键，免得同一份路径写两遍还对不上。
    private val mainApp = "com/agon/app/MainApp.kt"
    private val undoSnackbar = "com/agon/app/ui/components/UndoSnackbar.kt"
    private val appChrome = "com/agon/app/ui/components/app/AppChrome.kt"
    private val appSnackbar = "com/agon/app/ui/components/app/AppSnackbar.kt"
    private val homeScreen = "com/agon/app/ui/screens/HomeScreen.kt"
    private val consumptionLog = "com/agon/app/ui/screens/ConsumptionLogScreen.kt"
    private val archiveScreen = "com/agon/app/ui/screens/ArchiveScreen.kt"
    private val appViewModelStartup = "com/agon/app/viewmodel/AppViewModelStartup.kt"
    private val uiEvent = "com/agon/app/viewmodel/UiEvent.kt"

    /** 源码根：Gradle 跑测试时 CWD 是模块目录（`app/`），从仓库根跑时多一层前缀 —— 两种都认。 */
    private val sourceRoot: File? by lazy {
        listOf("src/main/java", "app/src/main/java").map { File(it) }.firstOrNull { it.exists() }
    }

    /**
     * 全仓 main 源码：相对路径 → 剥掉注释后的文本。
     *
     * 先取局部 `root`：`sourceRoot` 是 `by lazy` 委托属性，Kotlin **不做智能转换**，
     * 直接在 lambda 里写 `it.relativeTo(sourceRoot)` 会因为「要 File、给的是 File?」编译失败。
     */
    private val allMainSources: Map<String, String> by lazy {
        val root = sourceRoot ?: return@lazy emptyMap()
        root.walkTopDown()
            .filter { it.extension == "kt" }
            .associate { it.relativeTo(root).path.replace('\\', '/') to codeOnly(it.readText()) }
    }

    /**
     * 按行剥掉注释与 KDoc（`*` / `//` / `/*` 开头的行）。
     *
     * 朴素实现，够用但**不是词法分析**：它会被 `"*/*"` 这类字符串字面量骗到。本文件钉的 8 个文件里
     * 没有这种字面量（逐个核过）；真要上词法状态机，请用 `MiuixDialogContentTest` 里那份已验证的实现。
     * 它也不剥行尾注释，所以断言片段一律取自代码部分。
     */
    private fun codeOnly(src: String): String =
        src.lines()
            .filterNot {
                val t = it.trim()
                t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
            }
            .joinToString("\n")

    private fun source(relativePath: String): String {
        val root = sourceRoot
        assumeTrue("找不到源码根（非 Gradle 工作目录？），跳过", root != null)
        val file = File(root!!, relativePath)
        assumeTrue("找不到 $relativePath，跳过", file.exists())
        return codeOnly(file.readText())
    }

    /** 某个片段在全仓 main 源码（已剥注释）里的分布：相对路径 → 出现次数。 */
    private fun occurrences(fragment: String): Map<String, Int> =
        allMainSources.mapNotNull { (path, src) ->
            val n = src.count(fragment)
            if (n > 0) path to n else null
        }.toMap()

    private fun String.count(fragment: String): Int = split(fragment).size - 1

    @Test
    fun `注释剥离本身有效（阳性对照）`() {
        // 没有这一条，后面所有「只出现在 X 文件」的断言都可能是假的：UiEvent.kt 的 KDoc 里
        // 明明写着这两句文案，剥注释后必须查不到，才说明 count 数的是代码而不是注释。
        val root = sourceRoot
        assumeTrue("找不到源码根（非 Gradle 工作目录？），跳过", root != null)
        val file = File(root!!, uiEvent)
        assumeTrue("找不到 UiEvent.kt，跳过", file.exists())
        val raw = file.readText()
        assertTrue("对照前提：UiEvent.kt 的 KDoc 里应当提到撤销消耗文案", raw.contains("已减少一件并计入消耗"))
        assertTrue("对照前提：UiEvent.kt 的 KDoc 里应当提到自动同步文案", raw.contains("已自动同步到坚果云"))
        assertEquals("剥注释后 UiEvent.kt 不该再命中撤销消耗文案", 0, codeOnly(raw).count("已减少一件并计入消耗"))
        assertEquals("剥注释后 UiEvent.kt 不该再命中自动同步文案", 0, codeOnly(raw).count("已自动同步到坚果云"))
    }

    @Test
    fun `四条事件提示的文案各在其位、不漂移`() {
        assumeTrue("找不到源码根（非 Gradle 工作目录？），跳过", sourceRoot != null)
        // 期望值全部实测得来（2026-09-18）。改文案 ⇒ 这里就红 ⇒ 逼改的人承认这是行为改动、走真机复测。
        mapOf(
            "已减少一件并计入消耗" to mapOf(mainApp to 1),
            "件食品移入归档" to mapOf(mainApp to 1),
            "已删除「" to mapOf(consumptionLog to 1),
            "」的消耗记录" to mapOf(consumptionLog to 1),
            "已自动同步到坚果云 ☁️" to mapOf(appViewModelStartup to 1),
        ).forEach { (fragment, want) ->
            assertEquals("「$fragment」的分布变了（文案漂移或被复制到别处）", want, occurrences(fragment))
        }
    }

    @Test
    fun `恢复归档的两条文案在主壳与归档页两处保持一致`() {
        assumeTrue("找不到源码根（非 Gradle 工作目录？），跳过", sourceRoot != null)
        // 这是全仓唯一**故意重复**的提示文案：主壳那条服务「列表页搜索里恢复归档」（走 UiEvent），
        // 归档页那条服务「归档页自己点恢复」（走本地回调 `state.restoreEntry(id) { merged -> … }`，
        // 不经过事件系统）。两条流程不同、宿主不同，但用户看见的字必须一样 ⇒ 一起钉。
        listOf("库存中已有同批次「", "」到零食柜").forEach { fragment ->
            assertEquals(
                "「$fragment」应恰好出现在主壳与归档页各一处",
                mapOf(mainApp to 1, archiveScreen to 1),
                occurrences(fragment),
            )
        }
        // 「已合并」这一支的措辞必须在两条流程里各出现一次，少一个就说明有条流程丢了 merged 分支。
        assertEquals(1, source(mainApp).count("已合并数量"))
        assertEquals(1, source(archiveScreen).count("已合并数量"))
    }

    @Test
    fun `主壳不再直接碰两个主题的结果枚举`() {
        val src = source(mainApp)
        // #4b 的结构性判据：MD3 与 Miuix 的 SnackbarResult 是两个不相干的类型，它们一旦出现在主壳里，
        // 就说明「按主题分流」又在调用方重复了一遍（也违反 ARCHITECTURE.md:157
        // 「跨主题宿主对象不得漏回屏幕层」）。
        assertEquals("主壳里不该再出现任何 SnackbarResult 字样", 0, src.count("SnackbarResult"))
        assertEquals(
            "三处分流应全部走 helper（撤销消耗 / 恢复归档 / 批量归档）",
            3, src.count("showUndoSnackbarAcrossThemes("),
        )
        assertEquals(
            "helper 的 import 应恰好一条",
            1, src.count("import com.agon.app.ui.components.showUndoSnackbarAcrossThemes"),
        )
    }

    @Test
    fun `跨主题分流只有一处实现`() {
        val src = source(undoSnackbar)
        assertEquals(1, src.count("internal suspend fun showUndoSnackbarAcrossThemes"))
        // ⚠️ 用带接收者的整行片段，理由见类注释第 1 条（子串陷阱）。
        assertEquals("Miuix 侧判定应恰好一处",
            1, src.count("miuixHost.showUndoSnackbar(message) == MiuixSnackbarResult.ActionPerformed"))
        assertEquals("MD3 侧判定应恰好一处",
            1, src.count("md3Host.showUndoSnackbar(message) == SnackbarResult.ActionPerformed"))
        // 二级页那侧的同款分流在 AppSnackbarHostState 里（它自带两个宿主，所以能收在容器内）；
        // 主壳不能复用它：那容器是 remember(isMiuix) 建的，切主题会换宿主，而主壳的收集协程是
        // LaunchedEffect(Unit) ⇒ 协程会把提示弹到已经卸载的宿主上并永久挂住。详见 helper 的 KDoc。
        // ⚠️ 09-19 #11b 起这个容器在 ui/components/app/AppSnackbar.kt（原先与 AppScaffold 同住
        // AppChrome.kt）⇒ 下面两条读 appSnackbar、上面那条读 appChrome，别图省事并成一个常量：
        // 「谁定义」与「谁用默认落位」是两个不同的判据。
        val snackbarHost = source(appSnackbar)
        assertEquals(1, snackbarHost.count("class AppSnackbarHostState"))
        assertEquals(1, snackbarHost.count("suspend fun showUndoSnackbar(message: String): Boolean"))
    }

    @Test
    fun `撤销条的动作标签与 6 秒自关两主题一致`() {
        val src = source(undoSnackbar)
        // 不用 Short/Long 的理由写在扩展函数的 KDoc 里：MD3 有 action 时默认 Indefinite，Miuix 的
        // toMillis 会走无障碍 interactive timeout（HyperOS 上常被拉成永不超时）⇒ 必须自己 dismiss。
        // 这条钉住「两主题都还在自己 dismiss」：少一个就会出现某主题的撤销条永不消失。
        assertEquals("两主题各 delay 一次", 2, src.count("delay(UndoSnackbarTimeoutMs)"))
        assertEquals("两主题各用一次「撤销」标签", 2, src.count("actionLabel = UndoActionLabel"))
        assertEquals("时长常量只定义一次", 1, src.count("const val UndoSnackbarTimeoutMs = 6_000L"))
        assertEquals("动作标签只定义一次", 1, src.count("const val UndoActionLabel = \"撤销\""))
    }

    @Test
    fun `主壳覆盖层的落位不变`() {
        val src = source(mainApp)
        // 主壳这条不是 AppScaffold 里的宿主，而是一个自定义覆盖层：底部对齐 + 导航栏避让 + 键盘避让 +
        // 跟随底栏可见状态的动画偏移。四项缺一不可 —— 少 imePadding 就点不到键盘上方的「撤销」，
        // 少动画偏移就会被悬浮底栏盖住。
        mapOf(
            ".align(Alignment.BottomCenter)" to 1,
            ".navigationBarsPadding()" to 1,
            ".imePadding()" to 1,
            "padding(bottom = snackbarOffset)" to 1,
            "if (showChrome) 84.dp else 8.dp" to 1,
            // 宿主形态：MD3 侧是可滑掉的 SwipeDismissSnackbarHost，Miuix 侧是库自带宿主。
            "SwipeDismissSnackbarHost(snackbarHostState)" to 1,
            "MiuixSnackbarHost(miuixSnackbarHostState)" to 1,
        ).forEach { (fragment, want) ->
            assertEquals(
                "主壳覆盖层的「$fragment」变了 ⇒ 落位/形态改动，属行为变更、需真机复测",
                want, src.count(fragment),
            )
        }
    }

    @Test
    fun `二级页的落位不变（首页 FloatingNav、其余走默认 SystemBars）`() {
        // 首页有悬浮导航栏 ⇒ 抬 84dp；消耗记录页与归档页是二级页 ⇒ 走 AppScaffold 默认的 SystemBars
        // （MD3 侧 navigationBarsPadding() + 24dp，Miuix 侧不额外抬）。
        assertEquals(1, source(homeScreen).count("snackbarPlacement = AppSnackbarPlacement.FloatingNav"))
        assertEquals(
            "消耗记录页不该自己指定落位（指定了就是视觉改动）",
            0, source(consumptionLog).count("snackbarPlacement"),
        )
        assertEquals(
            "归档页同样不该自己指定落位",
            0, source(archiveScreen).count("snackbarPlacement"),
        )
        assertEquals(
            "AppScaffold 的默认落位必须是 SystemBars（消耗记录页与归档页都靠这个默认值）",
            1, source(appChrome).count("snackbarPlacement: AppSnackbarPlacement = AppSnackbarPlacement.SystemBars"),
        )
    }
}
