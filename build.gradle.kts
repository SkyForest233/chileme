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
            // 扫描 build 目录寻找 kotlin 编译器产生的日志文件
            val buildDir = task.project.layout.buildDirectory.asFile.orNull
            if (buildDir != null && buildDir.exists()) {
                buildDir.walkTopDown().forEach { file ->
                    if (file.isFile && (file.extension in listOf("log", "txt", "output") || file.name.contains("error") || file.name.contains("kotlin"))) {
                        if (file.length() < 100_000) {
                            java.lang.System.err.println("::notice::--- LOG FILE: ${file.path} ---")
                            file.readLines().forEach { line ->
                                if (line.contains("error") || line.contains("e: ") || line.contains("Unresolved") || line.contains("mismatch")) {
                                    java.lang.System.err.println("::error::$line")
                                } else {
                                    java.lang.System.err.println("::notice::$line")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
})
