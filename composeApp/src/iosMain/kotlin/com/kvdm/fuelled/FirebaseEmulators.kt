package com.kvdm.fuelled

import com.kvdm.fuelled.data.remote.FIREBASE_AUTH_EMULATOR_PORT
import com.kvdm.fuelled.data.remote.FIREBASE_FIRESTORE_EMULATOR_PORT
import com.kvdm.fuelled.data.remote.FIREBASE_FUNCTIONS_EMULATOR_PORT
import com.kvdm.fuelled.data.remote.FIREBASE_FUNCTIONS_REGION
import com.kvdm.fuelled.data.remote.FIREBASE_STORAGE_EMULATOR_PORT
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.storage.storage
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

// Written by `create-cmp add firebase`. initKoin() (KoinHelper.kt) calls it, before startKoin.
//
// A debug binary talks to the local Firebase emulator suite. The iOS simulator shares the host
// network, so 127.0.0.1 reaches the suite directly; the ports are data/remote/FirebaseConfig.kt's.
// FirebaseApp.configure() must have run first — iOSApp.swift's AppDelegate calls it before
// doInitKoin().
//
// Kotlin/Native has no BuildConfig, so the gate below is what keeps a RELEASE build from pointing
// every Firebase call at a loopback address that is not there. And a failure is NEVER swallowed,
// for the reason the Android twin (androidMain/FirebaseEmulators.kt) gives: a debug build that
// asked for emulators and did not get them would carry on against the real project in
// GoogleService-Info.plist.
@OptIn(ExperimentalNativeApi::class)
internal fun configureFirebaseEmulators() {
    // `Platform.isDebugBinary` is the Kotlin/Native equivalent of the Android flag: true for a
    // debug binary, false for the release one an adopter ships.
    if (!Platform.isDebugBinary) return
    val host = "127.0.0.1"
    try {
        Firebase.auth.useEmulator(host, FIREBASE_AUTH_EMULATOR_PORT)
        Firebase.firestore.useEmulator(host, FIREBASE_FIRESTORE_EMULATOR_PORT)
        Firebase.functions(FIREBASE_FUNCTIONS_REGION).useEmulator(host, FIREBASE_FUNCTIONS_EMULATOR_PORT)
        Firebase.storage.useEmulator(host, FIREBASE_STORAGE_EMULATOR_PORT)
    } catch (cause: Throwable) {
        throw IllegalStateException(
            "Firebase emulator redirect to $host FAILED in a debug build. Refusing to start: " +
                "continuing would authenticate and write against the real project in " +
                "GoogleService-Info.plist. Check that FirebaseApp.configure() ran first (iOSApp.swift) " +
                "and that no Firebase client was used before initKoin().",
            cause,
        )
    }
}
