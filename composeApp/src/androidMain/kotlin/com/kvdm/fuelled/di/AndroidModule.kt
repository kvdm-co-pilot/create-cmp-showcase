package com.kvdm.fuelled.di

import com.kvdm.fuelled.core.updates.AppInstaller
import com.kvdm.fuelled.core.updates.InstallCapability
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

    // UPD-06/UPD-08. Android is the ONLY platform that can install an APK, and this binding is
    // what makes the update surface exist here at all.
    //
    // Bound as InstallCapability, the INTERFACE the domain depends on — not as the concrete
    // AppInstaller. CheckForUpdateUseCase and UpdateViewModel take the interface (so commonTest
    // can substitute a fake, which an `expect class` can never be); only DI knows the actual.
    //
    // Its absence here was a crash, not a degradation: the desktop module bound AppInstaller
    // and Android bound nothing, so opening Settings → Updates threw
    // NoDefinitionFoundException the moment Koin built the ViewModel. Every desktop tier passed
    // — unit tests inject fakes, the golden renders the stateless screen with no VM, and no e2e
    // flow visited the surface — so only launching it on a device found it (2026-08-27).
    single<InstallCapability> { AppInstaller(androidContext()) }
}
