package com.ridecomp.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.ridecomp.ui.R
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import com.ridecomp.ui.util.thenIf

/**
 * Reusable list row (settings entries, saved sensors, routes, activities): title, optional
 * subtitle, optional trailing content, chevron when clickable. Height ≥ 56 dp (UI-07).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RideCompDimens.touchTargetMin)
                .thenIf(onClick != null) { clickable(role = Role.Button) { onClick?.invoke() } }
                .padding(horizontal = RideCompDimens.spacingM, vertical = RideCompDimens.spacingS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = RideCompTheme.typography.body, color = RideCompTheme.colors.textPrimary)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = RideCompTheme.typography.dataLabel,
                        color = RideCompTheme.colors.textSecondary,
                    )
                }
            }
            trailing?.invoke()
            if (onClick != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    // Decorative: the whole row is the button and its title is the label.
                    contentDescription = null,
                    tint = RideCompTheme.colors.textSecondary,
                    modifier = Modifier.size(RideCompDimens.iconSize),
                )
            }
        }
        HorizontalDivider(thickness = RideCompDimens.dividerThickness, color = RideCompTheme.colors.divider)
    }
}

/** Group title above list rows (e.g. "Saved", "Found"); marked as a heading for TalkBack (UI-08). */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = RideCompDimens.screenPaddingHorizontal,
) {
    Text(
        text = text.uppercase(),
        style = RideCompTheme.typography.dataLabel,
        color = RideCompTheme.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = RideCompDimens.spacingS)
            .semantics { heading() },
    )
}

@ThemePreviews
@Composable
private fun SectionHeaderPreview() {
    RideCompPreview { SectionHeader(text = "Saved") }
}

@ThemePreviews
@Composable
private fun ListRowPreview() {
    RideCompPreview {
        ListRow(title = "Profile", subtitle = "Weight, wheel, HR max, FTP", onClick = {})
    }
}
