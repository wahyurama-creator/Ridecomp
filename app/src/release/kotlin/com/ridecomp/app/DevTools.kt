package com.ridecomp.app

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.koin.core.module.Module

/** Release variant: no diagnostics, no developer menu, no simulated sources (SEC-BLD-01, SEC-BLD-04). */
object DevTools {
    const val IS_DEVELOPER_MENU_AVAILABLE = false

    fun install() = Unit

    /** No screen can be opened from outside in a release build (SEC-BLD-04). */
    @Suppress("UNUSED_PARAMETER")
    fun screenRoute(intent: Intent?): String? = null

    val koinOverrides: List<Module> = emptyList()

    @Composable
    @Suppress("UNUSED_PARAMETER")
    fun DeveloperScreen(modifier: Modifier = Modifier) = Unit
}
