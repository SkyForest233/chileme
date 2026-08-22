// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 内置 Kotlin：不再单独应用 org.jetbrains.kotlin.android（已与内置 Kotlin 冲突）。
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

try {
    val loggingOutput = (gradle as org.gradle.api.internal.GradleInternal).services.get(org.gradle.internal.logging.LoggingOutputInternal::class.java)
    val listener = org.gradle.api.logging.StandardOutputListener { output ->
        val text = output.toString().trim()
        if (text.startsWith("e: ") || text.contains("Unresolved reference") || text.contains("Type mismatch") || text.contains("None of the following") || text.contains("Cannot find a parameter") || text.contains("Argument type mismatch")) {
            java.lang.System.err.println("::error::$text")
        }
    }
    loggingOutput.addStandardOutputListener(listener)
    loggingOutput.addStandardErrorListener(listener)
} catch (e: Throwable) {
    java.lang.System.err.println("Logging hook error: ${e.message}")
}
