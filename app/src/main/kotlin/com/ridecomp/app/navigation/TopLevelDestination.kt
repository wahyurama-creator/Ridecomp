package com.ridecomp.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ridecomp.app.R
import com.ridecomp.ui.R as UiR

/**
 * The five bottom-navigation tabs (02-DESIGN §2, §3.11) — route names, labels and icons in one place.
 * Training (records, segments, trends) will be reached from History so the bar stays at five tabs.
 */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    HOME(route = "home", labelRes = R.string.nav_home, iconRes = UiR.drawable.ic_nav_ride),
    HISTORY(route = "history", labelRes = R.string.nav_history, iconRes = UiR.drawable.ic_nav_history),
    ROUTES(route = "routes", labelRes = R.string.nav_routes, iconRes = UiR.drawable.ic_nav_routes),
    SENSORS(route = "sensors", labelRes = R.string.nav_sensors, iconRes = UiR.drawable.ic_nav_sensors),
    SETTINGS(route = "settings", labelRes = R.string.nav_settings, iconRes = UiR.drawable.ic_nav_settings),
    ;

    companion object {
        val START: TopLevelDestination = HOME

        fun fromRoute(route: String?): TopLevelDestination? = entries.firstOrNull { it.route == route }
    }
}
