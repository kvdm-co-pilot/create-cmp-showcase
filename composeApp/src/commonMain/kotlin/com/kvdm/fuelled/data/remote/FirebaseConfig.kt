package com.kvdm.fuelled.data.remote

// Written by `create-cmp add firebase`.
//
// Region for Cloud Functions / callables. Keep schedulers, callables and Firestore in the SAME
// region — cross-region 2nd-gen wiring fails.
const val FIREBASE_FUNCTIONS_REGION = "us-central1"

// The local Firebase emulator suite's ports, declared ONCE for every platform: Android's and
// iOS's FirebaseEmulators.kt both read these. They are the Firebase CLI's defaults — if your
// firebase.json moves one, move it here and both platforms follow.
const val FIREBASE_AUTH_EMULATOR_PORT = 9099
const val FIREBASE_FIRESTORE_EMULATOR_PORT = 8080
const val FIREBASE_FUNCTIONS_EMULATOR_PORT = 5001
const val FIREBASE_STORAGE_EMULATOR_PORT = 9199
