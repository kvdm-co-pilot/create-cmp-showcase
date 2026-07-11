package com.kvdm.cmpshowcase

import com.kvdm.cmpshowcase.core.connectivity.NetworkMonitor
import com.kvdm.cmpshowcase.data.local.AppDatabase
import com.kvdm.cmpshowcase.data.local.buildDatabase
import com.kvdm.cmpshowcase.di.appModules
import org.koin.core.context.startKoin
import org.koin.dsl.module


fun initKoin() {
    startKoin {
        modules(
            module {
                single<AppDatabase> { buildDatabase() }
                single { NetworkMonitor(null) }
            },
            *appModules.toTypedArray()
        )
    }
}
