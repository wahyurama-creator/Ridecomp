package com.ridecomp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/** Most fields in one row of the grid (02-DESIGN §3.4: 2 columns). */
private const val COLUMNS = 2

/**
 * Grid of data fields following 02-DESIGN §3.4: 1 → full, 2 → one column, otherwise two columns; with
 * [isFirstFullWidth] (or an odd count) the first field gets its own full-width row. Thin dividers come
 * from the background showing through the spacing.
 */
@Composable
fun DataGrid(
    count: Int,
    modifier: Modifier = Modifier,
    isFirstFullWidth: Boolean = false,
    cell: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    val rows = gridRows(count, isFirstFullWidth)
    Column(
        modifier = modifier.fillMaxSize().background(RideCompTheme.colors.divider),
        verticalArrangement = Arrangement.spacedBy(RideCompDimens.dividerThickness),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(RideCompDimens.dividerThickness),
            ) {
                row.forEach { index -> cell(index, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/** Indices per row. */
internal fun gridRows(count: Int, isFirstFullWidth: Boolean): List<List<Int>> = when {
    count <= 0 -> emptyList()
    // Two fields stack in one column (02-DESIGN §3.4).
    count == COLUMNS -> listOf(listOf(0), listOf(1))
    isFirstFullWidth || count % COLUMNS == 1 -> listOf(listOf(0)) + (1 until count).chunked(COLUMNS)
    else -> (0 until count).chunked(COLUMNS)
}

@ThemePreviews
@Composable
private fun DataGridPreview() {
    RideCompPreview {
        DataGrid(count = 6, isFirstFullWidth = true) { index, cellModifier ->
            DataField(label = "Field $index", value = "${index * 7}.4", modifier = cellModifier)
        }
    }
}
