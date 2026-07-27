package com.kvdm.fuelled.di

import com.kvdm.fuelled.domain.notification.ReminderScheduler
import com.kvdm.fuelled.notification.AndroidReminderScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

// Android-only DI bindings (platform services that need a Context, etc.).
val androidModule: Module = module {
    // PLAN-07. This is the ONLY platform that arms real reminders — desktop and iOS bind
    // NoOpReminderScheduler, which reports no capability rather than pretending, so the meal
    // times sheet tells the same truth everywhere.
    single<ReminderScheduler> { AndroidReminderScheduler(androidContext()) }
}
