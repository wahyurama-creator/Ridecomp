package com.ridecomp.core.source

import com.ridecomp.core.model.AppLanguage

/** Where the chosen app language lives (Android per-app language, D-88). */
public interface AppLanguageStore {
    public fun current(): AppLanguage

    /** Applies [language]; the visible screens are recreated in it. */
    public fun set(language: AppLanguage)
}
