package com.anti.theft.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.anti.theft.core.dispatchers.AppDispatchers
import com.anti.theft.data.local.ProtectionStateHolder
import com.anti.theft.data.repo.DeviceCapabilitiesRepositoryImpl
import com.anti.theft.data.repo.PermissionRepositoryImpl
import com.anti.theft.data.repo.SettingsRepositoryImpl
import com.anti.theft.domain.repo.DeviceCapabilitiesRepository
import com.anti.theft.domain.repo.PermissionRepository
import com.anti.theft.domain.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { AppDispatchers(main = Dispatchers.Main, default = Dispatchers.Default, io = Dispatchers.IO) }
    single {
        PreferenceDataStoreFactory.create(
            produceFile = { androidContext().preferencesDataStoreFile("settings") },
        )
    }
    single { ProtectionStateHolder() }

    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<PermissionRepository> { PermissionRepositoryImpl(androidContext()) }
    single<DeviceCapabilitiesRepository> { DeviceCapabilitiesRepositoryImpl(androidContext()) }
}
