import org.jetbrains.kotlin.gradle.dsl.JvmTarget
// ⚠️ 必须显式 import：Gradle Kotlin DSL 里裸写 `java.util.Properties` 会被解析成
// `java` 扩展（JavaPluginExtension），不是包名，报 "Unresolved reference: util"。
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    // 仅用于把 @Preview 离屏渲染成 PNG（本地校验用，可随时删掉）
    id("com.android.compose.screenshot") version "0.0.1-alpha16"
}

// ===========================================================================
// 签名配置
// ---------------------------------------------------------------------------
// 正式签名密钥**不在仓库里**（*.jks / keystore.properties 都已被 .gitignore 覆盖）。
// 两条读取路径，优先级：环境变量（CI） > keystore.properties（本机）：
//   · CI：GitHub Actions 把密钥 base64 解到临时文件，再用 KEYSTORE_FILE 等环境变量指过去
//   · 本机：仓库根目录的 keystore.properties（已 gitignore），指向仓库外的密钥文件
// 两者都没有 → 回退 debug keystore：保证 clone 下来的人不配密钥也能构建自测。
// ===========================================================================
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(propName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("KEYSTORE_FILE", "storeFile")
val releaseStorePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("KEY_PASSWORD", "keyPassword")
val hasReleaseSigning =
    listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

android {
    namespace = "com.shiftcla.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shiftcla.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // 回退用：本机 debug keystore。没配正式密钥时 release 也走它（仅供自测，
        // 这种包不能用于公开分发 —— 换密钥后用户无法覆盖安装）。
        create("localTest") {
            storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // minSdk 24 已支持 v2/v3 签名，用默认即可
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("localTest")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // minSdk 24 < 26，要用 java.time.LocalDate（日期切换体系）必须开脱糖
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    // 截图测试插件要求：gradle.properties 里开全局开关 + 模块里再声明一次
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

// APK 输出名去掉构建类型后缀：app-debug.apk → Shiftcla.apk。
// 只是改文件名，不动 applicationId，旧包仍可覆盖安装。
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)?.outputFileName =
                "Shiftcla.apk"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))

    // minSdk 24 上用 java.time 的脱糖运行时（配合 compileOptions.isCoreLibraryDesugaringEnabled）
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // AutoAwesome / CalendarMonth / WbSunny / Park 在这里；版本交给 BOM 管
    implementation("androidx.compose.material:material-icons-extended")

    // ViewModel + StateFlow 收集（viewModel() / collectAsState）
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    // ---- 网络 + JSON：调 DeepSeek 用 ----
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.ktor:ktor-client-core:3.3.0")
    // OkHttp 引擎：Android 上比 ktor-client-android 成熟，且自带连接池
    implementation("io.ktor:ktor-client-okhttp:3.3.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.0")

    // ---- 单元测试（验证容错解析，不需要联网）----
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    screenshotTestImplementation(platform("androidx.compose:compose-bom:2025.09.00"))
    screenshotTestImplementation("androidx.compose.ui:ui-tooling")
    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha16")
}
