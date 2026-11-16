package com.ridecomp.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.ridecomp.ui.R
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme

/** Decorative icon on a tinted rounded square, leading a list row or a tip (the row text is the label). */
@Composable
fun IconTile(@DrawableRes iconRes: Int, modifier: Modifier = Modifier, tint: Color = RideCompTheme.colors.accent) {
    Box(
        modifier = modifier
            .size(RideCompDimens.iconTileSize)
            .clip(RoundedCornerShape(RideCompDimens.cornerRadiusSmall))
            .background(tint.copy(alpha = TILE_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(RideCompDimens.iconSize),
        )
    }
}

private const val TILE_ALPHA = 0.16f

@ThemePreviews
@Composable
private fun IconTilePreview() {
    RideCompPreview { IconTile(R.drawable.ic_download) }
}
