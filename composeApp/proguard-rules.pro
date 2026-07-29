# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.kvdm.fuelled.**$$serializer { *; }
-keepclassmembers class com.kvdm.fuelled.** {
    *** Companion;
}
-keepclasseswithmembers class com.kvdm.fuelled.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Koin
-keep class org.koin.** { *; }
-keepnames class org.koin.**

# Firebase
-keep class com.google.firebase.** { *; }
-keep class dev.gitlive.firebase.** { *; }
-dontwarn com.google.firebase.**

# GitLive's RemoteConfig module is compiled against kotlinx-datetime's OWN Instant, which
# this version set no longer has: on Kotlin 2.2 Instant moved into the stdlib as
# kotlin.time.Instant. R8 hits an unresolvable reference and FAILS the release build — the
# reason a release APK could not be produced at all until 2026-07-29.
#
# Suppressed rather than resolved, deliberately. The reference lives in
# FirebaseRemoteConfigInfo, and RemoteConfig is not a service this app uses, so no code path
# reaches it. Putting kotlinx-datetime back purely to satisfy a class nobody calls would put
# two Instant types in the graph — the more expensive mistake. When GitLive ships a build
# against kotlin.time, delete these two lines; the release build will tell you if it is time.
-dontwarn kotlinx.datetime.Instant$Companion
-dontwarn kotlinx.datetime.Instant

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Coil
-dontwarn okio.**

# Navigation
-keepnames class androidx.navigation.**

# Compose
-keep class androidx.compose.** { *; }
