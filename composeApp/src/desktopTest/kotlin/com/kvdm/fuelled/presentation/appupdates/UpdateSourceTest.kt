package com.kvdm.fuelled.presentation.appupdates

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * UPD-01, verified where it actually matters: by SOURCE SCAN, not by a network call.
 *
 * The clause makes two promises and only one of them is about reaching GitHub. The other —
 * "no token ships in the APK" — is a property of the code as written, and a live HTTP test
 * would not check it at all: a request that happens to carry no credential today proves
 * nothing about the one someone adds next month. A scan does, and it fails at build time
 * rather than after a release is already published.
 */
class UpdateSourceTest {

    private val source = File("../composeApp/src/commonMain/kotlin/com/kvdm/fuelled/data/remote/UpdateRepositoryImpl.kt")

    // SPEC: UPD-01
    @Test
    fun `the release check reads the public releases endpoint and ships no credential`() {
        if (!source.exists()) fail("UpdateRepositoryImpl.kt not found at ${source.absolutePath}")
        val text = source.readText()
        val code = text.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")

        assertTrue(
            code.contains("api.github.com/repos/") && code.contains("/releases/latest"),
            "UPD-01: the source of truth is the releases API",
        )

        // An APK is a public artifact: a credential inside one is a published credential.
        // These are the shapes a token would take if someone added one — an Authorization
        // header, or a bearer/token literal.
        val credentialShapes = listOf("Authorization", "Bearer ", "ghp_", "github_pat_")
        val found = credentialShapes.filter { code.contains(it, ignoreCase = false) }
        assertTrue(
            found.isEmpty(),
            "UPD-01: no credential ships in the APK — found ${found.joinToString()}. " +
                "This repository is public and the endpoint answers unauthenticated. If a token " +
                "becomes genuinely necessary (a private repo), it belongs behind a proxy the app " +
                "calls, not inside an artifact anyone can download and unzip.",
        )
    }
}
