package com.anti.theft.di

import com.anti.theft.core.dispatchers.AppDispatchers
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

val appModule = module {
    single { AppDispatchers(main = Dispatchers.Main, default = Dispatchers.Default, io = Dispatchers.IO) }
}
