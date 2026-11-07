// Root build: declares the plugins used across subprojects with `apply false`, so
// the Kotlin/Android Gradle plugins are loaded once at the root instead of
// independently in each subproject. Subprojects apply them themselves.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}
