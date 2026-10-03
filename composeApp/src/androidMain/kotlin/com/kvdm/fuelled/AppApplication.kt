package com.kvdm.fuelled

import android.app.Application
import com.kvdm.fuelled.data.local.AppDatabase
import com.kvdm.fuelled.data.local.appContext
import com.kvdm.fuelled.data.local.buildDatabase
import com.kvdm.fuelled.core.connectivity.NetworkMonitor
import com.kvdm.fuelled.core.updates.AppInstaller
import com.kvdm.fuelled.di.androidModule
import com.kvdm.fuelled.inspector.startInspector
import com.kvdm.fuelled.di.appModules
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
        configureFirebaseEmulators()
        startKoin {
            androidLogger()
            androidContext(this@AppApplication)
            modules(
                module {
                    single<AppDatabase> { buildDatabase() }
                    single { NetworkMonitor(androidContext()) }
                single { AppInstaller(androidContext()) }
                },
                androidModule,
                *appModules.toTypedArray()
            )
        }
    }

}
