// Top-level build file. Plugins are declared here (apply false) so every module uses the same versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Declared so that one Kotlin Gradle Plugin version (libs.versions.kotlin) is used by AGP's built-in Kotlin.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
