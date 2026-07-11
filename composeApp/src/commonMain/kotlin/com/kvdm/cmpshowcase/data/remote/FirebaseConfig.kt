package com.kvdm.cmpshowcase.data.remote

// Region for Cloud Functions / callables. Keep schedulers, callables, Firestore in the
// SAME region — cross-region 2nd-gen wiring fails.
const val FIREBASE_FUNCTIONS_REGION = "us-central1"
