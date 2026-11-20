package com.ridecomp.platform.theme

import android.content.Context
import android.os.StrictMode
import androidx.core.content.edit
import com.ridecomp.core.model.AppTheme

/**
 * A copy of the chosen theme that can be read before the first frame (T11.9, D-126), so a light theme does not
 * start dark until the settings table is read. The `settings` table stays the source: the app root copies
 * every value it reads from there into this cache.
 */
object ThemeCache {
    private const val PREFERENCES = "ridecomp_theme"
    private const val KEY_THEME = "theme"

    fun read(context: Context): AppTheme {
        // RULE-EXCEPTION: one tiny preference read while the first screen is created; the theme must be known
        // to draw it (StrictMode would flag the disk read, STB-01). Same as the app language (D-88).
        val policy = StrictMode.allowThreadDiskReads()
        val stored = try {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_THEME, null)
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
        return AppTheme.entries.firstOrNull { it.name == stored } ?: AppTheme.DEFAULT
    }

    /** Written in the background (`apply`); the in-memory value changes at once. */
    fun write(context: Context, theme: AppTheme) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putString(KEY_THEME, theme.name) }
    }
}
