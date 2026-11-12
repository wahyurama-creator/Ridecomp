package com.ridecomp.app.di

import com.ridecomp.data.db.RideCompDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Database and DAOs. Repository implementations are added per feature (ARCH-03). */
val dataModule = module {
    single { RideCompDatabase.build(androidContext()) }
    single { get<RideCompDatabase>().activityDao() }
    single { get<RideCompDatabase>().trackPointDao() }
    single { get<RideCompDatabase>().eventDao() }
    single { get<RideCompDatabase>().lapDao() }
    single { get<RideCompDatabase>().profileDao() }
    single { get<RideCompDatabase>().settingsDao() }
}
