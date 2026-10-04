package com.anti.theft.di

import com.anti.theft.core.navigation.AppNavViewModel
import com.anti.theft.domain.usecase.ResolvePermissionStatusUseCase
import com.anti.theft.domain.usecase.SyncProtectionUseCase
import com.anti.theft.domain.usecase.ToggleFeatureUseCase
import com.anti.theft.presentation.alarm.AlarmViewModel
import com.anti.theft.presentation.home.HomeViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeModule = module {
    factoryOf(::ToggleFeatureUseCase)
    factoryOf(::ResolvePermissionStatusUseCase)
    factoryOf(::SyncProtectionUseCase)
    viewModelOf(::HomeViewModel)
}

val alarmModule = module {
    viewModelOf(::AlarmViewModel)
}

val navigationModule = module {
    viewModelOf(::AppNavViewModel)
}
