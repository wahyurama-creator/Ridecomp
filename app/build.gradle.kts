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
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
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

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
