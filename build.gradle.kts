// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 内置 Kotlin：不再单独应用 org.jetbrains.kotlin.android（已与内置 Kotlin 冲突）。
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

gradle.addBuildListener(object : org.gradle.BuildAdapter() {
    override fun buildFinished(result: org.gradle.BuildResult) {
        val failure = result.failure
        if (failure != null) {
            println("::error::Build failure: ${failure.message}")
            var cur = failure.cause
            while (cur != null) {
                println("::error::Caused by: ${cur.message}")
                cur = cur.cause
            }
        }
    }
})
