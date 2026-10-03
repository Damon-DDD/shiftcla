import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    // 仅用于把 @Preview 离屏渲染成 PNG（本地校验用，可随时删掉）
    id("com.android.compose.screenshot") version "0.0.1-alpha16"
}

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
        // 只为"自己装到手机上试"用：复用本地 debug.keystore 签 release，
        // 好处是 release（关闭 Compose 的 debug 运行时开销）跑动画比 debug 顺。
        // 要上架的话请换成你自己的正式 keystore。
        create("localTest") {
            storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("localTest")
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
