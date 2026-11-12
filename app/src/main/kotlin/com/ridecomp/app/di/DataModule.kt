package com.ridecomp.app.di

import com.ridecomp.core.engine.sensors.SensorRepository
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.data.db.RideCompDatabase
import com.ridecomp.data.repository.ActivityRepositoryImpl
import com.ridecomp.data.repository.ProfileRepositoryImpl
import com.ridecomp.data.repository.SensorRepositoryImpl
import com.ridecomp.data.repository.SettingsRepositoryImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Database, DAOs and repository implementations (ARCH-03). */
val dataModule = module {
    single { RideCompDatabase.build(androidContext()) }
    single { get<RideCompDatabase>().activityDao() }
    single { get<RideCompDatabase>().trackPointDao() }
    single { get<RideCompDatabase>().eventDao() }
    single { get<RideCompDatabase>().lapDao() }
    single { get<RideCompDatabase>().profileDao() }
    single { get<RideCompDatabase>().settingsDao() }
    single { get<RideCompDatabase>().sensorDao() }
    single<ActivityRepository> { ActivityRepositoryImpl(get(), get(), get(), get(), get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<SensorRepository> { SensorRepositoryImpl(get()) }
}
