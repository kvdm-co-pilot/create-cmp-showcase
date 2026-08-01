package com.kvdm.fuelled.domain.model

import kotlin.time.Instant

/**
 * What the app knows about itself (START-01/START-02).
 *
 * [startedAt] is the instant this install was first opened — not a user setting and not a
 * preference: it is the boundary before which this app cannot make claims about your day.
 * Journey J3 found the defect it exists to fix: a first-ever day greeted its user with two
 * MISSED tags for meals eaten before the app was installed.
 */
data class AppState(
    val onboarded: Boolean,
    val startedAt: Instant,
)
