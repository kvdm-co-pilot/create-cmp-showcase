package com.kvdm.fuelled

import com.kvdm.fuelled.core.connectivity.NetworkMonitor
import com.kvdm.fuelled.core.updates.AppInstaller
import com.kvdm.fuelled.data.local.AppDatabase
import com.kvdm.fuelled.data.local.buildDatabase
import com.kvdm.fuelled.di.appModules
import com.kvdm.fuelled.domain.notification.NoOpReminderScheduler
import com.kvdm.fuelled.domain.notification.ReminderScheduler
import org.koin.core.context.startKoin
import org.koin.dsl.module


fun initKoin() {
    configureFirebaseEmulators()
    startKoin {
        modules(
            module {
                single<AppDatabase> { buildDatabase() }
                single { NetworkMonitor(null) }
                single { AppInstaller(null) }
                // PLAN-07 / brief decision 9: iOS reminders are deliberately NOT promised by
                // this contract. NoOp reports no capability rather than pretending, so the
                // times sheet says reminders are off here instead of implying they work.
                single<ReminderScheduler> { NoOpReminderScheduler() }
            },
            *appModules.toTypedArray()
        )
    }
}
