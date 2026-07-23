package com.kvdm.fuelled.domain.model

/**
 * The Profile aggregate's domain models (see specs/profile.spec.md). Pure Kotlin, no framework
 * types — the shape the presentation renders and the data layer maps its flat `profile` row
 * into. Presentation owns display concerns (the calorie thousands separator, the settings-list
 * labels) — the domain carries only the values, never their formatting (ARCH-02).
 */

/** The identity line (PROF-01): who the user is, their current plan, and their calorie target. */
data class ProfileIdentity(
    val name: String,
    val planLabel: String,
    val calorieTarget: Int,
)

/** The daily goals (PROF-02): calorie target, protein goal, and activity. */
data class ProfileGoals(
    val calorieTarget: Int,
    val proteinGoalG: Int,
    val activity: String,
)

/** The week's rolled-up stats (PROF-03): day streak, average protein, current weight. */
data class WeeklyStats(
    val streakDays: Int,
    val avgProteinG: Int,
    val weightKg: Double,
)

/**
 * The aggregate the Profile screen renders: identity, daily goals, and the week's stats. A
 * profile always exists (the source seeds one on first run), so there is no dataless arm — the
 * screen is Loading, Content, or Error only. The settings-list labels (units, reminders,
 * connected apps, account) are static presentation, not domain data (PROF-04).
 */
data class Profile(
    val identity: ProfileIdentity,
    val goals: ProfileGoals,
    val weeklyStats: WeeklyStats,
)
