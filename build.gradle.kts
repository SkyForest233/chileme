// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 内置 Kotlin：不再单独应用 org.jetbrains.kotlin.android（已与内置 Kotlin 冲突）。
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

gradle.addListener(object : org.gradle.api.execution.TaskExecutionListener {
    override fun beforeExecute(task: org.gradle.api.Task) {}
    override fun afterExecute(task: org.gradle.api.Task, state: org.gradle.api.tasks.TaskState) {
        val f = state.failure
        if (f != null) {
            java.lang.System.err.println("::error::Task ${task.path} failed: ${f.message}")
            var cause = f.cause
            while (cause != null) {
                java.lang.System.err.println("::error::Cause: ${cause.message}")
                cause = cause.cause
            }
        }
    }
})
