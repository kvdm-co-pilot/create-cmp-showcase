import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.compose.hot.reload)
}

kotlin {
    compilerOptions {
        // kotlinx-datetime 0.7.x Clock/Instant delegate to the experimental kotlin.time
        // types on Kotlin 2.2.20; opt in project-wide.
        optIn.add("kotlin.time.ExperimentalTime")
        // iosMain cinterop Foreign APIs (NWPathMonitor, NSHomeDirectory, etc.)
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    // The JVM tier — harness infrastructure, NOT feature-gated. This target hosts the fast
    // verification loop: unit tests, conformance gates, golden-tree renders, and Compose UI
    // Tests all run here (`:composeApp:desktopTest`), device-free. The dev-client window
    // feature merely reuses it.
    jvm("desktop") {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            // Lifecycle / ViewModel
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)

            // Navigation
            implementation(libs.navigation.compose)

            // Koin DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)


            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)

            // Kotlinx
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)

            // Coil image loading
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.cio)
            implementation(libs.koin.android)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.room.runtime.android)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        // JVM tier deps (harness infrastructure — see the jvm("desktop") target note).
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.cio)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.koin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        // JVM-tier test deps: Compose UI Tests, the golden-tree serializer, and the
        // conformance gates all run here (the verify lane's fast, device-free steps).
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.kvdm.cmpshowcase"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kvdm.cmpshowcase"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
    add("coreLibraryDesugaring", libs.android.desugar.jdk)
}

// Pin the generated resources accessor package so `com.kvdm.cmpshowcase.generated.resources.Res`
// is stable regardless of the Gradle project name. The default would derive from
// rootProject.name (slugified), which couples imports to the app-name token.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.kvdm.cmpshowcase.generated.resources"
    generateResClass = ResourcesExtension.ResourceClassGeneration.Always
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Entry point for the desktop dev-client window (and the class Compose Hot Reload launches).
compose.desktop {
    application {
        mainClass = "com.kvdm.cmpshowcase.MainKt"
    }
}

// Evidence integrity: golden-tree baselines (qa/golden) and the UPDATE_GOLDEN capture flag are
// REAL inputs of the JVM test tier, but Gradle can't see either on its own — baselines are read
// at runtime, not compiled, and env vars aren't tracked. Undeclared, the build cache will happily
// replay a PASS from a tree whose baselines differed (or serve an UPDATE_GOLDEN capture run from
// cache so it never writes the baseline at all). Declaring them makes caching honest; the verify
// lane additionally forces `--rerun` so evidence receipts always attest actual execution.
tasks.withType<Test>().configureEach {
    inputs.files(fileTree(rootProject.layout.projectDirectory.dir("qa/golden")) { include("*.json") })
        .withPropertyName("goldenBaselines")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("updateGolden", System.getenv("UPDATE_GOLDEN") ?: "")
}
