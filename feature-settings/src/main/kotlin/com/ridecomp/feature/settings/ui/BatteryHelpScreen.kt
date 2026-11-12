package com.ridecomp.feature.settings.ui

import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.ridecomp.feature.settings.R
import com.ridecomp.ui.component.BigButton
import com.ridecomp.ui.component.BigButtonStyle
import com.ridecomp.ui.component.ScreenScaffold
import com.ridecomp.ui.preview.RideCompPreview
import com.ridecomp.ui.preview.ThemePreviews
import com.ridecomp.ui.theme.RideCompDimens
import com.ridecomp.ui.theme.RideCompTheme
import java.util.Locale

/** Help page base (opened in the browser; the app itself makes no request). */
private const val DONT_KILL_MY_APP_URL = "https://dontkillmyapp.com/"

/**
 * Phone makers known to stop background apps (03-ARCHITECTURE §7), matched on `Build.MANUFACTURER`.
 * [slug] is the page on dontkillmyapp.com.
 */
enum class PhoneBrand(private val keys: List<String>, val slug: String, @param:StringRes val stepsRes: Int) {
    XIAOMI(listOf("xiaomi", "redmi", "poco"), "xiaomi", R.string.battery_help_xiaomi),
    OPPO(listOf("oppo", "realme", "oneplus"), "oppo", R.string.battery_help_oppo),
    VIVO(listOf("vivo", "iqoo"), "vivo", R.string.battery_help_vivo),
    TRANSSION(listOf("infinix", "tecno", "itel"), "infinix", R.string.battery_help_transsion),
    SAMSUNG(listOf("samsung"), "samsung", R.string.battery_help_samsung),
    OTHER(emptyList(), "", R.string.battery_help_generic),
    ;

    companion object {
        fun of(manufacturer: String): PhoneBrand {
            val name = manufacturer.lowercase(Locale.ROOT)
            return entries.firstOrNull { brand -> brand.keys.any { name.contains(it) } } ?: OTHER
        }
    }
}

/** "My phone kills the app": steps for this phone's brand + link (02-DESIGN §3.10). */
@Composable
fun BatteryHelpRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val brand = PhoneBrand.of(Build.MANUFACTURER)
    BatteryHelpScreen(
        manufacturer = Build.MANUFACTURER,
        brand = brand,
        onOpenHelp = {
            val intent = Intent(Intent.ACTION_VIEW, (DONT_KILL_MY_APP_URL + brand.slug).toUri())
            context.startActivity(intent)
        },
        modifier = modifier,
    )
}

@Composable
fun BatteryHelpScreen(manufacturer: String, brand: PhoneBrand, onOpenHelp: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(modifier = modifier, title = stringResource(R.string.battery_help_title)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(RideCompDimens.spacingM),
            verticalArrangement = Arrangement.spacedBy(RideCompDimens.spacingM),
        ) {
            Text(
                text = stringResource(R.string.battery_help_intro, manufacturer),
                style = RideCompTheme.typography.body,
                color = RideCompTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(brand.stepsRes),
                style = RideCompTheme.typography.body,
                color = RideCompTheme.colors.textPrimary,
            )
            BigButton(
                text = stringResource(R.string.battery_help_more),
                onClick = onOpenHelp,
                style = BigButtonStyle.SECONDARY,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun BatteryHelpScreenPreview() {
    RideCompPreview { BatteryHelpScreen(manufacturer = "Xiaomi", brand = PhoneBrand.XIAOMI, onOpenHelp = {}) }
}
