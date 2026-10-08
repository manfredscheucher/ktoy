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
include(":androidApp")

// kotlin-common :core (the leveled Logger) is currently unused by ktoy and its
// commonMain doesn't compile for the js/browser target (kotlin.time.Instant on
// Kotlin 2.3), so we don't include it. Re-enable both lines if ktoy starts using
// the Logger — and then :core needs a js() target in the submodule:
// include(":core")
// project(":core").projectDir = file("kotlin-common/core")
