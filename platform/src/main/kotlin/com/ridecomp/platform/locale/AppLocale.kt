package com.ridecomp.platform.locale

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.StrictMode
import androidx.core.content.edit
import com.ridecomp.core.model.AppLanguage
import com.ridecomp.core.source.AppLanguageStore
import java.util.Locale

/**
 * The app language without extra libraries (D-88). Android 13+ keeps it as the per-app language
 * ([LocaleManager]), which also shows in the phone's settings and recreates the screens. Android 8–12 keep
 * it in a small preference that [wrap] applies to every context (activity, service, application).
 */
class AndroidAppLanguageStore(private val context: Context) : AppLanguageStore {
    override fun current(): AppLanguage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        AppLanguage.fromTag(locales.takeUnless { it.isEmpty }?.get(0)?.language?.toBcp47())
    } else {
        AppLanguage.fromTag(AppLocale.storedTag(context))
    }

    override fun set(language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
            context.getSystemService(LocaleManager::class.java).applicationLocales = locales
        } else {
            AppLocale.store(context, language.tag)
        }
    }

    /** Android still reports Indonesian as the legacy "in". */
    private fun String.toBcp47(): String = if (this == LEGACY_INDONESIAN) AppLanguage.INDONESIAN.tag.orEmpty() else this

    private companion object {
        const val LEGACY_INDONESIAN = "in"
    }
}

/** Applies the stored language to a context on Android 8–12 (from `attachBaseContext`). */
object AppLocale {
    private const val PREFERENCES = "ridecomp_language"
    private const val KEY_TAG = "tag"

    /** True when the screen must be recreated by the app after a change (Android 13+ does it itself). */
    val isRecreateNeeded: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    fun wrap(base: Context): Context {
        val tag = if (isRecreateNeeded) storedTag(base) else null
        return tag?.let { localized(base, Locale.forLanguageTag(it)) } ?: base
    }

    // Language splits are off in the app bundle (app/build.gradle.kts), so every language is installed.
    @SuppressLint("AppBundleLocaleChanges")
    private fun localized(base: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }

    internal fun storedTag(context: Context): String? {
        // RULE-EXCEPTION: one tiny preference read while the first screen is created, before anything else
        // can run; the language must be known to draw it (StrictMode would flag the disk read, STB-01).
        val policy = StrictMode.allowThreadDiskReads()
        return try {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_TAG, null)
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
    }

    internal fun store(context: Context, tag: String?) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit(commit = true) {
            if (tag == null) remove(KEY_TAG) else putString(KEY_TAG, tag)
        }
    }
}
