package com.kvdm.fuelled.presentation.theme

import androidx.compose.runtime.Composable

/**
 * Whether the person asked the platform to reduce motion (MOTION-02). Read from the OS
 * setting they already made — Android's animator duration scale, iOS's Reduce Motion —
 * never from an in-app toggle: one setting, theirs. Desktop has no such setting and answers
 * `false`; the preview harness and tests do not consult this at all — they pass
 * [MotionScheme.Instant] explicitly.
 */
@Composable
expect fun platformReducedMotion(): Boolean
