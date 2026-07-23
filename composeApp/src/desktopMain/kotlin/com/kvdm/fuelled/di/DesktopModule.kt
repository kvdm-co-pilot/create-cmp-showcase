package com.kvdm.fuelled.di

import com.kvdm.fuelled.core.connectivity.NetworkMonitor
import com.kvdm.fuelled.data.local.AppDatabase
import com.kvdm.fuelled.data.local.buildDatabase
import org.koin.core.context.startKoin
import org.koin.dsl.module

// Desktop (JVM) DI bindings for the dev-client window.
//
// No Firebase here — the dev-client runs fully offline. The exemplar feature's data source
// (FoodRepositoryImpl, bound in appModules) is Room-backed via the AppDatabase bound below, so
// it seeds and serves the same catalog on every platform and nothing needs swapping out of the
// box. When you add a real remote-backed repository, bind its desktop fake in THIS module so the
// dev-client keeps working without a backend:
//
//   single<MyRepository> { InMemoryMyRepository() }   // shadows the remote binding on desktop
val desktopModule = module {
    single<AppDatabase> { buildDatabase() }
    single { NetworkMonitor(null) }
}

// Mirrors AppApplication (Android) / KoinHelper (iOS) — Koin start for the desktop entry point.
fun initDesktopKoin() {
    startKoin {
        modules(desktopModule, *appModules.toTypedArray())
    }
}
