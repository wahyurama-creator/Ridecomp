// :ui-common = design system shared by :app and :feature-* (theme, tokens, BigButton, DataField).
// Allowed by ARCH-02 so feature modules never depend on :app.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ridecomp.ui"
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.kotlinx.collections.immutable)
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.androidx.lifecycle.runtime.compose)
    // Offline map view (T3.3–T3.5, D-64); kept internal to :ui-common behind the RouteMap composable.
    implementation(libs.maplibre.android)
    // Already in the APK through MapLibre (same version): a client whose sockets are tagged (D-132).
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
