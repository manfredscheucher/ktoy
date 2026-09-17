rootProject.name = "ktoy"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")

// Shared, app-agnostic logic from the kotlin-common git submodule (checked out under
// ./kotlin-common/). We only need :core here — it provides the leveled Logger
// (Log/LogLevel, Logcat on Android, println elsewhere). It resolves libs.* against
// THIS build's version catalog.
include(":core")
project(":core").projectDir = file("kotlin-common/core")
