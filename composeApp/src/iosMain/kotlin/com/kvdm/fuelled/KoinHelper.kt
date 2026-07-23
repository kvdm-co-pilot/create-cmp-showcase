package com.kvdm.fuelled

import com.kvdm.fuelled.core.connectivity.NetworkMonitor
import com.kvdm.fuelled.data.local.AppDatabase
import com.kvdm.fuelled.data.local.buildDatabase
import com.kvdm.fuelled.data.remote.FIREBASE_FUNCTIONS_REGION
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.storage.storage
import com.kvdm.fuelled.di.appModules
import org.koin.core.context.startKoin
import org.koin.dsl.module

// Debug/QA: point GitLive Firebase at the local emulators. The iOS simulator shares the host
// network, so 127.0.0.1 reaches the emulators directly. Requires FirebaseApp.configure() to
// have run first (done in iOSApp.swift AppDelegate).
private fun configureFirebaseEmulators() {
    val host = "127.0.0.1"
    runCatching {
        Firebase.auth.useEmulator(host, 9099)
        Firebase.firestore.useEmulator(host, 8080)
        Firebase.functions(FIREBASE_FUNCTIONS_REGION).useEmulator(host, 5001)
        Firebase.storage.useEmulator(host, 9199)
    }
}

fun initKoin() {
    configureFirebaseEmulators()
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
