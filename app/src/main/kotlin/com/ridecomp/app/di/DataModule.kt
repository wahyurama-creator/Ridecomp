package com.ridecomp.app.di

import com.ridecomp.core.engine.sensors.SensorRepository
import com.ridecomp.core.repository.ActivityAnalysisRepository
import com.ridecomp.core.repository.ActivityProfileRepository
import com.ridecomp.core.repository.ActivityRepository
import com.ridecomp.core.repository.BestEffortRepository
import com.ridecomp.core.repository.IncidentRepository
import com.ridecomp.core.repository.ProfileRepository
import com.ridecomp.core.repository.RouteRepository
import com.ridecomp.core.repository.SafetyRepository
import com.ridecomp.core.repository.SettingsRepository
import com.ridecomp.core.usecase.BackupDatabase
import com.ridecomp.core.usecase.BackupStateRepository
import com.ridecomp.data.backup.BackupStateRepositoryImpl
import com.ridecomp.data.backup.RoomBackupDatabase
import com.ridecomp.data.db.RideCompDatabase
import com.ridecomp.data.repository.ActivityAnalysisRepositoryImpl
import com.ridecomp.data.repository.ActivityProfileRepositoryImpl
import com.ridecomp.data.repository.ActivityRepositoryImpl
import com.ridecomp.data.repository.BestEffortRepositoryImpl
import com.ridecomp.data.repository.IncidentRepositoryImpl
import com.ridecomp.data.repository.ProfileRepositoryImpl
import com.ridecomp.data.repository.RouteRepositoryImpl
import com.ridecomp.data.repository.SafetyRepositoryImpl
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
    single { get<RideCompDatabase>().activityProfileDao() }
    single { get<RideCompDatabase>().emergencyContactDao() }
    single { get<RideCompDatabase>().incidentDao() }
    single { get<RideCompDatabase>().routeDao() }
    single { get<RideCompDatabase>().bestEffortDao() }
    single<ActivityRepository> { ActivityRepositoryImpl(get(), get(), get(), get(), get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<SensorRepository> { SensorRepositoryImpl(get()) }
    single<ActivityProfileRepository> { ActivityProfileRepositoryImpl(get(), get()) }
    single<SafetyRepository> { SafetyRepositoryImpl(get(), get()) }
    single<IncidentRepository> { IncidentRepositoryImpl(get()) }
    single<RouteRepository> { RouteRepositoryImpl(get()) }
    single<BestEffortRepository> { BestEffortRepositoryImpl(get()) }
    single<ActivityAnalysisRepository> { ActivityAnalysisRepositoryImpl(get()) }
    single<BackupDatabase> { RoomBackupDatabase(androidContext(), get(), get(), get()) }
    single<BackupStateRepository> { BackupStateRepositoryImpl(get()) }
}
