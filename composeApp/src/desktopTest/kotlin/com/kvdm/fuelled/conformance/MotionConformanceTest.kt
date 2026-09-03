package com.kvdm.fuelled.conformance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The motion gates — `specs/motion.spec.md`'s source-level clauses as executable checks, in
 * the same dependency-free source-scanning style as [ArchitectureConformanceTest]. A screen
 * that reaches for `tween(300)` gets a named clause violation, not a style nit.
 */
class MotionConformanceTest {

    /** "/" + "*" — see the NOTE at its use site. */
    private val BLOCK_OPEN = "/" + "*"

    private val commonMain = File("src/commonMain/kotlin")

    private fun sources(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private val trailingLineComment = Regex("""(?<!:)//.*""")

    private fun nonCommentLines(file: File): List<String> =
        file.readLines()
            .filterNot {
                val t = it.trimStart()
            // NOTE: the two-character sequences that open and close a block comment are
            // built rather than written as literals. The lane's citation scanner counts
            // those pairs without skipping string literals, so a written "/" + "*" here
            // makes it believe a block comment opened and silently drops every `// SPEC:`
            // tag below it in this file (it dropped all four, 2026-09-03). Reported
            // upstream; this keeps the file's citations visible meanwhile.
                t.startsWith("//") || t.startsWith("*") || t.startsWith(BLOCK_OPEN)
            }
            .map { it.replace(trailingLineComment, "") }

    private fun under(file: File, segment: String): Boolean =
        file.path.replace(File.separatorChar, '/').contains("/$segment/")

    private fun violation(clause: String, rule: String, offenders: List<String>, fix: String): String =
        "[$clause] $rule\n  offenders:\n" + offenders.joinToString("\n") { "    $it" } + "\n  fix: $fix"

    // SPEC: MOTION-01
    @Test
    fun `MOTION-01 no animation spec literals outside the theme and the component registry`() {
        // A spec literal: the factory name followed by an opening paren. `motion.tween(` and
        // `motion.spring(` are the scheme's helpers, not literals — the preceding `.` excludes
        // them, as does any other receiver.
        val literal = Regex("""(?<![\w.])(tween|spring|keyframes|snap|repeatable|infiniteRepeatable)\(""")
        val offenders = sources(commonMain)
            .filterNot { under(it, "theme") || under(it, "components") }
            .filter { file -> nonCommentLines(file).any { literal.containsMatchIn(it) } }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "MOTION-01", "motion values come from FuelledMotion through the MotionScheme helpers, never spec literals.",
                offenders,
                "replace the literal with LocalMotion.current.tween(FuelledMotion.Duration.X) / .spring(FuelledMotion.Springs.Y), or compose a registry primitive.",
            )
        )
    }

    // SPEC: MOTION-01
    @Test
    fun `MOTION-01 no raw animate-as-state calls with inline specs outside the registry`() {
        val raw = Regex("""animate\w*AsState\(""")
        val offenders = sources(commonMain)
            .filterNot { under(it, "theme") || under(it, "components") }
            .filter { file -> nonCommentLines(file).any { raw.containsMatchIn(it) } }
            .map { it.path }
        if (offenders.isNotEmpty()) fail(
            violation(
                "MOTION-01", "screens animate through the registry's primitives, not animate*AsState with a hand-picked spec.",
                offenders,
                "use Modifier.pressable / enterRise / AnimatedNumber / TickButton, or add a primitive to the registry with a story.",
            )
        )
    }

    // SPEC: MOTION-04
    @Test
    fun `MOTION-04 the nav host declares its transitions once, from the tokens, and no destination overrides them`() {
        val host = File(commonMain, "com/kvdm/fuelled/presentation/navigation/AppNavHost.kt")
        val text = nonCommentLines(host).joinToString("\n")
        val declared = listOf("enterTransition", "exitTransition", "popEnterTransition", "popExitTransition")
        val missing = declared.filterNot { Regex("""\b$it\s*=""").containsMatchIn(text) }
        if (missing.isNotEmpty()) fail(
            violation("MOTION-04", "AppNavHost declares every transition on the host.", missing, "declare it on NavHost(...), built from FuelledMotion through the scheme.")
        )
        // Exactly one declaration each — a second would be a per-destination override.
        val overrides = declared.filter { Regex("""\b$it\s*=""").findAll(text).count() > 1 }
        if (overrides.isNotEmpty()) fail(
            violation("MOTION-04", "no composable(...) registration overrides the host's transitions.", overrides, "remove the per-destination transition.")
        )
        assertTrue(text.contains("FuelledMotion.ScreenSlide") && text.contains("FuelledMotion.ScreenLead"), "[MOTION-04] the slide distances are the tokens")
        assertTrue(text.contains("motion.tween("), "[MOTION-04] every transition spec goes through the scheme")
    }

    // SPEC: FOODS-09
    @Test
    fun `FOODS-09 the food row and the detail header share one title key`() {
        val foods = File(commonMain, "com/kvdm/fuelled/presentation/foods")
        val row = File(foods, "FoodsScreen.kt").readText()
        val detail = File(foods, "FoodDetailScreen.kt").readText()
        val key = Regex("""sharedTitle\("food-title-\$\{?\w+(\.id)?\}?"\)""")
        assertTrue(key.containsMatchIn(row), "[FOODS-09] FoodsScreen declares the row title as sharedTitle(\"food-title-<id>\")")
        assertTrue(key.containsMatchIn(detail), "[FOODS-09] FoodDetailScreen declares the header title as sharedTitle(\"food-title-<id>\")")
    }
}
