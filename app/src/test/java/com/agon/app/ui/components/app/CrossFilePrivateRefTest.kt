package com.agon.app.ui.components.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守卫（09-19，#11b 被 CI 打回来之后补的）：**一个文件的 `private` 顶层成员，不许被同包另一个文件按名字用**。
 *
 * 为什么要有这条：#11 的拆分全是「同包换文件」，所以拆之前我只查了两样东西——
 * 调用点的 import 要不要改（同包 ⇒ 不用）、新文件有没有"用了却没 import"的名字（全都在 ⇒ 没问题）。
 * 两条都过了，CI 却红了：`AppBarIconButton` 是 `AppChrome.kt` 的 `private fun`，
 * 而 Kotlin 的 `private` 顶层成员是**文件**级可见，`AppBarActions.kt` 按名字调它就编译失败。
 * 这种引用不会出现在任何 import 语句里 ⇒ 我那套 import 交叉检查**结构上看不见它**。
 *
 * 于是这条守卫补的正是那个盲区：它不看 import，只看「名字的家在哪个文件」。
 * 顺带一个现实理由：本仓库的编译器只有 CI，而 Actions 的日志在这个环境里取不回来，
 * 一个 `e: file:///...:14:5` 只能让人干瞪眼；单测失败则能把这句话写进断言消息里。
 *
 * 口径与 `ComponentAppHomeTest` / `CorruptGuardTest` 一致：**先剥注释**，文档里提一句符号名不算引用。
 * 只看"某名字全仓只被一个文件声明为 private、却被另一个文件用到"这一种形状：
 * 两个文件各有一个同名 private（比如各自的 `TAG`）是合法的，不报。
 */
class CrossFilePrivateRefTest {

    @Test
    fun `同包的另一个文件不引用彼此的私有顶层成员`() {
        val dir = uiDir() ?: return
        val codeOf = mutableMapOf<String, String>()
        for (f in dir.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            codeOf[f.path] = strip(f.readText())
        }
        assertTrue("ui/ 下一个 .kt 都没读到（口径变了？）", codeOf.isNotEmpty())

        // 每个 private 顶层成员：谁声明的。名字被多个文件各自声明 ⇒ 各自用各自的，跳过。
        val decl = Regex("""^private (?:fun|val|var|class|object)\s+(\w+)""", RegexOption.MULTILINE)
        val homes = mutableMapOf<String, MutableList<String>>()
        for ((path, code) in codeOf) {
            for (m in decl.findAll(code)) {
                // Kotlin 的 MatchResult 没有 Java Matcher 那套 group(i) ⇒ 只有 groupValues[1]
                // （09-19 就是在这里红了一次：`e: …:40:34 Unresolved reference 'group'.`）
                val name = m.groupValues[1]
                homes.getOrPut(name) { mutableListOf() }.add(path)
            }
        }
        val solo = homes.filter { it.value.size == 1 }.mapValues { it.value.first() }
        assertTrue("全仓没有任何 private 顶层成员（口径变了？）", solo.isNotEmpty())

        val use = mutableMapOf<String, MutableList<String>>()
        for ((path, code) in codeOf) {
            for (name in solo.keys) {
                if (path == solo[name]) continue
                // 引用形状：`name(` / `name,` / `name)` / `name.`
                if (Regex("""\b$name\b\s*[(:,.)]""").containsMatchIn(code)) {
                    use.getOrPut(name) { mutableListOf() }.add(File(path).name)
                }
            }
        }
        // 消息里只放违规清单：`use` 的 key 就是被跨文件引用的 private 名，value 是引用它的文件名。
        val bad = use.keys.sorted().joinToString("; ") { name -> "$name -> ${use[name]}" }
        assertTrue("跨文件引用了别的文件的 private 顶层成员，Kotlin 编译不过：$bad", use.isEmpty())
    }

    /** Gradle 的测试工作目录是模块目录（`app/`），IDE 里可能是仓库根 ⇒ 两处都找，找不到就跳过。 */
    private fun uiDir(): File? {
        val a = File("src/main/java/com/agon/app/ui")
        val b = File("app/src/main/java/com/agon/app/ui")
        return if (a.isDirectory) a else if (b.isDirectory) b else null
    }

    /** 剥块注释、行注释、KDoc 星号行与 import/package：文档里写一句符号名不该被算成引用。 */
    private fun strip(src: String): String {
        val noBlock = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL).replace(src, "")
        val lines = noBlock.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .filterNot { it.startsWith("import ") || it.startsWith("package ") }
        return lines.joinToString("\n")
    }
}
