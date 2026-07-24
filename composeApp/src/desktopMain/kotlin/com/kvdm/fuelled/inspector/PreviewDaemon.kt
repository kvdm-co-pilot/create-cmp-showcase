package com.kvdm.fuelled.inspector

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

/**
 * Resident preview daemon — phase 2 of the create-cmp preview loop (`@Preview` parity).
 *
 * A long-lived headless JVM that renders [previewRegistry] screens ON DEMAND over a
 * loopback HTTP protocol, so the per-change cost drops from a full Gradle JavaExec cycle
 * to a warm in-process render. Designed to run under **Compose Hot Reload**:
 *
 *   ./gradlew :composeApp:hotRunDesktop --mainClass=com.kvdm.fuelled.inspector.PreviewDaemonKt --auto
 *
 * With `--auto`, saving a source file recompiles incrementally and hot-swaps classes into
 * THIS running JVM; every `/render` composes fresh scenes (and re-reads the registry), so
 * the next render reflects the new code — typically 1–3s after save instead of a 20–40s
 * task cycle. Also runnable without hot reload via the plain `runPreviewDaemon` task
 * (resident, but a restart is needed to pick up recompiled classes).
 *
 * Routes (loopback only, mirroring the on-device inspector server's posture):
 *   GET /health                 → { ok, pid, screens, port, reloadCount, reloadHooked }
 *   GET /screens                → the registry (ids + titles)
 *   GET /render?screen=<id|all>[&afterReload=<n>]
 *       → renders to the previews dir; → { rendered, ms, out, reloadCount, reloadHooked }.
 *       With afterReload, the render WAITS (≤10s) until reloadCount exceeds <n>: classes
 *       appearing on disk precede the in-JVM swap, so a caller that just saw a source
 *       change uses this to avoid composing pre-swap code (a stale render).
 *   GET /shutdown               → 200, then exits
 *
 * Program args (never --args-through-Gradle for the renderScreens task, but ComposeHotRun
 * passes them fine; all optional): `--port <n>` (default 9601), `--out <dir>` (default
 * build/previews — resolved against the task's working dir, composeApp/), `--pngScale <n>`.
 */
fun main(args: Array<String>) {
    val port = argValue(args, "--port")?.toIntOrNull() ?: 9601
    val outRoot = File(argValue(args, "--out") ?: "build/previews")
    val pngScale = argValue(args, "--pngScale")?.toFloatOrNull()?.takeIf { it > 0f } ?: 2f

    val reloadCount = AtomicLong(0)
    val reloadErrors = AtomicLong(0)
    val reloadHooked = hookReloadListener { succeeded ->
        if (succeeded) reloadCount.incrementAndGet() else reloadErrors.incrementAndGet()
    }

    initPreviewKoin()
    outRoot.mkdirs()
    File(outRoot, "design-system.json").writeText(designSystemCatalog())

    val renderLock = Any()
    val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
    server.executor = Executors.newFixedThreadPool(3)

    server.createContext("/health") { exchange ->
        respondJson(exchange, 200, buildJsonObject {
            put("ok", JsonPrimitive(true))
            put("pid", JsonPrimitive(ProcessHandle.current().pid()))
            put("port", JsonPrimitive(port))
            // WHICH project this daemon serves. The port is machine-global, so a
            // preview service that finds a healthy daemon here has no other way to
            // tell "my project's daemon" from "another checkout's daemon on the
            // same port" — and adopting the wrong one renders another app's screens.
            put("previewsDir", JsonPrimitive(outRoot.absolutePath))
            put("reloadCount", JsonPrimitive(reloadCount.get()))
            put("reloadErrors", JsonPrimitive(reloadErrors.get()))
            put("reloadHooked", JsonPrimitive(reloadHooked))
            put("screens", buildJsonArray {
                previewRegistry().forEach { add(JsonPrimitive(it.id)) }
            })
        })
    }

    server.createContext("/screens") { exchange ->
        respondJson(exchange, 200, buildJsonObject {
            put("screens", buildJsonArray {
                previewRegistry().forEach {
                    add(buildJsonObject {
                        put("id", JsonPrimitive(it.id))
                        put("title", JsonPrimitive(it.title))
                    })
                }
            })
        })
    }

    server.createContext("/render") { exchange ->
        // Swap-aware rendering: classes on disk precede the in-JVM swap, so a caller
        // that just observed a source change passes afterReload=<last seen count> and
        // we wait (bounded) for the swap to actually land before composing.
        val afterReload = queryParam(exchange, "afterReload")?.toLongOrNull()
        if (afterReload != null && reloadHooked) {
            val deadline = System.currentTimeMillis() + 10_000
            while (reloadCount.get() <= afterReload && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
        }
        // Re-read the registry PER REQUEST: after a hot swap, this picks up the
        // redefined screen composables (fresh scenes are composed from current classes).
        val all = previewRegistry()
        val filter = queryParam(exchange, "screen") ?: "all"
        val selected = if (filter == "all") all else all.filter { it.id == filter }
        if (selected.isEmpty()) {
            respondJson(exchange, 404, buildJsonObject {
                put("error", JsonPrimitive(
                    "Unknown screen '$filter'. Available: ${all.joinToString(", ") { it.id }} (or 'all')."
                ))
            })
            return@createContext
        }
        try {
            val rendered = mutableListOf<String>()
            val ms = measureTimeMillis {
                synchronized(renderLock) {
                    for (entry in selected) {
                        val dir = File(outRoot, entry.id).apply { mkdirs() }
                        renderTree(entry, File(dir, "tree.json"))
                        renderPng(entry, File(dir, "screen.png"), pngScale)
                        rendered += entry.id
                    }
                    // The manifest always lists the FULL registry so single-screen renders
                    // keep the gallery complete.
                    File(outRoot, "manifest.json").writeText(manifestJson(all, pngScale))
                }
            }
            respondJson(exchange, 200, buildJsonObject {
                put("rendered", buildJsonArray { rendered.forEach { add(JsonPrimitive(it)) } })
                put("ms", JsonPrimitive(ms))
                put("out", JsonPrimitive(outRoot.absolutePath))
                put("reloadCount", JsonPrimitive(reloadCount.get()))
                put("reloadErrors", JsonPrimitive(reloadErrors.get()))
                put("reloadHooked", JsonPrimitive(reloadHooked))
            })
        } catch (t: Throwable) {
            respondJson(exchange, 500, buildJsonObject {
                put("error", JsonPrimitive(t.message ?: t.toString()))
            })
        }
    }

    server.createContext("/shutdown") { exchange ->
        respondJson(exchange, 200, buildJsonObject { put("ok", JsonPrimitive(true)) })
        Thread {
            Thread.sleep(100)
            exitProcess(0)
        }.start()
    }

    server.start()
    System.err.println(
        "preview daemon listening on http://127.0.0.1:$port " +
            "(previews -> ${outRoot.absolutePath}, pngScale $pngScale)"
    )
    CountDownLatch(1).await() // resident until /shutdown or SIGTERM
}

/**
 * Register a Compose Hot Reload after-reload callback via REFLECTION against the
 * AGENT (`org.jetbrains.compose.reload.agent.ReloadHooksKt`) — the agent jar is what
 * `hotRunDesktop` loads via -javaagent, so it IS visible to app code, unlike the
 * runtime-api facade (verified: ClassNotFoundException). No compile-time dependency,
 * so the inspector feature stays independent of the dev-client feature; plain
 * `runPreviewDaemon` JVMs return false and just report reloadHooked=false.
 *
 * The callback receives Either<Reload, Throwable>: success bumps the reload count,
 * failure (a swap the agent could not apply) is reported separately so callers can
 * surface "your edit did not land" instead of rendering pre-swap code forever.
 */
private fun hookReloadListener(onReload: (succeeded: Boolean) -> Unit): Boolean = try {
    val hooks = Class.forName("org.jetbrains.compose.reload.agent.ReloadHooksKt")
    val isSuccess = runCatching {
        Class.forName("org.jetbrains.compose.reload.core.TryKt")
            .getMethod("isSuccess", Class.forName("org.jetbrains.compose.reload.core.Either"))
    }.getOrNull()
    val callback: Function2<Any?, Any?, Unit> = { _, either ->
        val ok = runCatching { isSuccess?.invoke(null, either) as? Boolean }.getOrNull() ?: true
        onReload(ok)
    }
    hooks.getMethod("invokeAfterHotReload", Function2::class.java).invoke(null, callback)
    true
} catch (t: Throwable) {
    System.err.println(
        "preview daemon: reload hook unavailable (${t.javaClass.simpleName}) — " +
            "swap-aware renders disabled, callers fall back to time-based settling"
    )
    false
}

private fun argValue(args: Array<String>, flag: String): String? {
    val i = args.indexOf(flag)
    return if (i >= 0 && i + 1 < args.size) args[i + 1] else null
}

private fun queryParam(exchange: HttpExchange, key: String): String? =
    exchange.requestURI.query
        ?.split("&")
        ?.mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq > 0) part.substring(0, eq) to part.substring(eq + 1) else null
        }
        ?.firstOrNull { it.first == key }
        ?.second
        ?.takeIf { it.isNotBlank() }

private val daemonJson = Json { prettyPrint = true }

private fun respondJson(exchange: HttpExchange, status: Int, body: JsonElement) {
    val bytes = daemonJson.encodeToString(JsonElement.serializer(), body).toByteArray()
    exchange.responseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
