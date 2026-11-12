package com.ridecomp.app.di

import org.koin.core.module.Module

/** Every Koin module of the app, wired once in the composition root (ARCH-02). */
val appModules: List<Module> = listOf(
    platformModule,
    dataModule,
)
