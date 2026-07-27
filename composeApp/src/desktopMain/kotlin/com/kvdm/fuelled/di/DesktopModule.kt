package com.kvdm.fuelled.di

import com.kvdm.fuelled.core.connectivity.NetworkMonitor
import com.kvdm.fuelled.data.local.AppDatabase
import com.kvdm.fuelled.data.local.buildDatabase
import com.kvdm.fuelled.domain.notification.NoOpReminderScheduler
import com.kvdm.fuelled.domain.notification.ReminderScheduler
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
    // PLAN-07: the dev-client window has nothing to ring, and NoOp reports no capability rather
    // than pretending — so the times sheet here says exactly what it would say on a phone with
    // notifications denied, instead of quietly showing a schedule that will never fire.
    single<ReminderScheduler> { NoOpReminderScheduler() }
}

// Mirrors AppApplication (Android) / KoinHelper (iOS) — Koin start for the desktop entry point.
fun initDesktopKoin() {
    startKoin {
        modules(desktopModule, *appModules.toTypedArray())
    }
}
