package com.kvdm.fuelled.presentation.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The scheme is the one place every animation reads its spec through (MOTION-02/03), so its
 * arithmetic is what makes "reduced motion" and "instant in tests" true by construction.
 */
class MotionSchemeTest {

    // SPEC: MOTION-03
    @Test
    fun `Instant snaps every timed spec and every spring`() {
        val timed: FiniteAnimationSpec<Float> = MotionScheme.Instant.tween(FuelledMotion.Duration.Expressive)
        val sprung: FiniteAnimationSpec<Float> = MotionScheme.Instant.spring(FuelledMotion.Springs.Lively)
        assertIs<SnapSpec<Float>>(timed)
        assertIs<SnapSpec<Float>>(sprung)
    }

    // SPEC: MOTION-03
    @Test
    fun `Reduced fades no longer than Quick with linear easing, and springs snap`() {
        val timed = MotionScheme.Reduced.tween<Float>(FuelledMotion.Duration.Celebration, FuelledMotion.Easings.Enter)
        val tween = assertIs<TweenSpec<Float>>(timed)
        assertEquals(FuelledMotion.Duration.Quick, tween.durationMillis, "a Reduced fade is capped at Quick")
        assertEquals(FuelledMotion.Easings.Linear, tween.easing, "and is linear — no curve to notice")
        assertIs<SnapSpec<Float>>(MotionScheme.Reduced.spring<Float>(FuelledMotion.Springs.Weighty))
        // A spec already shorter than Quick keeps its own length.
        val short = assertIs<TweenSpec<Float>>(MotionScheme.Reduced.tween<Float>(FuelledMotion.Duration.Instant))
        assertEquals(0, short.durationMillis)
    }

    // SPEC: MOTION-03
    @Test
    fun `Full uses the token's own duration, easing, damping and stiffness unchanged`() {
        val tween = assertIs<TweenSpec<Float>>(
            MotionScheme.Full.tween<Float>(FuelledMotion.Duration.Emphasized, FuelledMotion.Easings.Exit),
        )
        assertEquals(FuelledMotion.Duration.Emphasized, tween.durationMillis)
        assertEquals(FuelledMotion.Easings.Exit, tween.easing)
        val spring = assertIs<SpringSpec<Float>>(MotionScheme.Full.spring<Float>(FuelledMotion.Springs.Lively))
        assertEquals(FuelledMotion.Springs.Lively.dampingRatio, spring.dampingRatio)
        assertEquals(FuelledMotion.Springs.Lively.stiffness, spring.stiffness)
    }

    // SPEC: MOTION-06
    @Test
    fun `a stagger is index times the step, capped at the sixth item`() {
        assertEquals(0, FuelledMotion.staggerDelayMs(0))
        assertEquals(FuelledMotion.StaggerStepMs, FuelledMotion.staggerDelayMs(1))
        assertEquals(6 * FuelledMotion.StaggerStepMs, FuelledMotion.staggerDelayMs(6))
        // The seventh item and beyond share the sixth's delay — a long list never arrives late.
        assertEquals(FuelledMotion.staggerDelayMs(6), FuelledMotion.staggerDelayMs(7))
        assertEquals(FuelledMotion.staggerDelayMs(6), FuelledMotion.staggerDelayMs(40))
        assertEquals(0, FuelledMotion.staggerDelayMs(-3), "a negative index is the first item")
    }

    // SPEC: MOTION-06
    @Test
    fun `only the Full scheme staggers`() {
        assertEquals(0, MotionScheme.Reduced.staggerDelayMs(5))
        assertEquals(0, MotionScheme.Instant.staggerDelayMs(5))
        assertEquals(FuelledMotion.staggerDelayMs(5), MotionScheme.Full.staggerDelayMs(5))
        assertTrue(MotionScheme.Full.moves && !MotionScheme.Reduced.moves && !MotionScheme.Instant.moves)
        assertTrue(MotionScheme.Full.loops && !MotionScheme.Reduced.loops && !MotionScheme.Instant.loops)
    }
}
