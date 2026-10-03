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

// Written by `create-cmp add firebase`. AppApplication.onCreate() calls it, before startKoin.
//
// A debug build talks to the local Firebase emulator suite (the BuildConfig fields are set at the
// end of composeApp/build.gradle.kts; the ports are data/remote/FirebaseConfig.kt's). It has to run
// before any Firebase client is used, or useEmulator can no longer take effect.
//
// A failure here is NEVER swallowed. A build that asked for emulators and did not get them would
// carry on against the real project named by google-services.json — reading, writing and
// authenticating against production from a debug build. A PARTIAL failure is worse: auth
// redirected and Firestore not, so half the app talks to the emulator and half to production. So
// it throws and the app refuses to start. Keep it that way: no runCatching around these calls,
// and no catch that only logs.
internal fun configureFirebaseEmulators() {
    if (!BuildConfig.USE_FIREBASE_EMULATORS) return
    val host = BuildConfig.FIREBASE_EMULATOR_HOST
    try {
        Firebase.auth.useEmulator(host, FIREBASE_AUTH_EMULATOR_PORT)
        Firebase.firestore.useEmulator(host, FIREBASE_FIRESTORE_EMULATOR_PORT)
        Firebase.functions(FIREBASE_FUNCTIONS_REGION)
            .useEmulator(host, FIREBASE_FUNCTIONS_EMULATOR_PORT)
        Firebase.storage.useEmulator(host, FIREBASE_STORAGE_EMULATOR_PORT)
    } catch (cause: Throwable) {
        // `throw IllegalStateException(msg, cause)`, never `error(msg)`: error() takes no cause,
        // so the underlying stack — the only thing that says WHICH of the four calls failed and
        // why — would be dropped and the crash would read `Cause: null`.
        throw IllegalStateException(
            "Firebase emulator redirect to $host FAILED, and this build asked for emulators " +
                "(USE_FIREBASE_EMULATORS=true). Refusing to start: continuing would authenticate and " +
                "write against the real project in google-services.json. Usual cause: a Firebase client " +
                "was already used before this ran, so useEmulator can no longer take effect.",
            cause,
        )
    }
}
