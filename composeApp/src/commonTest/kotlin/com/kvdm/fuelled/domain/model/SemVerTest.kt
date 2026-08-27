package com.kvdm.fuelled.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ordering UPD-02 turns on, tested directly — it is a pure function and deserves to fail
 * here rather than three layers up in a use case.
 */
class SemVerTest {

    // SPEC: UPD-02
    @Test
    fun `ordering is numeric per component, not lexical`() {
        // The whole reason this type exists: "0.10.0" sorts BEFORE "0.9.0" as a string.
        assertTrue(SemVer(0, 10, 0) > SemVer(0, 9, 0))
        assertTrue(SemVer(1, 0, 0) > SemVer(0, 99, 99))
        assertTrue(SemVer(0, 6, 10) > SemVer(0, 6, 9))
        assertEquals(SemVer(0, 6, 0), SemVer(0, 6, 0))
    }

    // SPEC: UPD-02
    @Test
    fun `parsing accepts exactly three integers and refuses everything else`() {
        assertEquals(SemVer(0, 6, 0), SemVer.parse("0.6.0"))
        assertEquals(SemVer(1, 2, 3), SemVer.parse(" 1.2.3 "), "surrounding space is not a typo worth failing")

        // Each of these has an ordering someone could invent, and inventing one is how a
        // downgrade ships — so none of them parse.
        assertNull(SemVer.parse(null))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("0.6"), "two components leave the third to a guess")
        assertNull(SemVer.parse("0.6.0.1"))
        assertNull(SemVer.parse("v0.6.0"), "the tag's v prefix is not part of the number")
        assertNull(SemVer.parse("0.6.0-rc1"), "pre-release ordering is not defined here")
        assertNull(SemVer.parse("0.x.0"))
        assertNull(SemVer.parse("-1.0.0"))
    }
}
