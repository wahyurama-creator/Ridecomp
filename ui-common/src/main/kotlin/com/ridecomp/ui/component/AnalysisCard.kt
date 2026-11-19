package com.ridecomp.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ridecomp.ui.R
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One line of an [AnalysisCard]'s number list: label left, value right; [valueColor] highlights it. */
@Immutable
data class AnalysisRow(val label: String, val value: String, val valueColor: Color? = null)

/** Texts of an [AnalysisCard]: [infoLabel] is read for the ⓘ button, [info] is what it opens. */
@Immutable
data class AnalysisCardTexts(val title: String, val info: String, val infoLabel: String)

/**
 * A section of the ride analysis (T11.2, 14-PHASE11-PLAN): title with an ⓘ that opens a short explanation
 * in place, the [chart], then the numbers as label/value rows, as on printed ride summaries.
 */
@Composable
fun AnalysisCard(
    texts: AnalysisCardTexts,
    rows: ImmutableList<AnalysisRow>,
    modifier: Modifier = Modifier,
    /** Below the numbers, e.g. time in zones (T11.5). */
    footer: @Composable () -> Unit = {},
    chart: @Composable () -> Unit = {},
) {
    val colors = RideCompTheme.colors
    var isInfoOpen by rememberSaveable(texts.title) { mutableStateOf(false) }
    RideCompCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = texts.title,
                style = RideCompTheme.typography.cardTitle,
                color = colors.textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconButton(
                onClick = { isInfoOpen = !isInfoOpen },
                modifier = Modifier.size(RideCompDimens.touchTargetMin),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = texts.infoLabel,
                    tint = if (isInfoOpen) colors.accent else colors.textSecondary,
                )
            }
        }
        if (isInfoOpen) {
            Text(text = texts.info, style = RideCompTheme.typography.body, color = colors.textSecondary)
        }
        chart()
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = colors.divider, thickness = RideCompDimens.dividerThickness)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = RideCompDimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    style = RideCompTheme.typography.body,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.value,
                    style = RideCompTheme.typography.bodyStrong,
                    color = row.valueColor ?: colors.textPrimary,
                )
            }
        }
        footer()
    }
}

@ThemePreviews
@Composable
private fun AnalysisCardPreview() {
    RideCompPreview {
        AnalysisCard(
            texts = AnalysisCardTexts(
                title = "Speed",
                info = "Average while moving; stops are left out.",
                infoLabel = "About speed",
            ),
            rows = persistentListOf(
                AnalysisRow("Average", "20.9 km/h"),
                AnalysisRow("Max", "50.4 km/h"),
            ),
        )
    }
}
