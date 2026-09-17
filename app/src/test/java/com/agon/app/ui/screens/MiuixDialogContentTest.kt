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
 * 这类问题**编译期完全静默**（间距为 0 不是错误，lint 与 detekt 都不会响），只有真机能发现，
 * 和 `ImeHandlingTest` 拦的「键盘盖住按钮」是同一档：所以用一条读源码的 JVM 单测兜住。
 * 标准写法与依据见 `ui/components/MiuixDialog.kt` 的 KDoc 与 `docs/MIUIX_UPGRADE.md` §2 第 8 条。
 *
 * 守卫口径：
 * 1. 每个 `MiuixDialog(…)` 调用点的尾随 lambda 里，顶层语句必须**恰好 1 条**；
 * 2. 扫到的调用点数不得少于 [ExpectedCallSites] —— 解析器或源码布局变了会让守卫**空转**
 *    （比「没有守卫」更糟），所以宁可在这里响一次（与 `tools/ci-gates.sh` 的 `detekt_selftest` 同思路）。
 *
 * 顶层语句的判定用一个小型 Kotlin 词法状态机（[Walker]）：字符串 / 原始字符串 / 字符串模板 `${…}` /
 * 字符字面量（`'"'`、`'{'`）/ 行注释 / **可嵌套的**块注释都要正确跳过，否则模板里的 `${ids.size}`
 * 会被当成花括号深度、注释里的 `MiuixDialog(` 会被当成调用点。算法先在真源码上验证过：当前 8 个调用点
 * 全部通过，而修复前的 `AppFormDialog.kt` 被准确判为 2 个顶层节点（见 [parserFlagsTheRealBug]）。
 */
class MiuixDialogContentTest {

    private companion object {
        const val CallName = "MiuixDialog("

        /**
         * 已知调用点数下限（2026-09-17 实测 8 个）：`AppDialogs.kt` 1 + `AppConfirmDialog.kt` 1 +
         * `AppFormDialog.kt` 1 + `AppOptionDialog.kt` 1 + `SettingsScreen.kt` 4。
         * 新增弹窗时这个数会自然变大（断言是 `>=`）；**删除**弹窗导致低于此数，说明清单该更新了。
         */
        const val ExpectedCallSites = 8

        const val TripleQuote = "\"\"\""

        /** 违规信息里多个顶层节点之间的分隔符（单独提出来，免得在字符串模板里再嵌一个字符串字面量）。 */
        const val SEP = " + "
    }

    /** Gradle 的测试工作目录是模块目录（app/），IDE 也可能用仓库根目录，两处都找一下。 */
    private fun sourceRoot(): File? =
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.exists() }

    @Test
    fun everyMiuixDialogContentHasSingleRoot() {
        val root = sourceRoot()
        assumeTrue("找不到 app/src/main/java（工作目录既不是模块目录也不是仓库根目录）", root != null)
        val files = requireNotNull(root).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .toList()

        val violations = mutableListOf<String>()
        var sites = 0
        for (file in files) {
            sites += checkFile(file, violations)
        }
        assertTrue(
            "只扫到 $sites 个 MiuixDialog 调用点，少于已知的 $ExpectedCallSites 个 —— " +
                "源码布局或解析器变了，这条守卫已经在空转，请修正后同步更新 ExpectedCallSites",
            sites >= ExpectedCallSites,
        )
        assertTrue(violations.joinToString("\n  ", "Miuix 弹窗 content 必须是单一根节点：\n  "), violations.isEmpty())
    }

    /** 扫描单个文件里的所有 `MiuixDialog(…)` 调用点：返回扫到的数量，违规项写进 [violations]。 */
    private fun checkFile(file: File, violations: MutableList<String>): Int {
        val src = file.readText()
        if (!src.contains(CallName)) return 0
        var sites = 0
        val fileName = file.name
        for (pos in callSites(src)) {
            val line = src.substring(0, pos).count { it == '\n' } + 1
            val closeParen = matchParen(src, pos + CallName.length - 1)
            assertTrue(
                "$fileName:$line 解析器没能配对 MiuixDialog 的参数表 —— 这是守卫自身失效，不是代码问题",
                closeParen >= 0,
            )
            val brace = trailingLambdaBrace(src, closeParen)
            if (brace < 0) {
                violations += "$fileName:$line 没找到尾随 lambda（content 若改用具名参数，守卫要跟着改扫描位置）"
                continue
            }
            val end = matchBrace(src, brace)
            assertTrue("$fileName:$line 解析器没能配对 content 的花括号", end > brace)
            val statements = topLevelStatements(src.substring(brace + 1, end))
            sites++
            if (statements.size != 1) {
                val heads = statements.joinToString(SEP) { firstLine(it) }
                val count = statements.size
                violations += "$fileName:$line content 有 $count 个顶层节点（$heads）" +
                    " —— 库的弹窗根 Column 不带间距，平级节点之间会是 0dp；" +
                    "请合成单一 Column(verticalArrangement = Arrangement.spacedBy(12.dp))"
            }
        }
        return sites
    }

    /**
     * 解析器自检：既要认得字符串模板 / 注释 / 嵌套 lambda（不误报），
     * 也要抓得住真机上出现过的那个写法（不漏报）。
     */
    @Test
    fun parserFlagsTheRealBug() {
        val ok = """
            MiuixDialog(show = show, onDismissRequest = onDismiss, title = "已选 ${'$'}{ids.size} 件") {
                // 这一行注释不是节点
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("共 ${'$'}{items.size} 项 /* 在字符串里，不是注释 */")
                    Text(names.joinToString("") { it.trim('"', ',') })   // 字符字面量里的引号不参与配对
                    Row { TextButton(onClick = { dismiss() }) { Text("确定") } }
                }
            }
        """.trimIndent()
        assertEquals(1, statementsOfFirstSite(ok).size)

        // 2026-09-17 真机上发现的写法：字段 Column 与按钮 Row 平级 → 两者之间 0dp
        val bad = """
            MiuixDialog(show = show, onDismissRequest = onDismiss, title = "添加分类") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { MiuixTextField(state = fieldState) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MiuixTextButton(text = "取消") }
            }
        """.trimIndent()
        assertEquals(2, statementsOfFirstSite(bad).size)
    }

    // ------------------------------------------------------------------ 调用点定位

    private fun callSites(src: String): List<Int> {
        val out = mutableListOf<Int>()
        var j = src.indexOf(CallName)
        while (j >= 0) {
            if (isRealCallSite(src, j)) out += j
            j = src.indexOf(CallName, j + 1)
        }
        return out
    }

    /** 排除声明本身（`fun MiuixDialog(`）与注释/KDoc 里的提及。 */
    private fun isRealCallSite(src: String, pos: Int): Boolean {
        val lineStart = src.lastIndexOf('\n', pos)
        val prefix = src.substring(lineStart + 1, pos)
        if ("fun " in prefix) return false
        val trimmed = prefix.trim()
        return !trimmed.startsWith("*") && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
    }

    /** 参数表的右括号之后、跳过空白，是不是 `{`（尾随 lambda）。 */
    private fun trailingLambdaBrace(src: String, closeParen: Int): Int {
        var k = closeParen + 1
        while (k < src.length && src[k].isWhitespace()) k++
        return if (k < src.length && src[k] == '{') k else -1
    }

    // ------------------------------------------------------------------ 词法状态机

    /** `c` 代码 / `s` 字符串 / `l` 行注释 / `b` 块注释（Kotlin 的块注释可嵌套，故记 [blockDepth]）。 */
    private class Frame(val kind: Char, val raw: Boolean = false, val template: Boolean = false) {
        var paren = 0
        var brace = 0
        var blockDepth = 1
    }

    private class Walker(val src: String) {
        val stack = ArrayDeque<Frame>()
        var i = 0

        init {
            stack.addLast(Frame('c'))
        }

        /** 最外层的代码帧；处在字符串/注释/模板里时返回 null —— 语句边界只在最外层代码里判定。 */
        fun rootCode(): Frame? {
            val t = stack.last()
            return if (stack.size == 1 && t.kind == 'c') t else null
        }

        fun atTopLevel(): Boolean {
            val t = rootCode()
            return t != null && t.paren == 0 && t.brace == 0
        }

        fun step() {
            when (stack.last().kind) {
                'l' -> stepLineComment()
                'b' -> stepBlockComment()
                's' -> stepString()
                else -> stepCode()
            }
        }

        private fun stepLineComment() {
            if (src[i] == '\n') stack.removeLast()
            i++
        }

        private fun stepBlockComment() {
            val t = stack.last()
            when (twoChars()) {
                "/*" -> { t.blockDepth++; i += 2 }
                "*/" -> { t.blockDepth--; i += 2; if (t.blockDepth == 0) stack.removeLast() }
                else -> i++
            }
        }

        private fun stepString() {
            val t = stack.last()
            when {
                !t.raw && src[i] == '\\' -> i += 2
                twoChars() == "\${" -> { stack.addLast(Frame('c', template = true)); i += 2 }
                t.raw && threeChars() == TripleQuote -> { stack.removeLast(); i += 3 }
                !t.raw && src[i] == '"' -> { stack.removeLast(); i++ }
                else -> i++
            }
        }

        private fun stepCode() {
            val t = stack.last()
            when {
                src[i] == '\'' -> i += charLiteralLength()
                twoChars() == "//" -> { stack.addLast(Frame('l')); i += 2 }
                twoChars() == "/*" -> { stack.addLast(Frame('b')); i += 2 }
                threeChars() == TripleQuote -> { stack.addLast(Frame('s', raw = true)); i += 3 }
                src[i] == '"' -> { stack.addLast(Frame('s')); i++ }
                src[i] == '{' -> { t.brace++; i++ }
                src[i] == '}' -> closeBrace(t)
                src[i] == '(' || src[i] == '[' -> { t.paren++; i++ }
                src[i] == ')' || src[i] == ']' -> { t.paren--; i++ }
                else -> i++
            }
        }

        /** 字符串模板 `${…}` 的收尾花括号属于字符串，不计入代码块深度。 */
        private fun closeBrace(t: Frame) {
            if (t.template && t.brace == 0) { stack.removeLast(); i++ } else { t.brace--; i++ }
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

    /** [openPos] 指向 `(`，返回配对 `)` 的下标；解析不出来返回 -1（守卫会就此报错，不静默）。 */
    private fun matchParen(src: String, openPos: Int): Int {
        val w = Walker(src)
        w.i = openPos
        w.step()
        while (w.i < src.length) {
            val t = w.rootCode()
            if (t != null && t.paren == 0) return w.i - 1
            w.step()
        }
        return -1
    }

    /** [openPos] 指向 `{`，返回配对 `}` 的下标。 */
    private fun matchBrace(src: String, openPos: Int): Int {
        val w = Walker(src)
        w.i = openPos
        w.step()
        while (w.i < src.length) {
            val t = w.rootCode()
            if (t != null && t.brace == 0) return w.i - 1
            w.step()
        }
        return -1
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

    private fun statementsOfFirstSite(src: String): List<String> {
        val pos = callSites(src).first()
        val closeParen = matchParen(src, pos + CallName.length - 1)
        val brace = trailingLambdaBrace(src, closeParen)
        return topLevelStatements(src.substring(brace + 1, matchBrace(src, brace)))
    }

    /** 失败信息里用：跳过前导注释行，给出这条语句真正的首个节点（截断到 40 字符）。 */
    private fun firstLine(statement: String): String =
        statement.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("//") }
            ?.take(40)
            ?: "(只有注释)"
}
