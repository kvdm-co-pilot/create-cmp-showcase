package com.kvdm.cmpshowcase

import android.app.Application
import com.kvdm.cmpshowcase.data.local.AppDatabase
import com.kvdm.cmpshowcase.data.local.appContext
import com.kvdm.cmpshowcase.data.local.buildDatabase
import com.kvdm.cmpshowcase.core.connectivity.NetworkMonitor
import com.kvdm.cmpshowcase.di.androidModule
import com.kvdm.cmpshowcase.inspector.startInspector
import com.kvdm.cmpshowcase.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.dsl.module

class AppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Debug builds only: the androidRelease twin is a no-op (see inspector/InspectorInit.kt).
        // Must run before any Activity so the Compose root registry catches every root.
        startInspector()
        appContext = this
        startKoin {
            androidLogger()
            androidContext(this@AppApplication)
            modules(
                module {
                    single<AppDatabase> { buildDatabase() }
                    single { NetworkMonitor(androidContext()) }
                },
                androidModule,
                *appModules.toTypedArray()
            )
        }
    }

}
