import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
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

            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.functions)
            implementation(libs.firebase.storage)
            implementation(libs.firebase.messaging)
            implementation(libs.firebase.config)

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
                // Headless semantics API (runDesktopComposeUiTest / onRoot) for the preview
                // harness (inspector/PreviewHarness.kt) — renders real screens with no window.
                // Desktop is a dev-only target, so shipping the test artifact here is deliberate.
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
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
    namespace = "com.kvdm.fuelled"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kvdm.fuelled"
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
        getByName("debug") {
            // Debug builds point GitLive Firebase at the local emulators (see Application/KoinHelper).
            // 10.0.2.2 is the Android emulator's host-loopback alias.
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "true")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"10.0.2.2\"")
            buildConfigField("int", "FIREBASE_AUTH_PORT", "9099")
            buildConfigField("int", "FIREBASE_FIRESTORE_PORT", "8080")
            buildConfigField("int", "FIREBASE_FUNCTIONS_PORT", "5001")
            buildConfigField("int", "FIREBASE_STORAGE_PORT", "9199")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

// Pin the generated resources accessor package so `com.kvdm.fuelled.generated.resources.Res`
// is stable regardless of the Gradle project name. The default would derive from
// rootProject.name (slugified), which couples imports to the app-name token.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.kvdm.fuelled.generated.resources"
    generateResClass = ResourcesExtension.ResourceClassGeneration.Always
}

room {
    // Per-target schema directories, NOT one shared dir. With a single directory the
    // copyRoomSchemas aggregation task requires every target's exported schema to be
    // byte-identical — and the first entity edit after scaffold trips a cross-target
    // checksum conflict against the stale intermediate of whichever target built last
    // ("Inconsistency detected exporting Room schema files"). Per-target locations are
    // exactly what that error's remediation asks for.
    schemaDirectory("android", "$projectDir/schemas/android")
    schemaDirectory("desktop", "$projectDir/schemas/desktop")
    schemaDirectory("iosSimulatorArm64", "$projectDir/schemas/iosSimulatorArm64")
    schemaDirectory("iosX64", "$projectDir/schemas/iosX64")
    schemaDirectory("iosArm64", "$projectDir/schemas/iosArm64")
}

// Entry point for the desktop dev-client window (and the class Compose Hot Reload launches).
compose.desktop {
    application {
        mainClass = "com.kvdm.fuelled.MainKt"
    }
}

// Headless screen previews — the project-wired tier-0 loop of the create-cmp inspector.
// Renders every screen in inspector/PreviewRegistry.kt (real DI, real theme, real data)
// to build/previews/<id>/{screen.png, tree.json} with NO device, emulator, or window,
// then qa/preview-gallery.mjs turns the output into one self-contained index.html:
//
//   ./gradlew :composeApp:renderScreens                      # all screens
//   ./gradlew :composeApp:renderScreens -Pscreen=home        # one screen
//   node qa/preview-gallery.mjs                              # build the gallery
//
// Parameters travel as -P properties -> system properties, NEVER via --args (Gradle's CLI
// parsing splits space-separated --args values into task names).
tasks.register<JavaExec>("renderScreens") {
    group = "verification"
    description = "Render registered screens headlessly to PNG + inspector-contract tree JSON."
    val desktopCompilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath(desktopCompilation.output.allOutputs, desktopCompilation.runtimeDependencyFiles)
    mainClass.set("com.kvdm.fuelled.inspector.PreviewHarnessKt")
    systemProperty("java.awt.headless", "true")
    systemProperty("screen", providers.gradleProperty("screen").getOrElse("all"))
    systemProperty(
        "out",
        providers.gradleProperty("previewOut")
            .getOrElse(layout.buildDirectory.dir("previews").get().asFile.absolutePath),
    )
    systemProperty("pngScale", providers.gradleProperty("pngScale").getOrElse("2"))
}

// Resident preview daemon (phase 2): a long-lived headless JVM serving /render on
// 127.0.0.1:9601 so warm re-renders skip the task cycle. Preferred launch is under
// Compose Hot Reload (saves hot-swap into the running daemon; needs the dev-client
// feature's Compose Hot Reload plugin):
//
//   ./gradlew :composeApp:hotRunDesktop --mainClass=com.kvdm.fuelled.inspector.PreviewDaemonKt --auto
//
// This plain variant runs on any JDK with no hot swap (restart to pick up new code):
tasks.register<JavaExec>("runPreviewDaemon") {
    group = "verification"
    description = "Run the resident preview daemon (loopback HTTP /render, no hot reload)."
    val desktopCompilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath(desktopCompilation.output.allOutputs, desktopCompilation.runtimeDependencyFiles)
    mainClass.set("com.kvdm.fuelled.inspector.PreviewDaemonKt")
    systemProperty("java.awt.headless", "true")
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
