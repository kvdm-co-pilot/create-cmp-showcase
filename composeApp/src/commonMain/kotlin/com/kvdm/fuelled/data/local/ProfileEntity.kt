package com.kvdm.fuelled.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.model.ProfileGoals
import com.kvdm.fuelled.domain.model.ProfileIdentity
import com.kvdm.fuelled.domain.model.WeeklyStats

// ── Room entity for the Profile aggregate — identity and stats, NEVER goals ──
// One FLAT row holds identity and the weekly stats; the repository maps it into the nested
// domain `Profile` at the seam, so domain never sees a Room type. A single profile always
// exists (id is a fixed key), so there is only ever one row.
//
// The goal numbers are deliberately ABSENT (PERS-01, schema v9): this row used to carry
// `calorieTarget`/`proteinGoalG` alongside the today-goal row's copies, and the two agreed
// only because their seed constants matched (usability-pass F5). Targets live in
// [TodayGoalEntity] — the one goal store — and Profile reads them from there.
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val planLabel: String,
    val activity: String,
    val streakDays: Int,
    val avgProteinG: Int,
    val weightKg: Double,
)

/** Map the flat row + the ONE goal store's targets into the domain [Profile] (PERS-01). */
fun ProfileEntity.toDomain(goal: TodayGoalEntity): Profile = Profile(
    identity = ProfileIdentity(name = name, planLabel = planLabel, calorieTarget = goal.targetKcal),
    goals = ProfileGoals(calorieTarget = goal.targetKcal, proteinGoalG = goal.proteinTargetG, activity = activity),
    weeklyStats = WeeklyStats(streakDays = streakDays, avgProteinG = avgProteinG, weightKg = weightKg),
)
