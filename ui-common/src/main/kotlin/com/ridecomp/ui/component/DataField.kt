package com.ridecomp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/** Visual state of a [DataField] (02-DESIGN §3.4). */
enum class DataFieldStatus {
    NORMAL,

    /** e.g. sensor disconnected: value shows [DataFieldDefaults.NO_VALUE], label in `warn` color. */
    WARNING,
}

object DataFieldDefaults {
    /** Shown when a value is unavailable — never show 0 for "unknown" (02-DESIGN §3.4). */
    const val NO_VALUE = "--"
    private const val CONTENT_DESCRIPTION_SEPARATOR = " "

    internal fun contentDescription(label: String, value: String, unit: String?): String =
        listOfNotNull(label, value, unit).joinToString(CONTENT_DESCRIPTION_SEPARATOR)
}

/**
 * One metric cell of the ride screen: small uppercase label + unit on top, big tabular value that
 * auto-fits the cell between 32 sp and 120 sp (02-DESIGN §3.4, §4.2).
 *
 * Callers pass already formatted text; each field should read only its own state (UI-04).
 */
@Composable
fun DataField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    status: DataFieldStatus = DataFieldStatus.NORMAL,
) {
    val colors = RideCompTheme.colors
    val typography = RideCompTheme.typography
    val labelColor = when (status) {
        DataFieldStatus.NORMAL -> colors.textSecondary
        DataFieldStatus.WARNING -> colors.warnText
    }
    val upperLabel = remember(label) { label.uppercase() }
    Column(
        modifier = modifier
            .background(colors.bg)
            .padding(RideCompDimens.spacingS)
            .semantics(mergeDescendants = true) {
                contentDescription = DataFieldDefaults.contentDescription(label, value, unit)
            },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(text = upperLabel, style = typography.dataLabel.copy(color = labelColor), maxLines = 1)
            if (unit != null) {
                BasicText(text = unit, style = typography.dataLabel.copy(color = colors.textSecondary), maxLines = 1)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BasicText(
                text = value,
                modifier = Modifier.fillMaxSize().padding(horizontal = RideCompDimens.spacingXs),
                style = typography.dataValue.copy(color = colors.textPrimary, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = RideCompDimens.dataValueMinSize,
                    maxFontSize = RideCompDimens.dataValueMaxSize,
                    stepSize = RideCompDimens.dataValueStep,
                ),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun DataFieldPreview() {
    RideCompPreview {
        DataField(label = "Speed", value = "32.4", unit = "km/h")
    }
}

@ThemePreviews
@Composable
private fun DataFieldWarningPreview() {
    RideCompPreview {
        DataField(label = "HR", value = DataFieldDefaults.NO_VALUE, unit = "bpm", status = DataFieldStatus.WARNING)
    }
}
