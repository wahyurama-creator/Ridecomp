package com.ridecomp.core.usecase

import com.ridecomp.core.model.AppLanguage
import com.ridecomp.core.source.AppLanguageStore

public class GetAppLanguageUseCase(private val store: AppLanguageStore) {
    public operator fun invoke(): AppLanguage = store.current()
}

public class SetAppLanguageUseCase(private val store: AppLanguageStore) {
    public operator fun invoke(language: AppLanguage) {
        if (store.current() != language) store.set(language)
    }
}
