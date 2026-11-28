pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // CI 若仍是 Java 17，toolchain(21) 靠这个解析器下载 JDK 21
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AgonApp"
include(":app")

// TEMP-DIAG (revert after diagnosis): 构建失败时把异常链以 ::error:: 注解回传
gradle.buildFinished {
    val f = it.failure
    if (f != null) {
        var t: Throwable? = f
        var depth = 0
        while (t != null && depth < 10) {
            val msg = (t.message ?: t.javaClass.name).replace("\n", " | ").take(900)
            println("::error title=gradle-diag-$depth::${t.javaClass.simpleName}: $msg")
            t = t.cause
            depth++
        }
    }
}
