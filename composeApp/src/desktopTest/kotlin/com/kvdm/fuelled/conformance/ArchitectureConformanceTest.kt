package com.kvdm.fuelled.conformance

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * The conformance gates — `specs/app-base.spec.md`'s ARCH clauses as executable checks.
 * These enforce the architecture mechanically: an AI (or human) that drifts from the
 * contract gets a named clause violation with the offending files, not a style nit.
 *
 * Deliberately dependency-free (plain source scanning) rather than Konsist: the template's
 * moat is a frozen, lockstep-safe version set, and Konsist would add a kotlin-compiler
 * pin to track. Swap in Konsist if you want richer queries — keep the clause ids.
 *
 * Runs on the JVM tier (`:composeApp:desktopTest`), invoked by the verify lane's
 * `conformance` step.
 */
class ArchitectureConformanceTest {

    private val commonMain = File("src/commonMain/kotlin")
    private val commonTest = File("src/commonTest/kotlin")

    private fun sources(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun imports(file: File): List<String> =
        file.readLines().filter { it.trimStart().startsWith("import ") }.map { it.trim() }

    /**
     * Source lines with comments stripped — whole comment lines AND trailing `// …` tails
     * (a `(?<!:)` guard keeps `https://` URLs inside strings intact). Layer-boundary rules
     * scan these for BOTH `import x.y.` statements AND fully-qualified inline references
     * (`x.y.Type(...)`) — import-only matching leaves a one-edit evasion open: delete the
     * import, qualify the name inline, and the gate goes green while the violation remains.
     * Stripping trailing comments closes the inverse hole: prose like
     * `get() // wires com.app.data.ItemRepositoryImpl` must not fail a boundary gate.
     */
    private val trailingLineComment = Regex("""(?<!:)//.*""")

    private fun nonCommentLines(file: File): List<String> =
        file.readLines()
            .filterNot {
                val t = it.trimStart()
                t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
            }
            .map { it.replace(trailingLineComment, "") }

    private fun bannedReference(file: File, banned: List<String>): Boolean =
        nonCommentLines(file).any { line -> banned.any { line.contains(it) } }

    private fun under(file: File, segment: String): Boolean =
        file.path.replace(File.separatorChar, '/').contains("/$segment/")

    /**
     * True when the file sits in a feature subpackage (presentation/<feature>/…) rather than
     * at the presentation root (App.kt) — the scope for the composable-file rules.
     */
    private fun inPresentationFeatureDir(file: File): Boolean {
        val rel = file.path.replace(File.separatorChar, '/').substringAfter("/presentation/", "")
        return rel.isNotEmpty() && rel.contains('/')
    }

    private fun violation(clause: String, rule: String, offenders: List<String>, fix: String): String =
        "[$clause] $rule\n  Offending: ${offenders.joinToString("\n             ")}\n  Fix: $fix"

    // SPEC: ARCH-01
    @Test
    fun `ARCH-01 presentation never references the data layer`() {
        val offenders = sources(commonMain)
            .filter { under(it, "presentation") }
            .filter { bannedReference(it, listOf("com.kvdm.fuelled.data.")) }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-01", "presentation depends on domain only — it never references the data layer " +
                    "(neither imports nor fully-qualified inline names).",
                offenders,
                "depend on a domain interface (domain/repository) and let di/ wire the data implementation.",
            )
        )
    }

    // SPEC: ARCH-02
    @Test
    fun `ARCH-02 domain is pure - no app layers, no frameworks`() {
        val banned = listOf(
            "com.kvdm.fuelled.presentation.", "com.kvdm.fuelled.data.", "com.kvdm.fuelled.di.",
            "androidx.compose.", "org.koin.",
        )
        val offenders = sources(commonMain)
            .filter { under(it, "domain") }
            .filter { bannedReference(it, banned) }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-02", "domain references nothing app-internal and no UI/DI frameworks " +
                    "(neither imports nor fully-qualified inline names).",
                offenders,
                "move framework-touching code out to presentation/data; domain stays pure Kotlin.",
            )
        )
    }

    // SPEC: ARCH-03
    @Test
    fun `ARCH-03 every ViewModel has a test`() {
        val testNames = sources(commonTest).map { it.name }.toSet()
        val offenders = sources(commonMain)
            .filter { it.name.endsWith("ViewModel.kt") }
            .filterNot { "${it.name.removeSuffix(".kt")}Test.kt" in testNames }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-03", "every ViewModel has a corresponding *ViewModelTest in commonTest.",
                offenders,
                "add the test mirroring FoodsViewModelTest (Turbine + fakes; loading/success/error paths).",
            )
        )
    }

    // SPEC: ARCH-04
    // Component-derived tags (component-system-deep-dive.md §6.4) count as tag provenance:
    // a screen built entirely from registry components (ScreenColumn/AppHeader/
    // ContentStateContainer/…) is automation-reachable through the tags THOSE components
    // emit from their required `screenTag` parameter, even with no literal `testTag` of its
    // own. The acceptance is narrow by design (mitigates the risk table's sloppy-scan
    // concern): `screenTag\s*=` in a call-argument position AND the file actually imports
    // from `presentation.components` — a file that merely contains the substring
    // "screenTag" in a comment, or that never touches the registry, still fails.
    private val screenTagArgument = Regex("""screenTag\s*=""")

    private fun hasTagProvenance(file: File): Boolean {
        val text = file.readText()
        val importsComponents = text.lines().any {
            it.trimStart().startsWith("import ") && it.contains(".presentation.components.")
        }
        return text.contains("testTag") || (importsComponents && screenTagArgument.containsMatchIn(text))
    }

    @Test
    fun `ARCH-04 every feature composable file is automation-reachable - literal testTag or screenTag provenance`() {
        // Scoped by CONTENT (contains @Composable), not by *Screen.kt filename: real apps
        // split features into Screen.kt (often ViewModel-only) and Content.kt (the UI).
        // Filename scoping produced both false negatives (untagged FooContent.kt slid
        // through) and false positives (VM-only FooScreen.kt was flagged) in the field.
        val offenders = sources(commonMain)
            .filter { inPresentationFeatureDir(it) }
            .filterNot { under(it, "components") || under(it, "navigation") || under(it, "theme") }
            .filter { it.readText().contains("@Composable") }
            .filterNot { hasTagProvenance(it) }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-04", "every feature UI file is automation-reachable: files containing a " +
                    "@Composable declare at least one literal testTag OR pass screenTag = to a " +
                    "registry component imported from presentation.components.",
                offenders,
                "add Modifier.semantics { testTag = \"<feature>_<element>\" } to the file's key nodes, " +
                    "or compose it from registry components (e.g. ScreenColumn(screenTag = \"<feature>\") { … }).",
            )
        )
    }

    // SPEC: ARCH-11
    @Test
    fun `ARCH-11 screens present loading through the components registry, never a hand-rolled indicator`() {
        val banned = listOf("CircularProgressIndicator", "LinearProgressIndicator")
        val offenders = sources(commonMain)
            .filter { inPresentationFeatureDir(it) }
            .filterNot { under(it, "components") || under(it, "navigation") || under(it, "theme") }
            .filter { bannedReference(it, banned) }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-11", "screens never reference CircularProgressIndicator/LinearProgressIndicator " +
                    "directly — loading is presented through ContentStateContainer/ContentStateDefaults.",
                offenders,
                "bind the screen's loading arm to ContentStateContainer(state = …, screenTag = …) instead " +
                    "of drawing a progress indicator by hand (see the exemplar FoodsRoute).",
            )
        )
    }

    // SPEC: ARCH-05
    @Test
    fun `ARCH-05 no hardcoded Color literals outside the theme`() {
        val colorLiteral = Regex("""Color\(0x""")
        val offenders = sources(commonMain)
            .filterNot { under(it, "theme") }
            .filter { file -> nonCommentLines(file).any { colorLiteral.containsMatchIn(it) } }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-05", "design colors come from the token catalog, never Color(0x…) literals.",
                offenders,
                "add/use a token in presentation/theme instead of the literal.",
            )
        )
    }

    /**
     * Each `suspend fun` declaration in [file] as one whitespace-normalized signature —
     * from the keyword through its balanced parameter list to the end of that line, so a
     * multiline (trailing-comma style) signature is judged by its return type, not by
     * whichever fragment happens to share a line with `suspend fun`.
     */
    private fun suspendFunSignatures(file: File): List<String> {
        val text = nonCommentLines(file).joinToString("\n")
        val signatures = mutableListOf<String>()
        var start = text.indexOf("suspend fun")
        while (start >= 0) {
            val open = text.indexOf('(', start)
            if (open == -1) break
            var depth = 0
            var i = open
            while (i < text.length) {
                val c = text[i]
                if (c == '(') depth += 1
                if (c == ')') {
                    depth -= 1
                    if (depth == 0) break
                }
                i += 1
            }
            val end = text.indexOf('\n', i).let { if (it == -1) text.length else it }
            signatures.add(text.substring(start, end).replace(Regex("\\s+"), " ").trim())
            start = text.indexOf("suspend fun", end)
        }
        return signatures
    }

    // SPEC: ARCH-06
    @Test
    fun `ARCH-06 repository interfaces return AppResult - exceptions never cross the boundary`() {
        val offenders = sources(commonMain)
            .filter { under(it, "domain") && under(it, "repository") }
            .flatMap { file ->
                suspendFunSignatures(file)
                    .filterNot { it.contains(": AppResult<") }
                    .map { "${file.path} — $it" }
            }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-06", "every one-shot repository operation (suspend fun) declares an AppResult<…> " +
                    "return type — failures cross the data boundary as typed DomainError values, never as exceptions.",
                offenders,
                "return AppResult<T> and translate exceptions inside the data implementation via suspendRunCatching.",
            )
        )
    }

    // SPEC: ARCH-07
    @Test
    fun `ARCH-07 ViewModels contain no exception handling`() {
        // Syntactic forms only (`try {`, `catch (`, `runCatching`) — a bare-word scan would
        // false-positive on user-facing copy like "…try again." inside the ViewModel's strings.
        val exceptionHandling = Regex("""\btry\s*\{|\bcatch\s*[({]|\brunCatching\b""")
        val offenders = sources(commonMain)
            .filter { under(it, "presentation") && it.name.endsWith("ViewModel.kt") }
            .filter { file -> nonCommentLines(file).any { exceptionHandling.containsMatchIn(it) } }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-07", "ViewModels contain no try/catch/runCatching — they fold over AppResult " +
                    "and map DomainError kinds to user copy; exception translation is the data layer's job.",
                offenders,
                "remove the exception handling; `when` over the use case's AppResult instead (see the exemplar ViewModel).",
            )
        )
    }

    // SPEC: ARCH-08
    @Test
    fun `ARCH-08 the data layer's only catch mechanism is suspendRunCatching with its cancellation guard`() {
        val helperName = "AppResultCatching.kt"
        val catching = Regex("""\bcatch\s*\(|\brunCatching\b""")
        val dataFiles = sources(commonMain).filter { under(it, "data") }

        val offenders = dataFiles
            .filterNot { it.name == helperName }
            .filter { file -> nonCommentLines(file).any { catching.containsMatchIn(it) } }
            .map { it.path }
            .toMutableList()

        // The helper itself must keep the guard that makes the convention safe:
        // CancellationException is rethrown, never mapped to a Failure.
        val helper = dataFiles.firstOrNull { it.name == helperName }
        if (helper != null) {
            val text = helper.readText()
            if (!text.contains("catch (e: CancellationException)") || !text.contains("throw e")) {
                offenders.add("${helper.path} (the CancellationException rethrow guard is missing)")
            }
        }

        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-08", "the ONLY exception-catching mechanism in the data layer is the shared " +
                    "suspendRunCatching helper ($helperName), which always rethrows CancellationException — " +
                    "ad-hoc catch blocks can silently swallow cancellation.",
                offenders,
                "wrap the I/O in suspendRunCatching { … } (with a mapError classifier) instead of catching directly.",
            )
        )
    }

    // SPEC: ARCH-09
    @Test
    fun `ARCH-09 data never references presentation or di`() {
        val offenders = sources(commonMain)
            .filter { under(it, "data") }
            .filter { bannedReference(it, listOf("com.kvdm.fuelled.presentation.", "com.kvdm.fuelled.di.")) }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-09", "data serves domain contracts — it never references presentation or di " +
                    "(neither imports nor fully-qualified inline names).",
                offenders,
                "move the presentation/di-touching code out of data; data implements domain's repository " +
                    "interfaces and stops there.",
            )
        )
    }

    // SPEC: ARCH-10
    @Test
    fun `ARCH-10 core is leaf utility code - imports domain at most`() {
        val banned = listOf("com.kvdm.fuelled.presentation.", "com.kvdm.fuelled.data.", "com.kvdm.fuelled.di.")
        val offenders = sources(commonMain)
            .filter { under(it, "core") }
            .filter { bannedReference(it, banned) }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-10", "core is leaf utility code, importable by every other layer — it references " +
                    "nothing in presentation, data, or di (neither imports nor fully-qualified inline names).",
                offenders,
                "move the app-layer-touching code out of core; core stays a leaf (domain types at most).",
            )
        )
    }

    // SPEC: ARCH-12
    @Test
    fun `ARCH-12 sample fixtures never cross into production wiring`() {
        // The UI-first pattern's hazard gate. A stateless screen may default its own state
        // parameter to a `sample*` fixture — that same-file default IS the preview seam —
        // and the preview registry / stories / tests (desktopMain, commonTest) render it
        // freely. But the moment ANOTHER commonMain file references the fixture (a nav
        // host resolving entities from sample data, a repository seeding from it), fake
        // data is driving production behavior. Proven drift: the Fuelled dogfood run's
        // AppNavHost read `sampleFoods.firstOrNull { it.id == foodId }` for a real route.
        val sampleDecl = Regex("""\bval\s+(sample[A-Z][A-Za-z0-9]*)""")
        val declarations = mutableMapOf<String, File>() // symbol -> declaring file
        for (file in sources(commonMain)) {
            for (m in sampleDecl.findAll(file.readText())) declarations[m.groupValues[1]] = file
        }
        if (declarations.isEmpty()) return
        val offenders = mutableListOf<String>()
        for (file in sources(commonMain)) {
            val lines = nonCommentLines(file)
            for ((symbol, declaring) in declarations) {
                if (file == declaring) continue
                if (lines.any { Regex("""\b$symbol\b""").containsMatchIn(it) }) {
                    offenders.add("${file.path} (references $symbol from ${declaring.name})")
                }
            }
        }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-12", "a sample*/fixture symbol is never referenced outside its declaring file, " +
                    "the preview registry, or test sources — sample data is a preview seam, not " +
                    "production wiring.",
                offenders.distinct(),
                "wire the real path (repository → use case → ViewModel) and keep the sample only as " +
                    "the stateless screen's default parameter.",
            )
        )
    }

    // SPEC: ARCH-13
    @Test
    fun `ARCH-13 ambient time is read only inside the designated time provider`() {
        // The classic "inject the clock" rule. Ambient time reads make rendered structure
        // and test evidence a function of WHEN you run them — a golden tree that passes at
        // 23:00 and fails at 09:00 with no code change, an assertion that only reds out
        // across a midnight boundary. Time is injected from one provider (a package ending
        // in `.core.time`) so tests can pin it; everywhere else these APIs are banned.
        // Scanned at reference level (imports AND fully-qualified inline calls), the same
        // pragmatic granularity as the layer-boundary rules above. Covers kotlinx-datetime
        // and kotlin.time (`Clock.System`), the JVM clock (`System.currentTimeMillis`),
        // java.time (`LocalDate.now()` et al.), and the ambient zone
        // (`TimeZone.currentSystemDefault()`) — the zone is time-of-run state too.
        val ambientTime = Regex(
            """Clock\.System|System\.currentTimeMillis|LocalDate\.now\s*\(|LocalDateTime\.now\s*\(|""" +
                """LocalTime\.now\s*\(|Instant\.now\s*\(|TimeZone\.currentSystemDefault\s*\("""
        )
        val offenders = sources(commonMain)
            .filterNot { it.path.replace(File.separatorChar, '/').contains("/core/time/") }
            .filter { file -> nonCommentLines(file).any { ambientTime.containsMatchIn(it) } }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-13", "ambient time APIs (Clock.System, System.currentTimeMillis, *.now(), " +
                    "TimeZone.currentSystemDefault()) are called only inside the designated time " +
                    "provider (a `core/time` package) — everywhere else, time is an injected value.",
                offenders,
                "inject a clock/time provider (constructor parameter, wired in di/) that lives in " +
                    "`core/time`, and read the current moment through it — never ambiently. Tests then " +
                    "pin the provider instead of inheriting the wall clock.",
            )
        )
    }

    // SPEC: ARCH-14
    @Test
    fun `ARCH-14 ViewModels are registered with explicit viewModel factories - viewModelOf is banned`() {
        // Koin's reflection-based `viewModelOf(::X)` silently ignores Kotlin default
        // parameter values: it resolves EVERY constructor parameter from the graph, so a
        // ViewModel that compiles and previews fine crashes at runtime resolution the
        // first time a defaulted parameter matters (the default masked a dependency the
        // graph never registered). An explicit `viewModel { X(get(), …) }` factory states
        // each dependency and fails at compile time instead.
        val offenders = sources(commonMain)
            .filter { file -> nonCommentLines(file).any { it.contains("viewModelOf") } }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-14", "ViewModels are registered with explicit `viewModel { … }` factories — " +
                    "reflection-based `viewModelOf` is banned (it silently ignores constructor " +
                    "default parameter values; failures move from compile time to runtime resolution).",
                offenders,
                "replace `viewModelOf(::XViewModel)` with `viewModel { XViewModel(get(), …) }` " +
                    "(import org.koin.core.module.dsl.viewModel), one get() per constructor dependency.",
            )
        )
    }

    // SPEC: SHELL-05
    @Test
    fun `SHELL-05 every non-shell nav destination wraps its content in BaseScreen`() {
        // SHELL-03 bans direct inset-API calls, but a destination that simply never handles
        // insets at all (bare Column at the nav layer) passes that rule while rendering
        // under the status bar. Tab screens are exempt — AppShell wraps them — so the rule
        // targets exactly the destinations registered directly on the NavHost.
        // Filename first; a renamed nav host is still found by content (the `composable(`
        // registrations this rule parses) so the gate cannot go silently vacuous.
        val navHost = sources(commonMain).firstOrNull { it.name == "AppNavHost.kt" }
            ?: sources(commonMain).firstOrNull { under(it, "navigation") && it.readText().contains("composable(") }
            ?: return
        val text = navHost.readText()
        // Both destination shapes: the plain `XScreen(...)` AND the UI-first seam's
        // VM-backed `XRoute(...)` wrapper — keying on `Screen(` alone left every *Route
        // destination uninspected, passing the gate vacuously (dogfood finding: the
        // invariant held in Fuelled, but only by convention, not because this asserted it).
        val screenCall = Regex("""([A-Z][A-Za-z0-9]*(?:Screen|Route))\s*\(""")
        // A call with only a trailing lambda has no paren — `BaseScreen { … }` — so match both.
        val baseScreenCall = Regex("""BaseScreen\s*[({]""")
        val allSources = sources(commonMain)

        val offenders = mutableListOf<String>()
        val chunks = text.split("composable(").drop(1)
        for (chunk in chunks) {
            if (chunk.contains("AppShell(")) continue // shell destination: tabs inherit BaseScreen
            for (m in screenCall.findAll(chunk)) {
                val name = m.groupValues[1]
                if (name == "BaseScreen") continue
                val defining = allSources.firstOrNull { f ->
                    Regex("""fun\s+$name\s*\(""").containsMatchIn(f.readText())
                } ?: continue
                if (!baseScreenCall.containsMatchIn(defining.readText())) {
                    offenders.add("${defining.path} ($name is a NavHost destination without BaseScreen)")
                }
            }
        }
        if (offenders.isNotEmpty()) fail(
            violation(
                "SHELL-05", "every screen registered directly on the NavHost composes inside " +
                    "BaseScreen — otherwise it renders edge-to-edge with no inset handling.",
                offenders.distinct(),
                "wrap the destination's content in BaseScreen { … } (see DetailScreen).",
            )
        )
    }

    // SPEC: SHELL-03
    @Test
    fun `SHELL-03 insets are owned by BaseScreen - screens never touch inset APIs`() {
        val insetApi = Regex(
            "WindowInsets|safeDrawingPadding|safeContentPadding|systemBarsPadding|" +
                "statusBarsPadding|navigationBarsPadding|imePadding"
        )
        val offenders = sources(commonMain)
            .filterNot { under(it, "components") }
            .filterNot { under(it, "navigation") }
            .filter { file -> nonCommentLines(file).any { insetApi.containsMatchIn(it) } }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "SHELL-03", "window insets are solved once, in BaseScreen/AppShell — screens never call inset APIs directly.",
                offenders,
                "compose your content inside BaseScreen (or the shell) and remove the direct inset call.",
            )
        )
    }
}
