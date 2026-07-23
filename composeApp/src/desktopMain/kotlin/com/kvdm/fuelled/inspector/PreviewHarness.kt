package com.kvdm.fuelled.inspector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.kvdm.fuelled.core.connectivity.NetworkMonitor
import com.kvdm.fuelled.data.local.AppDatabase
import com.kvdm.fuelled.data.local.buildDatabase
import com.kvdm.fuelled.di.appModules
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledTheme
import com.kvdm.fuelled.presentation.theme.FuelledTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.skia.EncodedImageFormat
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.io.File
import kotlin.system.exitProcess

// Phone-shaped viewport, density 1 — px == dp in the dumped tree (matches the dev-client
// window and keeps the inspector's a11y/touch-target math exact).
internal const val WIDTH = 411
internal const val HEIGHT = 891

/**
 * Headless preview harness — the project-wired tier-0 loop of the create-cmp inspector.
 *
 * Renders REAL screens from [previewRegistry] with no device, emulator, or window:
 * for each screen it writes the inspector-contract semantics tree (`tree.json`, via
 * `runDesktopComposeUiTest`) and a pixel preview (`screen.png`, via [ImageComposeScene])
 * from the same composition sources at the same viewport, plus the declared design-system
 * catalog (`design-system.json`) and a `manifest.json` for gallery tooling
 * (qa/preview-gallery.mjs turns the output into a single self-contained index.html).
 *
 * Real DI, real theme, real data: Koin starts with the same modules as the app, and
 * screens resolve their ViewModels through `koinViewModel()` exactly as in production.
 *
 * Invoked by the `:composeApp:renderScreens` Gradle task; parameters arrive as SYSTEM
 * PROPERTIES (never `--args`, which Gradle's CLI parsing mangles):
 *   -Pscreen=<id|all>   which registry entry to render        (default all)
 *   -PpreviewOut=<dir>  output root                           (default build/previews)
 *   -PpngScale=<n>      PNG density multiplier for sharpness  (default 2; tree stays density 1)
 */
fun main() {
    val screenFilter = System.getProperty("screen")?.takeIf { it.isNotBlank() } ?: "all"
    val outRoot = File(System.getProperty("out")?.takeIf { it.isNotBlank() } ?: "build/previews")
    val pngScale = System.getProperty("pngScale")?.toFloatOrNull()?.takeIf { it > 0f } ?: 2f

    initPreviewKoin()

    val all = previewRegistry()
    val selected = if (screenFilter == "all") all else all.filter { it.id == screenFilter }
    if (selected.isEmpty()) {
        System.err.println(
            "Unknown screen '$screenFilter'. Available: ${all.joinToString(", ") { it.id }} (or 'all').",
        )
        exitProcess(2)
    }

    outRoot.mkdirs()
    File(outRoot, "design-system.json").writeText(designSystemCatalog())

    for (entry in selected) {
        val dir = File(outRoot, entry.id).apply { mkdirs() }
        renderTree(entry, File(dir, "tree.json"))
        renderPng(entry, File(dir, "screen.png"), pngScale)
        System.err.println("rendered ${entry.id} -> ${dir.absolutePath}")
    }

    File(outRoot, "manifest.json").writeText(manifestJson(selected, pngScale))
    System.err.println("previews -> ${outRoot.absolutePath}")
    // AWT/EDT and Koin threads are non-daemon; exit explicitly once outputs are on disk.
    exitProcess(0)
}

/**
 * Preview-harness DI — intentionally independent of the dev-client feature (which owns
 * DesktopModule): the same platform bindings, started once for the render run.
 */
internal fun initPreviewKoin() {
    startKoin {
        modules(
            module {
                single<AppDatabase> { buildDatabase() }
                single { NetworkMonitor(null) }
            },
            *appModules.toTypedArray(),
        )
    }
}

/**
 * Provides what a bare offscreen composition lacks so production screens compose
 * unmodified: a RESUMED [LifecycleOwner] (for `collectAsStateWithLifecycle`) and a fresh
 * [ViewModelStoreOwner] per composition (so `koinViewModel()` resolves — and each render
 * gets fresh ViewModels), then the app theme.
 */
@Composable
internal fun PreviewRoot(content: @Composable () -> Unit) {
    val owner = remember { PreviewOwner() }
    CompositionLocalProvider(
        LocalLifecycleOwner provides owner,
        LocalViewModelStoreOwner provides owner,
    ) {
        FuelledTheme(content)
    }
}

internal class PreviewOwner : LifecycleOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry.createUnsafe(this).apply {
        currentState = Lifecycle.State.RESUMED
    }
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
}

/**
 * Semantics tree at the phone viewport, density 1. ADAPTIVE settle: async data arrives in
 * real time, so keep sampling until two consecutive dumps are identical (bounded) — static
 * screens finish in ~300ms instead of paying the full window.
 */
@OptIn(ExperimentalTestApi::class)
internal fun renderTree(entry: ScreenPreview, outFile: File) {
    var json = ""
    runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
        setContent { PreviewRoot { entry.content() } }
        waitForIdle()
        var prev: String? = null
        var stable = 0
        var iterations = 0
        while (iterations < 8 && stable < 2) {
            Thread.sleep(150)
            waitForIdle()
            val dump = PreviewSemanticsJson.dumpTree(onRoot(useUnmergedTree = true).fetchSemanticsNode())
            if (dump == prev) stable++ else { stable = 0; prev = dump }
            iterations++
        }
        json = prev ?: PreviewSemanticsJson.dumpTree(onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }
    outFile.writeText(json)
}

/**
 * Pixel twin of the tree: same content, same dp viewport, density [scale] for sharpness.
 * Frames are re-rendered while invalidations arrive so async data reaches the pixels too.
 */
internal fun renderPng(entry: ScreenPreview, outFile: File, scale: Float) {
    val scene = ImageComposeScene(
        width = (WIDTH * scale).toInt(),
        height = (HEIGHT * scale).toInt(),
        density = Density(scale),
    ) {
        PreviewRoot { entry.content() }
    }
    try {
        var elapsedNanos = 0L
        var image = scene.render(elapsedNanos)
        var quiet = 0
        var iterations = 0
        while (iterations < 12 && quiet < 2) {
            Thread.sleep(100)
            elapsedNanos += 100_000_000L
            if (scene.hasInvalidations()) {
                image = scene.render(elapsedNanos)
                quiet = 0
            } else {
                quiet++
            }
            iterations++
        }
        val data = image.encodeToData(EncodedImageFormat.PNG)
            ?: error("Skia failed to encode ${entry.id} as PNG")
        outFile.writeBytes(data.bytes)
    } finally {
        scene.close()
    }
}

/** The declared design-system catalog — generated FROM the theme objects, so it can't drift. */
internal fun designSystemCatalog(): String {
    val pretty = Json { prettyPrint = true }
    fun hex(color: androidx.compose.ui.graphics.Color): JsonElement =
        JsonPrimitive("#%06X".format(color.toArgb() and 0xFFFFFF))
    fun dp(value: androidx.compose.ui.unit.Dp): JsonElement =
        JsonPrimitive("${value.value.toInt()}dp")

    val doc = buildJsonObject {
        put("colors", buildJsonObject {
            put("Primary", hex(FuelledColors.Primary))
            put("OnPrimary", hex(FuelledColors.OnPrimary))
            put("Accent", hex(FuelledColors.Accent))
            put("OnAccent", hex(FuelledColors.OnAccent))
            put("Secondary", hex(FuelledColors.Secondary))
            put("Error", hex(FuelledColors.Error))
            put("Success", hex(FuelledColors.Success))
            put("Warning", hex(FuelledColors.Warning))
            put("Info", hex(FuelledColors.Info))
            put("Background", hex(FuelledColors.Background))
            put("Surface", hex(FuelledColors.Surface))
            put("SurfaceVariant", hex(FuelledColors.SurfaceVariant))
            put("OnSurface", hex(FuelledColors.OnSurface))
            put("OnSurfaceVariant", hex(FuelledColors.OnSurfaceVariant))
            put("Outline", hex(FuelledColors.Outline))
            put("OutlineVariant", hex(FuelledColors.OutlineVariant))
            put("Divider", hex(FuelledColors.Divider))
        })
        put("dimens", buildJsonObject {
            put("ElevationCard", dp(FuelledTokens.ElevationCard))
            put("ElevationModal", dp(FuelledTokens.ElevationModal))
            put("PaddingPage", dp(FuelledTokens.PaddingPage))
            put("PaddingCard", dp(FuelledTokens.PaddingCard))
            put("GapCard", dp(FuelledTokens.GapCard))
            put("BottomNavHeight", dp(FuelledTokens.BottomNavHeight))
            put("RadiusCard", dp(FuelledTokens.RadiusCard))
            put("RadiusPill", dp(FuelledTokens.RadiusPill))
            put("RadiusModal", dp(FuelledTokens.RadiusModal))
            put("RadiusInput", dp(FuelledTokens.RadiusInput))
        })
    }
    return pretty.encodeToString(JsonElement.serializer(), doc)
}

internal fun manifestJson(entries: List<ScreenPreview>, pngScale: Float): String {
    val pretty = Json { prettyPrint = true }
    val doc = buildJsonObject {
        put("viewport", buildJsonObject {
            put("width", JsonPrimitive(WIDTH))
            put("height", JsonPrimitive(HEIGHT))
            put("treeDensity", JsonPrimitive(1))
            put("pngScale", JsonPrimitive(pngScale))
        })
        put("screens", buildJsonArray {
            entries.forEach { entry ->
                add(buildJsonObject {
                    put("id", JsonPrimitive(entry.id))
                    put("title", JsonPrimitive(entry.title))
                    put("tree", JsonPrimitive("${entry.id}/tree.json"))
                    put("png", JsonPrimitive("${entry.id}/screen.png"))
                })
            }
        })
    }
    return pretty.encodeToString(JsonElement.serializer(), doc)
}
