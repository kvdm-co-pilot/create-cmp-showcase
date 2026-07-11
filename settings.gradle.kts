rootProject.name = "CMP-Showcase"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupByRegex(".*google.*")
                includeGroupByRegex(".*android.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Compose Hot Reload runs the desktop dev-client on the JetBrains Runtime; this resolver
    // lets Gradle auto-provision it (one-time download) when no local JBR is found.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // GitLive Firebase KMP
        maven("https://jitpack.io")
    }
}

include(":composeApp")
