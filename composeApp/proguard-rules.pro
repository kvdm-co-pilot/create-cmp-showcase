# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.kvdm.cmpshowcase.**$$serializer { *; }
-keepclassmembers class com.kvdm.cmpshowcase.** {
    *** Companion;
}
-keepclasseswithmembers class com.kvdm.cmpshowcase.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Koin
-keep class org.koin.** { *; }
-keepnames class org.koin.**

# Firebase
-keep class com.google.firebase.** { *; }
-keep class dev.gitlive.firebase.** { *; }
-dontwarn com.google.firebase.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Coil
-dontwarn okio.**

# Navigation
-keepnames class androidx.navigation.**

# Compose
-keep class androidx.compose.** { *; }
