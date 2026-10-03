plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    // kotlinx.serialization 编译器插件，版本必须跟 Kotlin 本体一致
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
}
