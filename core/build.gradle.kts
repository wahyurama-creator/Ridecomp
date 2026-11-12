// :core = DOMAIN layer. Pure Kotlin/JVM: no android.*, androidx.*, Room, Koin or UI (ARCH-01).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    // Shared fakes (FakeClock, TestDispatcherProvider, ...) reusable by every module's tests (TEST-04).
    `java-test-fixtures`
}

// TEST-01: line coverage of :core must stay >= 80 %.
kover {
    currentProject {
        sources {
            // Test helpers are not production code.
            excludedSourceSets.add("testFixtures")
        }
    }
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testFixturesApi(libs.kotlinx.coroutines.test)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
