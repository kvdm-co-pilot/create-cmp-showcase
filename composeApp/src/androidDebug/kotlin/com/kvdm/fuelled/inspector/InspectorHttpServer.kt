package com.kvdm.fuelled.inspector

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.core.view.drawToBitmap
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/**
 * Debug-only, zero-dependency inspection server: a hand-rolled HTTP/1.1 responder over a
 * plain [ServerSocket]. Binds LOOPBACK ONLY (never on the LAN); the host reaches it via
 * `adb forward tcp:9500 tcp:9500`.
 *
 * Routes (JSON unless noted, `Connection: close`):
 *   GET  /inspect/health         → { status, schemaVersion, source, appId, buildType }
 *   GET  /inspect/tree           → the semantics-tree contract document (source "live-android"),
 *                                  read from the topmost Compose root ON THE MAIN THREAD.
 *                                  503 while no Compose root is attached yet (cold start).
 *   GET  /inspect/design-system  → the declared token catalog { colors, dimens }.
 *   GET  /inspect/screenshot     → PNG bytes of the current Compose root (`image/png`) —
 *                                  pixels for the HUMAN's live view, never for model context.
 *   POST /inspect/tap            → body {"x":<px>,"y":<px>} (root-relative px, exactly as the
 *                                  tree's bounds report them) → dispatches a down+up MotionEvent
 *                                  pair to the root view → {"tapped":true,"x":…,"y":…}.
 *   GET  /inspect/remote         → the self-contained remote-control HTML page (same-origin,
 *                                  zero CORS): live screenshot + click-to-tap in a browser.
 *   GET  /inspect/nav            → { currentRoute, backStack } — best-effort, reported by the
 *                                  common `NavInspectionHook` seam; empty snapshot before the
 *                                  first navigation event (see [NavInspector]).
 *   GET  /inspect/crashes        → { crashes: [...] } — persisted crash JSON (current boot +
 *                                  previous ones), newest first (see [CrashRecorder]).
 *   GET  /inspect/db             → schema: { tables:[{name,sql}] } via `sqlite_master`.
 *   GET  /inspect/db?table=<n>&limit=<n> → rows for one table (read-only, bounded; see
 *                                  [DbInspector]). 404 (or empty schema) when the project's
 *                                  `room` feature is off.
 *
 * Single-threaded accept loop on a daemon thread = one client at a time = bounded by design.
 * Failure to bind logs a warning and gives up — the inspector must never crash or block
 * app startup. This class exists only in the androidDebug source set; release builds do
 * not compile it at all.
 */
object InspectorHttpServer {

    const val PORT = 9500
    private const val TAG = "CmpInspector"

    // The main thread is busy during cold start (first setContent/layout) — be generous.
    private const val MAIN_THREAD_TIMEOUT_MS = 5_000L

    // Gap between the synthetic ACTION_DOWN and ACTION_UP — a natural, unambiguous tap
    // (well under the long-press timeout).
    private const val TAP_UP_DELAY_MS = 50L

    private const val JSON_TYPE = "application/json; charset=utf-8"
    private const val HTML_TYPE = "text/html; charset=utf-8"
    private const val PNG_TYPE = "image/png"

    @Volatile private var started = false

    // Set once in [start]; read from the HTTP thread only (crashes/db routes). applicationContext
    // is safe to hold — it never leaks an Activity.
    @Volatile private var appContext: Context? = null

    fun start(appId: String, context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        val thread = Thread({ serve(appId) }, "cmp-inspector-http")
        thread.isDaemon = true
        thread.start()
    }

    private fun serve(appId: String) {
        val socket = try {
            ServerSocket(PORT, 1, InetAddress.getLoopbackAddress())
        } catch (t: Throwable) {
            Log.w(TAG, "inspector server failed to bind 127.0.0.1:$PORT — giving up", t)
            return
        }
        Log.i(TAG, "inspector server listening on 127.0.0.1:$PORT (debug build only)")
        while (true) {
            val client = try {
                socket.accept()
            } catch (t: Throwable) {
                Log.w(TAG, "inspector accept failed — stopping", t)
                return
            }
            try {
                client.use { handle(it, appId) }
            } catch (t: Throwable) {
                // Never let a bad request take the app (or the accept loop) down.
                Log.w(TAG, "inspector request failed", t)
            }
        }
    }

    private fun handle(client: Socket, appId: String) {
        client.soTimeout = 5_000
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        // Drain headers; the only one we ever need is Content-Length (POST /inspect/tap).
        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0) ?: ""
        val rawTarget = parts.getOrNull(1) ?: ""
        val path = rawTarget.substringBefore('?')
        val query = rawTarget.substringAfter('?', "")

        when {
            method == "GET" && path == "/inspect/health" ->
                writeJson(client, 200, healthJson(appId))
            method == "GET" && path == "/inspect/tree" ->
                treeResponse().let { (s, b) -> writeJson(client, s, b) }
            method == "GET" && path == "/inspect/design-system" ->
                writeJson(client, 200, InspectorCatalog.json())
            method == "GET" && path == "/inspect/screenshot" ->
                screenshotResponse(client)
            method == "GET" && path == "/inspect/remote" ->
                writeResponse(client, 200, RemoteControlPage.html(appId).toByteArray(StandardCharsets.UTF_8), HTML_TYPE)
            method == "POST" && path == "/inspect/tap" ->
                tapResponse(readBody(reader, contentLength)).let { (s, b) -> writeJson(client, s, b) }
            method == "GET" && path == "/inspect/nav" ->
                writeJson(client, 200, navJson())
            // The JUMP half of the nav seam (the read half is /inspect/nav above): coverage
            // by route, not by synthesized taps. GET (not POST) deliberately — it is
            // idempotent-ish debug tooling meant to be curl-able, like everything else here.
            method == "GET" && path == "/inspect/navigate" ->
                navigateResponse(query).let { (s, b) -> writeJson(client, s, b) }
            method == "GET" && path == "/inspect/crashes" ->
                writeJson(client, 200, crashesJson())
            method == "GET" && path == "/inspect/db" ->
                dbResponse(query).let { (s, b) -> writeJson(client, s, b) }
            method != "GET" && method != "POST" ->
                writeJson(client, 405, errorJson("method not allowed"))
            else ->
                writeJson(client, 404, errorJson("unknown path"))
        }
    }

    /**
     * Read the request body. The stream is already wrapped in a UTF-8 reader, so we read
     * [contentLength] CHARS — exact for the ASCII JSON `{"x":…,"y":…}` this route accepts
     * (and never under-reads it), which is all this debug server needs.
     */
    private fun readBody(reader: BufferedReader, contentLength: Int): String {
        if (contentLength <= 0) return ""
        val buf = CharArray(contentLength.coerceAtMost(8_192))
        var read = 0
        while (read < buf.size) {
            val n = reader.read(buf, read, buf.size - read)
            if (n < 0) break
            read += n
        }
        return String(buf, 0, read)
    }

    private fun healthJson(appId: String): String {
        // Process start as epoch millis: elapsedRealtime anchors the boot clock to the wall
        // clock. This is the DETERMINISM primitive — an external relaunch (force-stop +
        // launch) is VERIFIED by this value moving forward, so a walk can prove it started
        // from a fresh process instead of trusting that a retained ViewModel isn't lurking.
        val startedAtMs = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime() +
            android.os.Process.getStartElapsedRealtime()
        return """{"status":"ok","schemaVersion":1,"source":"live-android","appId":${JsonPrimitive(appId)},"buildType":"debug",""" +
            """"processStartedAtMs":$startedAtMs,"processUptimeMs":${android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()}}"""
    }

    private fun navigateResponse(query: String): Pair<Int, String> {
        val route = query.split('&')
            .firstOrNull { it.startsWith("route=") }
            ?.substringAfter('=')
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            ?.takeIf { it.isNotBlank() }
            ?: return 400 to errorJson("missing route parameter — /inspect/navigate?route=<route>")
        val error = NavInspector.navigate(route)
        return if (error == null) {
            200 to """{"ok":true,"route":${JsonPrimitive(route)}}"""
        } else if (error.startsWith("nav host not composed")) {
            503 to errorJson(error)
        } else if (error.startsWith("unknown route")) {
            404 to errorJson(error)
        } else {
            500 to errorJson(error)
        }
    }

    private fun treeResponse(): Pair<Int, String> {
        val root = ComposeRootRegistry.current()
            ?: return 503 to errorJson(
                "compose root not ready yet — no Compose root attached (cold start?). Retry shortly."
            )
        // Semantics/layout nodes are not thread-safe: read the tree on the main thread and
        // await with a generous timeout (the main thread is busy during first composition).
        val result = AtomicReference<Pair<Int, String>>()
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            result.set(
                try {
                    // Merged tree — matches the Phase 0 harness (onRoot() default).
                    200 to LiveSemanticsJson.dumpTree(root.semanticsOwner.rootSemanticsNode)
                } catch (t: Throwable) {
                    500 to errorJson("failed to walk semantics tree: ${t.message}")
                }
            )
            latch.countDown()
        }
        return if (latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            result.get()
        } else {
            503 to errorJson("main thread did not respond within ${MAIN_THREAD_TIMEOUT_MS}ms — app busy (cold start?). Retry.")
        }
    }

    /** { currentRoute, backStack } from [NavInspector] — best-effort, never blocks. */
    private fun navJson(): String {
        val snapshot = NavInspector.current()
        val currentRouteJson = snapshot.currentRoute?.let { JsonPrimitive(it).toString() } ?: "null"
        val backStackJson = snapshot.backStack.joinToString(",") { JsonPrimitive(it).toString() }
        return """{"currentRoute":$currentRouteJson,"backStack":[$backStackJson]}"""
    }

    /** { crashes:[...] } — each element is a persisted crash JSON document, verbatim. */
    private fun crashesJson(): String {
        val ctx = appContext ?: return """{"crashes":[]}"""
        val crashes = CrashRecorder.readAll(ctx)
        return """{"crashes":[${crashes.joinToString(",")}]}"""
    }

    /** GET /inspect/db dispatch: no `table` → schema, else → rows for that table. */
    private fun dbResponse(query: String): Pair<Int, String> {
        val params = parseQuery(query)
        val table = params["table"]
        return if (table == null) DbInspector.schema() else DbInspector.rows(table, params["limit"])
    }

    /** Minimal `a=b&c=d` query-string parser (URL-decoded values). Last value wins on repeats. */
    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val key = if (eq >= 0) pair.substring(0, eq) else pair
            val value = if (eq >= 0) pair.substring(eq + 1) else ""
            out[urlDecode(key)] = urlDecode(value)
        }
        return out
    }

    private fun urlDecode(s: String): String =
        try {
            URLDecoder.decode(s, "UTF-8")
        } catch (t: Throwable) {
            s
        }

    /**
     * PNG of the current Compose root. Pixels are read with [PixelCopy] (API 26+) from the
     * COMPOSITED window surface — the same source `adb screencap` reads — so a capture can
     * never replay a stale Compose layer recording. The software fallback (`View.draw` into a
     * bitmap canvas) replays recorded display lists, and a nav-transition `graphicsLayer`
     * whose recording predates the current frame replays the PREVIOUS screen: byte-identical
     * "screenshots" of two different screens, detectable only by hash-compare. PixelCopy is
     * therefore the primary path; the draw fallback remains for pre-26, windowless roots, and
     * dialog/popup roots (their content lives in a different window than the Activity's).
     *
     * The capture runs on the MAIN thread (views are not thread-safe; PixelCopy's listener is
     * delivered there too); PNG compression — tens of ms for a full screen — happens back on
     * the server thread so the UI never pays for it.
     */
    private fun screenshotResponse(client: Socket) {
        val root = ComposeRootRegistry.current()
        if (root == null) {
            writeJson(
                client, 503,
                errorJson("compose root not ready yet — no Compose root attached (cold start?). Retry shortly.")
            )
            return
        }
        val bitmapRef = AtomicReference<Bitmap?>()
        val errorRef = AtomicReference<String?>()
        val latch = CountDownLatch(1)
        val main = Handler(Looper.getMainLooper())
        main.post {
            try {
                val view = root.view
                // PixelCopy only when this root actually lives in the Activity's window —
                // a dialog/popup root has its own window, and copying the Activity's would
                // capture the screen BENEATH it. Those fall back to the software draw.
                val window = windowOf(view)?.takeIf { it.decorView === view.rootView }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && window != null) {
                    val loc = IntArray(2).also(view::getLocationInWindow)
                    val bmp = Bitmap.createBitmap(
                        view.width.coerceAtLeast(1),
                        view.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    val src = Rect(loc[0], loc[1], loc[0] + bmp.width, loc[1] + bmp.height)
                    PixelCopy.request(window, src, bmp, { result ->
                        try {
                            // No composited frame to copy (mid-transition, surface torn down):
                            // fall back rather than fail — a fallback frame beats no frame.
                            bitmapRef.set(if (result == PixelCopy.SUCCESS) bmp else softwareDraw(view))
                        } catch (t: Throwable) {
                            errorRef.set("failed to render screenshot: ${t.message}")
                        }
                        latch.countDown()
                    }, main)
                    return@post // countDown happens in the PixelCopy listener above
                }
                bitmapRef.set(softwareDraw(view))
            } catch (t: Throwable) {
                errorRef.set("failed to render screenshot: ${t.message}")
            }
            latch.countDown()
        }
        if (!latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            writeJson(client, 503, errorJson("main thread did not respond within ${MAIN_THREAD_TIMEOUT_MS}ms — app busy (cold start?). Retry."))
            return
        }
        errorRef.get()?.let {
            writeJson(client, 500, errorJson(it))
            return
        }
        val bitmap = bitmapRef.get()
        if (bitmap == null) {
            writeJson(client, 500, errorJson("failed to render screenshot: no bitmap produced"))
            return
        }
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bitmap.recycle()
        writeResponse(client, 200, bytes, PNG_TYPE)
    }

    /** The view's host [Window], unwrapped through the ContextWrapper chain. MAIN thread. */
    private fun windowOf(view: View): Window? {
        var ctx = view.context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx.window
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Software-canvas capture — re-issues the view's draw. Correct for laid-out static content,
     * but can replay a stale `graphicsLayer` recording mid-transition (see [screenshotResponse]);
     * used only where PixelCopy can't be (pre-API-26, windowless, or dialog roots). MAIN thread.
     */
    private fun softwareDraw(view: View): Bitmap =
        try {
            // androidx.core.view.drawToBitmap (core-ktx — already an androidMain dep).
            view.drawToBitmap()
        } catch (t: Throwable) {
            // Not laid out yet / hardware path refused — plain Canvas draw fallback.
            Bitmap.createBitmap(
                view.width.coerceAtLeast(1),
                view.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            ).also { view.draw(Canvas(it)) }
        }

    /**
     * Dispatch a synthetic tap (ACTION_DOWN, then ACTION_UP ~50ms later) to the topmost
     * Compose root, on the MAIN thread. Coordinates are root-relative px — exactly the
     * space the tree's `bounds` report, so callers can tap what they just inspected.
     */
    private fun tapResponse(body: String): Pair<Int, String> {
        val (x, y) = try {
            val obj = Json.parseToJsonElement(body).jsonObject
            val px = obj["x"]?.jsonPrimitive?.floatOrNull
            val py = obj["y"]?.jsonPrimitive?.floatOrNull
            if (px == null || py == null) {
                return 400 to errorJson("""tap body must be {"x":<px>,"y":<px>} (root-relative px).""")
            }
            px to py
        } catch (t: Throwable) {
            return 400 to errorJson("""tap body must be {"x":<px>,"y":<px>} (root-relative px).""")
        }
        val root = ComposeRootRegistry.current()
            ?: return 503 to errorJson(
                "compose root not ready yet — no Compose root attached (cold start?). Retry shortly."
            )
        val latch = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            try {
                val view = root.view
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
                view.dispatchTouchEvent(down)
                down.recycle()
                handler.postDelayed({
                    try {
                        val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
                        view.dispatchTouchEvent(up)
                        up.recycle()
                    } finally {
                        latch.countDown()
                    }
                }, TAP_UP_DELAY_MS)
            } catch (t: Throwable) {
                latch.countDown()
            }
        }
        return if (latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            200 to """{"tapped":true,"x":${x.roundToInt()},"y":${y.roundToInt()}}"""
        } else {
            503 to errorJson("main thread did not respond within ${MAIN_THREAD_TIMEOUT_MS}ms — app busy (cold start?). Retry.")
        }
    }

    private fun errorJson(message: String): String =
        """{"error":${JsonPrimitive(message)}}"""

    private fun writeJson(client: Socket, status: Int, body: String) =
        writeResponse(client, status, body.toByteArray(StandardCharsets.UTF_8), JSON_TYPE)

    private fun writeResponse(client: Socket, status: Int, bytes: ByteArray, contentType: String) {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            503 -> "Service Unavailable"
            else -> "Internal Server Error"
        }
        val head = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val out = client.getOutputStream()
        out.write(head.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }
}
