package com.ridecomp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/**
 * Standard layout for non-ride screens: themed background, then either a large screen title (top-level
 * screens) or a [TopBarBack] (secondary screens, when [onBack] is set), optional banner, then content.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    banner: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    backIcon: TopBarIcon = TopBarIcon.BACK,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(RideCompTheme.colors.bg)) {
        val headerModifier = Modifier
            .fillMaxWidth()
            .padding(
                start = RideCompDimens.screenPaddingHorizontal,
                end = RideCompDimens.screenPaddingHorizontal,
                top = RideCompDimens.screenPaddingTop,
                bottom = RideCompDimens.blockGap,
            )
        if (title != null && onBack != null) {
            TopBarBack(
                title = title,
                onBack = onBack,
                backLabel = backLabel ?: title,
                subtitle = subtitle,
                icon = backIcon,
                modifier = headerModifier,
            )
        } else if (title != null) {
            Text(
                text = title,
                modifier = headerModifier.semantics { heading() },
                style = RideCompTheme.typography.screenTitle,
                color = RideCompTheme.colors.textPrimary,
            )
        }
        banner?.invoke()
        content()
    }
}

@ThemePreviews
@Composable
private fun ScreenScaffoldPreview() {
    RideCompPreview {
        ScreenScaffold(title = "History") {
            Box(modifier = Modifier.weight(1f)) {
                EmptyState(message = "No rides yet")
            }
        }
    }
}
