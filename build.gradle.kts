// Toolchain mirrors what is already cached and proven on this machine:
// Gradle 8.9 + AGP 8.7.3 + Kotlin 2.1.0.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
