// Top-level build file
plugins {
    id("com.android.application") version "9.3.1" apply false
    // AGP 9 内置 Kotlin：不再单独应用 org.jetbrains.kotlin.android（已与内置 Kotlin 冲突）。
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
}
