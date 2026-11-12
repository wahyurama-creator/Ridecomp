package com.ridecomp.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.theme.ThemeMode

/** Multi-preview: renders a composable in the dark (default) and light theme (UI-06). */
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class ThemePreviews

/** Wraps preview content in the RideComp theme (following the preview's night mode) and background. */
@Composable
fun RideCompPreview(content: @Composable () -> Unit) {
    RideCompTheme(themeMode = ThemeMode.SYSTEM) {
        Box(modifier = Modifier.background(RideCompTheme.colors.bg)) {
            content()
        }
    }
}
