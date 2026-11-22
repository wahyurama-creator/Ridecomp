import java.util.Properties

// :app = composition root: Application, navigation, DI (Koin) wiring, i18n resources.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ridecomp.app"
    defaultConfig {
        applicationId = "com.ridecomp.app"
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 102
        versionName = "0.10.2"
        // Strava (D-82): RideComp's public Client ID and its token server. Not secret (the Client ID is in
        // every authorize URL); the client secret lives only on the token server. Empty = Strava hidden.
        // The Strava redirect (SEC-AUTH-03) is claimed by the release app only, see the debug build type.
        manifestPlaceholders["stravaRedirectScheme"] = "ridecomp"
        buildConfigField(
            "String",
            "STRAVA_CLIENT_ID",
            "\"${providers.gradleProperty("ridecomp.strava.clientId").getOrElse("")}\"",
        )
        buildConfigField(
            "String",
            "STRAVA_TOKEN_SERVER_URL",
            "\"${providers.gradleProperty("ridecomp.strava.tokenServerUrl").getOrElse("")}\"",
        )
    }

    // SEC-BLD-02/03: the release key lives outside the repo in keystore.properties (git-ignored).
    // Without that file the release APK is built unsigned.
    val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }
    if (keystoreProperties != null) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        // T13.2 (D-144): the debug app installs next to the release app, so a phone with real rides can run
        // the comparison screenshots without uninstalling. It does not claim the Strava redirect, else the
        // release app's login would ask which app to open; Strava login is tested with the release build.
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["stravaRedirectScheme"] = "ridecomp-debug-unused"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 minify + resource shrinking (DEP-04, SEC-BLD-01).
            optimization {
                enable = true
            }
        }
    }

    // MapLibre (Phase 3) ships native libraries; many low-end phones are still 32-bit (03-ARCHITECTURE §2).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The language is chosen inside the app (D-88): every install must carry all languages, so a Play
    // bundle is not split by language.
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":platform"))
    implementation(project(":ui-common"))
    implementation(project(":feature-ride"))
    implementation(project(":feature-history"))
    implementation(project(":feature-sensors"))
    implementation(project(":feature-routes"))
    implementation(project(":feature-settings"))
    implementation(project(":feature-training"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // MEM-07: leak detection in debug builds only.
    debugImplementation(libs.leakcanary.android)
}
