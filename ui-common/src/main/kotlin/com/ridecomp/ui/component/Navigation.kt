package com.ridecomp.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ridecomp.ui.R
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Icon of the [TopBarBack] button: back arrow, or a cross where leaving cancels (pre-ride). */
enum class TopBarIcon(@param:DrawableRes val iconRes: Int) {
    BACK(R.drawable.ic_arrow_back),
    CLOSE(R.drawable.ic_close),
}

/** TopBarBack (13-UI-SCREENS §2): square back button on `surface` + title (and optional subtitle). */
@Composable
fun TopBarBack(
    title: String,
    onBack: () -> Unit,
    backLabel: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: TopBarIcon = TopBarIcon.BACK,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingSm),
    ) {
        IconSquareButton(
            iconRes = icon.iconRes,
            contentDescription = backLabel,
            onClick = onBack,
            tint = RideCompTheme.colors.textPrimary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = RideCompTheme.typography.title,
                color = RideCompTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = RideCompTheme.typography.caption,
                    color = RideCompTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** One destination of [RideCompBottomNav]. */
@Immutable
data class BottomNavItem(val label: String, @param:DrawableRes val iconRes: Int, val isSelected: Boolean)

/** BottomNav (13-UI-SCREENS §2): five tabs on `surfaceAlt`; the active one in `accent` and bold. */
@Composable
fun RideCompBottomNav(items: ImmutableList<BottomNavItem>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = RideCompTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.surfaceAlt)) {
        HorizontalDivider(thickness = RideCompDimens.dividerThickness, color = colors.divider)
        Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup()) {
            items.forEachIndexed { index, item ->
                val tint = if (item.isSelected) colors.accent else colors.textSecondary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RideCompDimens.touchTargetMin)
                        .selectable(selected = item.isSelected, role = Role.Tab) { onSelect(index) }
                        .padding(vertical = RideCompDimens.spacingS),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
                ) {
                    // Decorative: the label below names the tab.
                    Icon(painterResource(item.iconRes), contentDescription = null, tint = tint)
                    Text(
                        text = item.label,
                        style = RideCompTheme.typography.navLabel.copy(
                            fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** SegmentedTabs (13-UI-SCREENS §2): `surface` track, the active tab white with dark text. */
@Composable
fun SegmentedTabs(
    tabs: ImmutableList<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RideCompTheme.colors
    val shape = RoundedCornerShape(RideCompDimens.cornerRadiusSmall)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .padding(RideCompDimens.spacingXs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingXs),
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = RideCompDimens.chipHeight)
                    .clip(shape)
                    .background(if (isSelected) colors.textPrimary else colors.surface)
                    .selectable(selected = isSelected, role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = RideCompTheme.typography.buttonSmall,
                    color = if (isSelected) colors.bg else colors.textTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** PageIndicator (13-UI-SCREENS §2): the active page is a wide pill, the others small dots. */
@Composable
fun PageIndicator(count: Int, current: Int, description: String, modifier: Modifier = Modifier) {
    val colors = RideCompTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = RideCompDimens.spacingS)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            val isActive = index == current
            Box(
                modifier = Modifier
                    .height(RideCompDimens.pageDotSize)
                    .width(if (isActive) RideCompDimens.pageDotActiveWidth else RideCompDimens.pageDotSize)
                    .clip(RoundedCornerShape(RideCompDimens.pageDotSize))
                    .background(if (isActive) colors.textPrimary else colors.indicatorInactive),
            )
        }
    }
}

/** OnboardingProgress (13-UI-SCREENS §2): one 4 dp bar per step, filled up to [step], plus "LANGKAH n DARI m". */
@Composable
fun OnboardingProgress(step: Int, total: Int, label: String, modifier: Modifier = Modifier) {
    val colors = RideCompTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
        Row(horizontalArrangement = Arrangement.spacedBy(RideCompDimens.spacingS)) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(RideCompDimens.onboardingProgressHeight)
                        .clip(RoundedCornerShape(RideCompDimens.onboardingProgressHeight))
                        .background(if (index < step) colors.accent else colors.divider),
                )
            }
        }
        Text(text = label, style = RideCompTheme.typography.dataLabel, color = colors.textSecondary)
    }
}

/** Clickable text in the accent color (e.g. "Ganti", "Buka pengaturan baterai"), ≥ 56 dp tall (UI-07). */
@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = RideCompDimens.touchTargetMin)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = RideCompTheme.typography.bodyStrong, color = RideCompTheme.colors.accent)
    }
}

@ThemePreviews
@Composable
private fun NavigationPreview() {
    RideCompPreview {
        Column(verticalArrangement = Arrangement.spacedBy(RideCompDimens.blockGap)) {
            TopBarBack(title = "Sensor", subtitle = "2 tersimpan", onBack = {}, backLabel = "Kembali")
            OnboardingProgress(step = 2, total = 3, label = "LANGKAH 2 DARI 3")
            SegmentedTabs(persistentListOf("Aktivitas", "Rekor", "Segmen", "Latihan"), selectedIndex = 0, onSelect = {})
            PageIndicator(count = 4, current = 0, description = "Halaman 1 dari 4")
            Box(modifier = Modifier.size(RideCompDimens.spacingXs))
            RideCompBottomNav(
                items = persistentListOf(
                    BottomNavItem("Ride", R.drawable.ic_nav_ride, isSelected = true),
                    BottomNavItem("Riwayat", R.drawable.ic_nav_history, isSelected = false),
                    BottomNavItem("Rute", R.drawable.ic_nav_routes, isSelected = false),
                    BottomNavItem("Sensor", R.drawable.ic_nav_sensors, isSelected = false),
                    BottomNavItem("Setelan", R.drawable.ic_nav_settings, isSelected = false),
                ),
                onSelect = {},
            )
        }
    }
}
