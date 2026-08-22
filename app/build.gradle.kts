import java.util.Properties
import org.gradle.api.logging.StandardOutputListener

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 捕获编译错误并在 CI 环境打出 GitHub Actions ::error:: 注解，以便通过 API 读取具体报错
gradle.addListener(StandardOutputListener { output ->
    val text = output.toString()
    if (text.startsWith("e: ") || text.contains("Unresolved reference") || text.contains("Type mismatch") || text.contains("None of the following functions can be called with the arguments supplied")) {
        println("::error::$text")
    }
})

// ---- Release 签名凭据 ----
// 优先级：环境变量（CI / GitHub Secrets）> 根目录 keystore.properties（本地，不入库）。
// 历史问题：此前只读 keystore.properties，而 CI 只设置了环境变量且从不生成该文件，
// 导致 hasReleaseSigning 恒为 false、release 静默回退 debug 签名（详见
// docs/audits/2026-08-21-fix-plan.md 阶段 1）。现两条通道都支持，且缺失时不再静默降级。
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/** 读环境变量；空串视为未设置。用 providers API 以便 Gradle 正确追踪输入。 */
fun env(name: String): String? =
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

/** 环境变量优先，回退 keystore.properties。 */
fun signingValue(envName: String, propName: String): String? =
    env(envName) ?: keystoreProperties.getProperty(propName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("RELEASE_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("RELEASE_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")

// 四项齐备才算配置了正式签名，避免"配了一半"导致更隐蔽的失败。
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

// 本地无密钥时可用 -PallowUnsignedRelease=true 跑通 assembleRelease 做 R8 / 体积验证。
// CI 不传该参数，因此凭据缺失会直接失败，杜绝"发出去才发现是 debug 签名"。
val allowUnsignedRelease =
    providers.gradleProperty("allowUnsignedRelease").orNull?.toBoolean() ?: false

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
        // java.time（LocalDate/ChronoUnit/DateTimeFormatter 等，全项目 28 处）是 API 26 引入。
        // 此前 minSdk=24 且未开启 core library desugaring，API 24/25 设备一进首页即
        // NoClassDefFoundError（daysLeft 走 ChronoUnit）。2026 年 Android 7.x 存量可忽略，
        // 故直接提升到 26，省去脱糖的包体与构建开销。详见 fix-plan 阶段 2。
        minSdk = 26
        targetSdk = 36
        // versionCode 由 CI 注入（GITHUB_RUN_NUMBER 单调递增），本地开发回落 1。
        // 此前恒为 1，配合 debug 签名导致已发布的三个 tag 对系统而言是同一版本、无法升级。
        // 注意：设置页「关于」展示的 "吃了么 v1.0" 是硬编码字符串，不读 BuildConfig，
        // 因此本项不影响 CLAUDE.md §5 的「展示版本号锁定 v1.0」约束。
        versionCode = env("APP_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = env("APP_VERSION_NAME")?.removePrefix("v") ?: "1.0"
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
            // 凭据来自环境变量或 keystore.properties（均不入库）。
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
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
            // 不再静默回退 debug 签名。注意这里只做「配置」，不在此处 throw：
            // buildTypes 块在**配置阶段**执行，即便只跑 assembleDebug 也会被求值，
            // 在此抛异常会让无密钥环境（如 build.yml 的 debug 构建）整个构建失败。
            // 真正的拦截放在下方 taskGraph.whenReady —— 仅当确实要构建 release 时才报错。
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        // miuix-nav 的 rememberNavBackStack / entry<> 按 JVM 21 编译，
        // 内联进本模块必须同目标，否则 “Cannot inline bytecode … 21 into … 17”。
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    lint {
        // NewApi/InlinedApi 提为 error：minSdk 提升到 26 后仍可能误用更高版本 API，
        // 这类问题 assembleDebug 不报错、只在老设备上崩，必须由 CI 的 lint 拦住。
        abortOnError = true
        error += listOf("NewApi", "InlinedApi")
        // 报告输出给 CI 上传为 artifact
        htmlReport = true
        textReport = true
        // 同时把文本报告打到 stdout，这样在 Actions 日志里能直接看到违规条目，
        // 不必下载 artifact（沙箱/受限网络下 artifact 常常拿不到）。
        textOutput = File("stdout")
    }
}

// ---- Release 签名凭据缺失时的拦截 ----
// 放在 taskGraph.whenReady（执行阶段前）而非 buildTypes 块内：
// 后者属配置阶段，assembleDebug 也会求值，会误伤无密钥的 debug 构建。
// 这里只在任务图里确实包含 release 打包任务时才失败，从而做到
// 「debug 构建照常、release 构建绝不静默回退 debug 签名」。
gradle.taskGraph.whenReady {
    if (hasReleaseSigning || allowUnsignedRelease) return@whenReady
    val releaseTask = allTasks.firstOrNull {
        it.project == project &&
            Regex("^(assemble|bundle|package)Release$").matches(it.name)
    } ?: return@whenReady
    throw GradleException(
        "Release 签名凭据缺失，拒绝以 debug 密钥打包 ${releaseTask.name}。\n" +
            "CI 请检查 RELEASE_KEYSTORE_PATH / RELEASE_KEYSTORE_PASSWORD / " +
            "RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD 四个 secrets 是否齐备；\n" +
            "本地仅做构建验证请加 -PallowUnsignedRelease=true。"
    )
}

// AGP 9 起 kotlinOptions DSL 已移除，改用 KGP 的 compilerOptions。
kotlin {
    // CI 仍可能是 setup-java 17；toolchain 让 Gradle 自行拉 JDK 21 来编译。
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    // 纯 JVM 单测：不需要模拟器，./gradlew testDebugUnitTest 秒级跑完。
    testImplementation(libs.junit)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // 二级页路由与预测性返回：纯 Android 模块用 -android 坐标（含 rememberNavSystemCornerRadius actual）。
    implementation(libs.miuix.nav.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.datastore.preferences)

    // Miuix（HyperOS 风格 Compose 组件库），版本对齐 skill 基线 v0.9.4-rc01。
    // 使用 common 坐标，Gradle Module Metadata 会自动解析到 android 变体。
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    // 图标库：仅 MIUIX 主题使用（MD3 主题继续用 material-icons-extended）。
    implementation(libs.miuix.icons)

    implementation(libs.material.kolor)

    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
