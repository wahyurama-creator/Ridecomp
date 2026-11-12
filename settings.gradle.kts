pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // RULE-EXCEPTION: SUP-01 — approved by the owner on 2026-11-12. The Gradle Plugin Portal is used
        // only for build-time quality/security plugins that are not published elsewhere, and is restricted
        // to their groups so it cannot serve any other artifact.
        gradlePluginPortal {
            content {
                includeGroupByRegex("org\\.jlleitschuh\\.gradle.*")
                includeGroupByRegex("org\\.owasp.*")
                includeGroupByRegex("com\\.github\\.jk1.*")
                includeGroupByRegex("io\\.gitlab\\.arturbosch\\.detekt.*")
                includeGroupByRegex("org\\.jetbrains\\.kotlinx\\.kover.*")
                includeGroupByRegex("org\\.gradle\\.toolchains.*")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RideComp"

include(":app")
include(":core")
include(":data")
include(":platform")
include(":ui-common")
include(":feature-ride")
include(":feature-history")
include(":feature-sensors")
include(":feature-routes")
include(":feature-settings")
include(":feature-training")
