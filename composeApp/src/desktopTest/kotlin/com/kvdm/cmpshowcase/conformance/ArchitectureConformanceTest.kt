package com.kvdm.cmpshowcase.conformance

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

    private fun under(file: File, segment: String): Boolean =
        file.path.replace(File.separatorChar, '/').contains("/$segment/")

    private fun violation(clause: String, rule: String, offenders: List<String>, fix: String): String =
        "[$clause] $rule\n  Offending: ${offenders.joinToString("\n             ")}\n  Fix: $fix"

    // SPEC: ARCH-01
    @Test
    fun `ARCH-01 presentation never imports the data layer`() {
        val offenders = sources(commonMain)
            .filter { under(it, "presentation") }
            .filter { file -> imports(file).any { it.startsWith("import com.kvdm.cmpshowcase.data.") } }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-01", "presentation depends on domain only — it never imports the data layer.",
                offenders,
                "depend on a domain interface (domain/repository) and let di/ wire the data implementation.",
            )
        )
    }

    // SPEC: ARCH-02
    @Test
    fun `ARCH-02 domain is pure - no app layers, no frameworks`() {
        val banned = listOf(
            "import com.kvdm.cmpshowcase.presentation.", "import com.kvdm.cmpshowcase.data.", "import com.kvdm.cmpshowcase.di.",
            "import androidx.compose.", "import org.koin.",
        )
        val offenders = sources(commonMain)
            .filter { under(it, "domain") }
            .filter { file -> imports(file).any { imp -> banned.any { imp.startsWith(it) } } }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-02", "domain imports nothing app-internal and no UI/DI frameworks.",
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
                "add the test mirroring HomeViewModelTest (Turbine + fakes; loading/success/error paths).",
            )
        )
    }

    // SPEC: ARCH-04
    @Test
    fun `ARCH-04 every Screen composable declares a testTag`() {
        val offenders = sources(commonMain)
            .filter { it.name != "Screen.kt" && it.name.endsWith("Screen.kt") }
            .filterNot { under(it, "components") || under(it, "navigation") }
            .filterNot { it.readText().contains("testTag") }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-04", "every screen is automation-reachable: *Screen files declare at least one testTag.",
                offenders,
                "add Modifier.semantics { testTag = \"<feature>_<element>\" } to the screen's key nodes.",
            )
        )
    }

    // SPEC: ARCH-05
    @Test
    fun `ARCH-05 no hardcoded Color literals outside the theme`() {
        val colorLiteral = Regex("""Color\(0x""")
        val offenders = sources(commonMain)
            .filterNot { under(it, "theme") }
            .filter { colorLiteral.containsMatchIn(it.readText()) }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "ARCH-05", "design colors come from the token catalog, never Color(0x…) literals.",
                offenders,
                "add/use a token in presentation/theme instead of the literal.",
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
            .filter { insetApi.containsMatchIn(it.readText()) }
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
