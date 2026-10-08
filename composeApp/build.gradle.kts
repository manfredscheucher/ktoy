plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)

    androidTarget()

    jvm()

    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        jsMain.dependencies {
            implementation(libs.kotlin.browser)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.bytefred.ktoy.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "ktoy"
            packageVersion = project.version.toString()
        }
    }
}

// Regenerate ExampleSources.kt from pytoy's canonical examples/ (pytoy is the
// source of truth). Gradle's input/output tracking runs this only when an
// example or the generator actually changed, so repeated builds skip it. If the
// pytoy checkout isn't present (e.g. ktoy cloned alone), the task is skipped and
// the committed ExampleSources.kt is used as-is.
val pytoyDir = file(providers.environmentVariable("PYTOY").orElse(
    rootProject.projectDir.parentFile.resolve("pytoy").absolutePath))
val genExamples = tasks.register<Exec>("genExampleSources") {
    val examplesDir = pytoyDir.resolve("examples")
    onlyIf { examplesDir.isDirectory }
    if (examplesDir.isDirectory) {
        inputs.dir(examplesDir)
    }
    inputs.file(rootProject.projectDir.resolve("scripts/gen_example_sources.py"))
    outputs.file(layout.projectDirectory.file(
        "src/commonMain/kotlin/org/bytefred/ktoy/core/ExampleSources.kt"))
    workingDir = rootProject.projectDir
    environment("PYTOY", pytoyDir.absolutePath)
    commandLine("python3", "scripts/gen_example_sources.py")
}

// Run the generator before any Kotlin source is compiled.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(genExamples)
}

android {
    namespace = "org.bytefred.ktoy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.bytefred.ktoy"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = project.version.toString()
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
