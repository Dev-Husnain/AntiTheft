package com.anti.theft

import android.app.Application
import com.anti.theft.data.service.ProtectionNotifier
import com.anti.theft.di.appModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
        get<ProtectionNotifier>().createChannels()
    }
}
