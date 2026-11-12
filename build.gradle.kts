import com.android.build.api.dsl.CommonExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

// Top-level build file. Plugins are declared here (apply false) so every module uses the same versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Declared so that one Kotlin Gradle Plugin version (libs.versions.kotlin) is used by AGP's built-in Kotlin.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dependency.check)
    alias(libs.plugins.license.report) apply false
}

// ---------------------------------------------------------------------------------------------
// Supply-chain security (T0.13, 11-SECURITY-RULES §A).
// ---------------------------------------------------------------------------------------------

/** CVSS score from which a vulnerability is High/Critical and blocks the build (SUP-06). */
val cveBlockingCvssScore = 7.0f

// SUP-03: lock every resolved configuration (lockfiles are committed). Regenerate consciously with
// `./gradlew <tasks> --write-locks` whenever a dependency changes.
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}
buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

// SUP-06: `./gradlew dependencyCheckAggregate`. Set NVD_API_KEY in the environment (never commit it)
// to download the NVD database quickly.
dependencyCheck {
    failBuildOnCVSS = cveBlockingCvssScore
    formats = listOf("HTML", "JSON")
    skipTestGroups = true
    nvd {
        providers.environmentVariable("NVD_API_KEY").orNull?.let { apiKey = it }
    }
    analyzers {
        assemblyEnabled = false
        nodePackage {
            enabled = false
        }
        nodeAudit {
            enabled = false
        }
        retirejs {
            enabled = false
        }
        ossIndex {
            enabled = false
        }
    }
}

// SUP-07: `./gradlew generateLicenseReport` -> <module>/build/reports/dependency-license/ for every module.
// Only what ships in the APK: release runtime of Android modules, runtime of the JVM :core module.
subprojects {
    apply(plugin = "com.github.jk1.dependency-license-report")
    extensions.configure<com.github.jk1.license.LicenseReportExtension> {
        configurations = arrayOf("releaseRuntimeClasspath", "runtimeClasspath")
        renderers = arrayOf(com.github.jk1.license.render.InventoryHtmlReportRenderer())
    }
}

val detektKotlinVersion = libs.versions.detektKotlin.get()
val jvmTargetVersion = libs.versions.jvmTarget.get()
val compileSdkLevel = libs.versions.compileSdk.get().toInt()
val minSdkLevel = libs.versions.minSdk.get().toInt()
val androidTestRunner = "androidx.test.runner.AndroidJUnitRunner"

// ---------------------------------------------------------------------------------------------
// Shared Android configuration for every Android module (one place for SDK/JVM levels).
// ---------------------------------------------------------------------------------------------
subprojects {
    plugins.withId("com.android.base") {
        extensions.configure<CommonExtension>("android") {
            compileSdk = compileSdkLevel
            defaultConfig.minSdk = minSdkLevel
            defaultConfig.testInstrumentationRunner = androidTestRunner
            compileOptions.sourceCompatibility = JavaVersion.toVersion(jvmTargetVersion)
            compileOptions.targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)
        }
    }
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = jvmTargetVersion
    }
    // KT-01: zero compiler warnings.
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions.allWarningsAsErrors.set(true)
    }
}

// ---------------------------------------------------------------------------------------------
// Code quality (T0.7, KT-01): ktlint + detekt on every module, Android Lint with zero warnings.
// ---------------------------------------------------------------------------------------------
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<KtlintExtension> {
        ignoreFailures.set(false)
        filter {
            exclude { it.file.path.contains("${File.separator}build${File.separator}") }
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        val moduleConfig = rootProject.file("config/detekt/detekt-${moduleKind(path)}.yml")
        config.setFrom(listOfNotNull(rootProject.file("config/detekt/detekt.yml"), moduleConfig.takeIf { it.exists() }))
    }

    // detekt 1.23.x must run with the Kotlin compiler it was built with, not the project's Kotlin version.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(detektKotlinVersion)
        }
    }

    plugins.withId("com.android.base") {
        extensions.configure<CommonExtension>("android") {
            lint.apply {
                abortOnError = true
                warningsAsErrors = true
                checkReleaseBuilds = true
                // Dependency freshness is reviewed at every Phase Gate (SUP-10), not on every build.
                disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
                // Lint checks bundled inside AndroidX libraries that are incompatible with this lint version are
                // skipped by lint itself; this only silences that notice (it is not a finding in our code).
                disable += "ObsoleteLintCustomCheck"
                // Security category is always fatal (11-SECURITY-RULES §A.2).
                fatal += setOf(
                    "HardcodedDebugMode", "ExportedService", "ExportedReceiver", "ExportedContentProvider",
                    "UnprotectedSMSBroadcastReceiver", "UnsafeProtectedBroadcastReceiver", "AllowBackup",
                    "SetJavaScriptEnabled", "AddJavascriptInterface", "TrustAllX509TrustManager",
                    "BadHostnameVerifier", "SSLCertificateSocketFactoryGetInsecure", "WorldReadableFiles",
                    "WorldWriteableFiles", "UnspecifiedImmutableFlag", "InsecureBaseConfiguration",
                    "PackagedPrivateKey", "SecureRandom", "TrulyRandom", "UnsafeImplicitIntentLaunch",
                    "MutableImplicitPendingIntent", "UnsafeIntentLaunch",
                )
            }
        }
    }
}

/** Returns which detekt override file applies to a module path. */
fun moduleKind(path: String): String = when {
    path == ":core" -> "core"
    path.startsWith(":feature-") -> "feature"
    else -> "android"
}

// ---------------------------------------------------------------------------------------------
// Architecture guard (T0.11, ARCH-01, ARCH-02). Evaluated at configuration time so any forbidden
// module dependency fails the build immediately, whatever task is run.
// ---------------------------------------------------------------------------------------------
val allowedModuleDependencies: Map<String, Set<String>> = mapOf(
    ":core" to emptySet(),
    ":data" to setOf(":core"),
    ":platform" to setOf(":core"),
    ":ui-common" to emptySet(),
)
val featureAllowedDependencies = setOf(":core", ":ui-common")

gradle.projectsEvaluated {
    val violations = subprojects.flatMap { module ->
        val allowed = when {
            module.path == ":app" -> null
            module.path.startsWith(":feature-") -> featureAllowedDependencies
            else -> allowedModuleDependencies[module.path] ?: emptySet()
        } ?: return@flatMap emptyList()
        module.configurations
            .flatMap { configuration -> configuration.dependencies.withType<ProjectDependency>() }
            .map { it.path }
            .filter { it != module.path && it !in allowed }
            .distinct()
            .map { "${module.path} must not depend on $it" }
    }
    if (violations.isNotEmpty()) {
        throw GradleException("Architecture rule violated (ARCH-01/ARCH-02):\n" + violations.joinToString("\n"))
    }
}
