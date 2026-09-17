package com.agon.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 「Miuix 弹窗的 content 必须是单一根节点」的静态守卫。
 *
 * 背景（2026-09-17 真机复测发现）：`AppFormDialog.kt` 的 Miuix 分支曾把「字段 `Column`」与「按钮 `Row`」
 * 写成 content 里的两个**平级**节点，真机上表现为输入框下边与「取消 / 添加」按钮上边重合。
 * 根因在库里：`top/yukonga/miuix/kmp/layout/DialogContentLayout.kt` 的 `DialogContent` 把
 * `title` / `summary` / `content()` 依次放进一个**不带 `verticalArrangement` 的 Column**，
 * 留白只由 title、summary 各自的 `padding(bottom = 12.dp)` 提供，`content()` 之后没有任何补白 ——
 * 所以 content 里两个平级节点之间是 **0dp**。
 *
 * 这类问题**编译期完全静默**（间距为 0 不是错误，ktlint / detekt / lint 都不会响），只有真机能发现，
 * 和 `ImeHandlingTest` 拦的「键盘盖住按钮」是同一档：所以用一条读源码的 JVM 单测兜住。
 * 标准写法与依据见 `ui/components/MiuixDialog.kt` 的 KDoc 与 `docs/MIUIX_UPGRADE.md` §2 第 8 条。
 *
 * **守卫口径**：
 * 1. 每个能解析出尾随 lambda 的 `MiuixDialog(…)` 调用点，其 content 的顶层语句必须**恰好 1 条**；
 * 2. 成功解析的调用点数不得少于 [ExpectedParsedSites]（2026-09-17 实测 8 个：`AppDialogs` 1 +
 *    `AppConfirmDialog` 1 + `AppFormDialog` 1 + `AppOptionDialog` 1 + `SettingsScreen` 4）。
 *    解析不出来的调用点**不算违规**，但会让这个计数掉下来 —— 于是「解析器失效」与「弹窗被删」
 *    都会在这一条上响，守卫不会静默空转（与 `tools/ci-gates.sh` 的 `detekt_selftest` 同思路）。
 *
 * **顶层语句的判定**用一个小型 Kotlin 词法状态机（[Walker]）：字符串、原始字符串、字符串模板 `${…}`、
 * 字符字面量（`'"'`、`'{'`）、行注释、**可嵌套的**块注释都要正确跳过 —— 否则模板里的 `${ids.size}`
 * 会被当成花括号深度，注释里的 `MiuixDialog(` 会被当成调用点，一个 `'"'` 会让状态机以为字符串从那里开始。
 * 算法先用 Python 实现同一套状态机在真源码上验证过：当前 8 个调用点全部判为单一根节点；
 * 把 `AppFormDialog.kt` 换回修复前（`git show HEAD~1:`）的版本，被准确判为 2 个顶层节点（见 [parserFlagsTheRealBug]）。
 *
 * 另有一组**按钮惯例**不变量（见 [dialogActionsFollowMiuixButtonConvention]）：弹窗动作不得用实心
 * `Button` + `buttonColorsPrimary()`；content 里有 2 个及以上 `TextButton(` 时至少一个要显式传 `colors =`。
 * 依据是上游 `example/shared/src/commonMain/kotlin/component/DialogSection.kt` 的 7 个弹窗一律
 * `TextButton` + 主要动作 `textButtonColorsPrimary()`（蓝底白字胶囊），以及库源码 `basic/Button.kt`：
 * `TextButton` 内部就是 `Button`，而 `Button` 用 `.squircleSurface(color = containerColor)` 实心填充，
 * 默认 `textButtonColors()` 的容器色是 `secondaryVariant`（浅灰）—— 所以「不传 colors」的主要动作
 * 会和「取消」完全同色（2026-09-17 真机复测发现 4 处这样的偏离）。
 *
 * 实现上刻意让 [Walker] **只对外暴露 Int / Boolean**：`Frame` 是 private 类型，一旦出现在 public 成员的
 * 签名或推断类型里就会撞上「public 暴露 private 类型」的编译错误 —— 本仓 CI 为此红过一次
 * （`internal val MainTabs` 的推断类型暴露了 `private data class TabSpec`，见 `devlog/2026-09-16.md` §13）。
 */
class MiuixDialogContentTest {

    private companion object {
        const val CallName = "MiuixDialog("

        /** 成功解析的调用点数下限，见类 KDoc 第 2 条。新增弹窗时它自然变大（断言是 `>=`）。 */
        const val ExpectedParsedSites = 8

        /** 违规信息里多个顶层节点之间的分隔符（提出来，免得在字符串模板里再嵌字符串字面量）。 */
        const val Sep = " + "

        const val TripleQuote = "\"\"\""

        const val DollarBrace = "\${"
    }

    /** Gradle 的测试工作目录是模块目录（app/），IDE 也可能用仓库根目录，两处都找一下。 */
    private fun sourceRoot(): File? =
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.exists() }

    @Test
    fun everyMiuixDialogContentHasSingleRoot() {
        val root = sourceRoot()
        assumeTrue("找不到主源码目录（工作目录既不是模块目录也不是仓库根目录）", root != null)
        val violations = mutableListOf<String>()
        var parsed = 0
        val files = requireNotNull(root).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .toList()
        for (file in files) {
            parsed += scanFile(file, violations)
        }
        assertTrue(
            "只解析出 $parsed 个 MiuixDialog 调用点，少于已知的 $ExpectedParsedSites 个 —— " +
                "要么解析器失效（守卫已在空转），要么弹窗被删/改写成了具名参数 content，请核对后同步更新下限",
            parsed >= ExpectedParsedSites,
        )
        assertTrue(
            violations.joinToString("\n  ", "Miuix 弹窗 content 必须是单一根节点：\n  "),
            violations.isEmpty(),
        )
    }

    /**
     * 解析器自检：既要认得字符串模板 / 注释 / 字符字面量 / 嵌套 lambda（不误报），
     * 也要抓得住真机上出现过的那个写法（不漏报）。
     */
    @Test
    fun parserFlagsTheRealBug() {
        val ok = """
            MiuixDialog(show = show, onDismissRequest = onDismiss, title = "已选 ${'$'}{ids.size} 件") {
                // 这一行注释不是节点
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("共 ${'$'}{items.size} 项 /* 在字符串里，不是注释 */")
                    Text(names.joinToString("") { it.trim('"', ',') })
                    Row { TextButton(onClick = { dismiss() }) { Text("确定") } }
                }
            }
        """.trimIndent()
        assertEquals(1L, contentBodyOfFirstSite(ok)?.let { topLevelStatements(it).size }?.toLong() ?: -1L)

        // 2026-09-17 真机上发现的写法：字段 Column 与按钮 Row 平级 → 两者之间 0dp
        val bad = """
            MiuixDialog(show = show, onDismissRequest = onDismiss, title = "添加分类") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { MiuixTextField(state = fieldState) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MiuixTextButton(text = "取消") }
            }
        """.trimIndent()
        assertEquals(2L, contentBodyOfFirstSite(bad)?.let { topLevelStatements(it).size }?.toLong() ?: -1L)
    }

    /**
     * 弹窗动作按钮的两条惯例。都是「偏离了也不报错、只有真机看得出来」的那一类，故一并做成静态守卫。
     */
    @Test
    fun dialogActionsFollowMiuixButtonConvention() {
        val root = sourceRoot()
        assumeTrue("找不到主源码目录（工作目录既不是模块目录也不是仓库根目录）", root != null)
        val violations = mutableListOf<String>()
        val files = requireNotNull(root).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .toList()
        for (file in files) {
            scanButtons(file, violations)
        }
        assertTrue(
            violations.joinToString("\n  ", "Miuix 弹窗的动作按钮不符合库惯例：\n  "),
            violations.isEmpty(),
        )
    }

    // ------------------------------------------------------------------ 扫描

    /**
     * 剥掉注释、保留代码与字符串，供按钮惯例那类**原文匹配**的断言使用。
     *
     * 必须走 [Walker] 而不是正则：`ImeHandlingTest.codeOnly()` 用正则剥块注释，被 `SettingsScreen.kt`
     * 里 MIME 通配符（星号斜杠星号那种写法）反咬一口 —— 约 600 行真实代码被当成注释吞掉
     * （见 `devlog/2026-09-17.md`）。本守卫手上已经有词法器，没理由再踩一遍。
     * 反过来，注释里写「原先这里是 Button + 那个实心配色工厂」这类说明也不该触发断言
     * （第一版就是这么误报的），剥掉注释正好两头都解决。
     */
    private fun codeOnly(src: String): String {
        val walker = Walker(src)
        val out = StringBuilder(src.length)
        while (walker.i < src.length) {
            if (!walker.inComment()) out.append(src[walker.i])
            walker.step()
        }
        return out.toString()
    }

    /**
     * A. 不得用实心 `Button` + `buttonColorsPrimary()` 当弹窗动作（上游一律 `TextButton`）；
     * B. content 里出现 2 个及以上 `TextButton(` 时，至少一个必须显式传 `colors =` ——
     *    静态判不出「哪个是主要动作」，所以只约束「不许全是库默认色」：默认容器色是
     *    `secondaryVariant` 浅灰，全默认时用户分不出主要动作与「取消」。
     *    危险动作传 error 色同样满足 B（`SettingsScreen` 的「覆盖导入」就是这种）。
     */
    private fun scanButtons(file: File, violations: MutableList<String>) {
        val src = file.readText()
        if (!src.contains(CallName)) return
        val name = file.name
        for (pos in callSites(src)) {
            val body = contentBody(src, pos) ?: continue
            val code = codeOnly(body)
            val line = src.substring(0, pos).count { it == '\n' } + 1
            if (code.contains("buttonColorsPrimary(")) {
                violations += "$name:$line 弹窗动作用了实心 Button(buttonColorsPrimary()) —— " +
                    "上游 DialogSection.kt 的 7 个弹窗一律 TextButton + textButtonColorsPrimary()"
            }
            val buttons = code.split("TextButton(").size - 1
            if (buttons >= 2 && !code.contains("colors =")) {
                violations += "$name:$line 弹窗里有 $buttons 个 TextButton 却没有一个显式传 colors = —— " +
                    "主要动作应是 textButtonColorsPrimary()（蓝底白字），库默认是 secondaryVariant 浅灰"
            }
        }
    }

    /** 返回本文件里成功解析的调用点数；违规写进 [violations]。 */
    private fun scanFile(file: File, violations: MutableList<String>): Int {
        val src = file.readText()
        if (!src.contains(CallName)) return 0
        val name = file.name
        var parsed = 0
        for (pos in callSites(src)) {
            val body = contentBody(src, pos)
            if (body == null) continue
            parsed++
            val statements = topLevelStatements(body)
            if (statements.size != 1) {
                val line = src.substring(0, pos).count { it == '\n' } + 1
                val heads = statements.joinToString(Sep) { firstLine(it) }
                val count = statements.size
                violations += "$name:$line content 有 $count 个顶层节点（$heads）" +
                    " —— 库的弹窗根 Column 不带间距，平级节点之间会是 0dp；" +
                    "请合成单一 Column(verticalArrangement = Arrangement.spacedBy(12.dp))"
            }
        }
        return parsed
    }

    private fun callSites(src: String): List<Int> {
        val out = mutableListOf<Int>()
        var j = src.indexOf(CallName)
        while (j >= 0) {
            if (isRealCallSite(src, j)) out += j
            j = src.indexOf(CallName, j + 1)
        }
        return out
    }

    /** 排除声明本身（`fun MiuixDialog(`）与注释 / KDoc 里的提及。 */
    private fun isRealCallSite(src: String, pos: Int): Boolean {
        val lineStart = src.lastIndexOf('\n', pos)
        val prefix = src.substring(lineStart + 1, pos)
        if ("fun " in prefix) return false
        val trimmed = prefix.trim()
        return !trimmed.startsWith("*") && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
    }

    /** 某个调用点的尾随 lambda body；结构不符（具名参数 content、括号配不上）时返回 null。 */
    private fun contentBody(src: String, pos: Int): String? {
        val closeParen = matchParen(src, pos + CallName.length - 1)
        val brace = if (closeParen < 0) -1 else trailingLambdaBrace(src, closeParen)
        val end = if (brace < 0) -1 else matchBrace(src, brace)
        return if (end > brace) src.substring(brace + 1, end) else null
    }

    /** 参数表的右括号之后、跳过空白，是不是 `{`（尾随 lambda）。 */
    private fun trailingLambdaBrace(src: String, closeParen: Int): Int {
        var k = closeParen + 1
        while (k < src.length && src[k].isWhitespace()) k++
        return if (k < src.length && src[k] == '{') k else -1
    }

    private fun contentBodyOfFirstSite(src: String): String? {
        val sites = callSites(src)
        return if (sites.isEmpty()) null else contentBody(src, sites.first())
    }

    // ------------------------------------------------------------------ 词法状态机

    /** `c` 代码 / `s` 字符串 / `l` 行注释 / `b` 块注释（Kotlin 的块注释可嵌套，故记 [blockDepth]）。 */
    private class Frame(val kind: Char, val raw: Boolean = false, val template: Boolean = false) {
        var paren = 0
        var brace = 0
        var blockDepth = 1
    }

    /** 逐字符推进的词法状态机；对外只暴露 Int / Boolean，见类 KDoc 最后一段。 */
    private class Walker(val src: String) {
        private val frames = ArrayList<Frame>()
        var i = 0

        init {
            frames.add(Frame('c'))
        }

        private fun top(): Frame = frames[frames.size - 1]

        private fun pop() {
            frames.removeAt(frames.size - 1)
        }

        /** 是否处在最外层代码语境（不在字符串 / 注释 / 模板里）。 */
        fun atRootCode(): Boolean = frames.size == 1 && frames[0].kind == 'c'

        /** 最外层的括号深度；不在最外层代码里时返回 -1（调用方据此继续推进而不是误判配对完成）。 */
        fun rootParen(): Int = if (atRootCode()) frames[0].paren else -1

        /** 最外层的花括号深度；同上。 */
        fun rootBrace(): Int = if (atRootCode()) frames[0].brace else -1

        /** 语句边界只在「最外层代码 + 括号与花括号深度都为 0」处判定。 */
        fun atTopLevel(): Boolean = atRootCode() && frames[0].paren == 0 && frames[0].brace == 0

        /** 当前是否处在注释里（行注释 / 块注释）。给 [codeOnly] 用；对外仍只暴露 Boolean。 */
        fun inComment(): Boolean = top().kind == 'l' || top().kind == 'b'

        fun step() {
            val kind = top().kind
            if (kind == 'l') {
                stepLine()
            } else if (kind == 'b') {
                stepBlock()
            } else if (kind == 's') {
                stepStr()
            } else {
                stepCode()
            }
        }

        private fun stepLine() {
            if (src[i] == '\n') pop()
            i++
        }

        private fun stepBlock() {
            val t = top()
            val two = twoChars()
            if (two == "/*") {
                t.blockDepth++
                i += 2
            } else if (two == "*/") {
                t.blockDepth--
                i += 2
                if (t.blockDepth == 0) pop()
            } else {
                i++
            }
        }

        private fun stepStr() {
            val t = top()
            val c = src[i]
            if (!t.raw && c == '\\') {
                i += 2
            } else if (twoChars() == DollarBrace) {
                frames.add(Frame('c', template = true))
                i += 2
            } else if (t.raw && threeChars() == TripleQuote) {
                pop()
                i += 3
            } else if (!t.raw && c == '"') {
                pop()
                i++
            } else {
                i++
            }
        }

        private fun stepCode() {
            val t = top()
            val c = src[i]
            val two = twoChars()
            val three = threeChars()
            if (c == '\'') {
                i += charLiteralLength()
            } else if (two == "//") {
                frames.add(Frame('l'))
                i += 2
            } else if (two == "/*") {
                frames.add(Frame('b'))
                i += 2
            } else if (three == TripleQuote) {
                frames.add(Frame('s', raw = true))
                i += 3
            } else if (c == '"') {
                frames.add(Frame('s'))
                i++
            } else if (c == '{') {
                t.brace++
                i++
            } else if (c == '}') {
                closeBrace(t)
            } else if (c == '(' || c == '[') {
                t.paren++
                i++
            } else if (c == ')' || c == ']') {
                t.paren--
                i++
            } else {
                i++
            }
        }

        /** 字符串模板 `${…}` 的收尾花括号属于字符串，不计入代码块深度。 */
        private fun closeBrace(t: Frame) {
            if (t.template && t.brace == 0) {
                pop()
                i++
            } else {
                t.brace--
                i++
            }
        }

        /**
         * 字符字面量（`'\n'`、`'"'`、`'{'`）的整体长度：里面的引号与花括号**不参与配对**，
         * 否则一个 `'"'` 就会让状态机以为字符串从这里开始，把后面几十行全吞掉。
         */
        private fun charLiteralLength(): Int {
            var j = i + 1
            var closed = false
            while (j < src.length && !closed) {
                if (src[j] == '\\') {
                    j += 2
                } else {
                    closed = src[j] == '\''
                    if (!closed) j++
                }
            }
            return j + 1 - i
        }

        private fun twoChars(): String = if (i + 1 < src.length) src.substring(i, i + 2) else ""

        private fun threeChars(): String = if (i + 2 < src.length) src.substring(i, i + 3) else ""
    }

    /**
     * [openPos] 指向 `(`，返回配对 `)` 的下标；解析不出来返回 -1。
     *
     * 退出循环后要**再判一次深度**而不是判 `w.i < src.length`：配对字符正好是最后一个字符时
     * `w.i == src.length`，按位置判会误报「没找到」（真源码里弹窗后面总还有代码，所以扫描 8 处时
     * 这个 off-by-one 不发作，是 canary 把它逼出来的）。
     */
    private fun matchParen(src: String, openPos: Int): Int {
        val w = Walker(src)
        w.i = openPos
        w.step()
        while (w.i < src.length && w.rootParen() != 0) {
            w.step()
        }
        return if (w.rootParen() == 0) w.i - 1 else -1
    }

    /** [openPos] 指向 `{`，返回配对 `}` 的下标；解析不出来返回 -1。off-by-one 说明见 [matchParen]。 */
    private fun matchBrace(src: String, openPos: Int): Int {
        val w = Walker(src)
        w.i = openPos
        w.step()
        while (w.i < src.length && w.rootBrace() != 0) {
            w.step()
        }
        return if (w.rootBrace() == 0) w.i - 1 else -1
    }

    /** 顶层语句：最外层代码语境下、括号与花括号深度均为 0 时，从一个非空白字符起到行尾。 */
    private fun topLevelStatements(body: String): List<String> {
        val w = Walker(body)
        val out = mutableListOf<String>()
        var cur = -1
        while (w.i < body.length) {
            cur = stepStatementChar(body, w, cur, out)
            w.step()
        }
        if (cur >= 0) addStatement(body.substring(cur), out)
        return out
    }

    /** 喂一个字符给「当前语句」：返回更新后的语句起点（-1 = 当前不在语句中）。 */
    private fun stepStatementChar(body: String, w: Walker, cur: Int, out: MutableList<String>): Int {
        if (!w.atTopLevel()) return cur
        val c = body[w.i]
        var next = cur
        if (next < 0 && !c.isWhitespace()) next = w.i
        if (c == '\n' && next >= 0) {
            addStatement(body.substring(next, w.i), out)
            next = -1
        }
        return next
    }

    private fun addStatement(text: String, out: MutableList<String>) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) out += trimmed
    }

    /** 违规信息里用：跳过前导注释行，给出这条语句真正的首个节点（截断到 40 字符）。 */
    private fun firstLine(statement: String): String =
        statement.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("//") }
            ?.take(40)
            ?: "(只有注释)"
}
