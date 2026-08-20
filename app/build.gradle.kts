import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 从项目根目录的 keystore.properties 读取 release 签名信息（该文件不入库）。
// CI 环境由 workflow 从 GitHub Secrets 生成此文件。
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

// 项目内的调试密钥（可选）。CI 环境若未提交该文件，
// 则不覆盖 debug 签名配置，AGP 会回退到默认调试密钥
// （~/.android/debug.keystore，不存在时自动生成），保证 CI 可构建。
val projectDebugKeystore = rootProject.file("debug.keystore")

android {
    namespace = "com.agon.app"
    // Miuix 0.9.4-rc01 及其传递依赖（Compose 1.12.0-rc01 等）要求 compileSdk ≥ 37。
    // compileSdk 与 targetSdk/minSdk 相互独立，仅此一项升级即可。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.chileme.pantry"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        getByName("debug") {
            // 仅当项目根目录存在 debug.keystore 时才覆盖默认配置；
            // 否则保留 AGP 默认（~/.android/debug.keystore，自动生成），
            // 避免 CI 上因文件缺失导致 validateSigning 失败。
            if (projectDebugKeystore.exists()) {
                storeFile = projectDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            // 凭据来自 keystore.properties（不入库）；缺失时回退 debug 签名，
            // 保证本地无密钥环境仍可完成 release 构建验证。
            if (hasReleaseSigning) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // R8 代码压缩（去除未使用代码）+ 资源压缩，但不混淆：
            // proguard-rules.pro 中 -dontobfuscate 保留类名/方法名/字段名，
            // 崩溃堆栈可直接阅读，无需 mapping.txt。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// AGP 9 起 kotlinOptions DSL 已移除，改用 KGP 的 compilerOptions。
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    implementation("androidx.navigation:navigation-compose:2.9.7")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // Miuix（HyperOS 风格 Compose 组件库），版本对齐 skill 基线 v0.9.4-rc01。
    // 使用 common 坐标，Gradle Module Metadata 会自动解析到 android 变体。
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4-rc01")

    implementation("com.materialkolor:material-kolor:4.0.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
