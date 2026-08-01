package com.kvdm.fuelled.domain.model

import kotlin.time.Instant

/**
 * What the app knows about itself (START-01/START-02) and how its owner has set it up
 * (SET-02/SET-07).
 *
 * [startedAt] is the instant this install was first opened — not a user setting and not a
 * preference: it is the boundary before which this app cannot make claims about your day.
 * Journey J3 found the defect it exists to fix: a first-ever day greeted its user with two
 * MISSED tags for meals eaten before the app was installed.
 *
 * [settings] rides the same one row (settings decision D8) — one read, one observed stream,
 * so changing a unit re-renders every surface with no reload (RS-01) instead of each
 * preference arriving from its own table on its own schedule.
 */
data class AppState(
    val onboarded: Boolean,
    val startedAt: Instant,
    val settings: AppSettings = AppSettings(),
)

/** The default prep lead (PLAN-07) — what a fresh install reminds you at until you say otherwise. */
const val DEFAULT_PREP_LEAD_MINUTES: Int = 30

/**
 * SET-07: the leads offered. A closed set rather than a typed minutes field, because a free
 * text box invites `-15` and `9999` and then needs the guard anyway. Zero is legitimate and
 * means *at the meal time* — the behaviour before the journeys pass, still available to
 * anyone who wants it.
 */
val PREP_LEAD_CHOICES: List<Int> = listOf(0, 15, 30, 45, 60, 90, 120)

/** The bound a stored lead must satisfy — the guard for values arriving from anywhere else. */
val PREP_LEAD_RANGE: IntRange = 0..120

/** The user's choices. Every field has a default, so a fresh row is already valid. */
data class AppSettings(
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val prepLeadMinutes: Int = DEFAULT_PREP_LEAD_MINUTES,
)
