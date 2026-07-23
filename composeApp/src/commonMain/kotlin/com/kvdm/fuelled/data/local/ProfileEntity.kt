package com.kvdm.fuelled.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kvdm.fuelled.domain.model.Profile
import com.kvdm.fuelled.domain.model.ProfileGoals
import com.kvdm.fuelled.domain.model.ProfileIdentity
import com.kvdm.fuelled.domain.model.WeeklyStats

// ── Room entity for the Profile aggregate — the on-device SSOT the repository reads ──
// One FLAT row holds every scalar the screen shows; the repository maps it into the nested
// domain `Profile` at the seam, so domain never sees a Room type. A single profile always
// exists (id is a fixed key), so there is only ever one row.
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val planLabel: String,
    val calorieTarget: Int,
    val proteinGoalG: Int,
    val activity: String,
    val streakDays: Int,
    val avgProteinG: Int,
    val weightKg: Double,
)

/** Map the flat row into the nested domain [Profile] the screen renders. */
fun ProfileEntity.toDomain(): Profile = Profile(
    identity = ProfileIdentity(name = name, planLabel = planLabel, calorieTarget = calorieTarget),
    goals = ProfileGoals(calorieTarget = calorieTarget, proteinGoalG = proteinGoalG, activity = activity),
    weeklyStats = WeeklyStats(streakDays = streakDays, avgProteinG = avgProteinG, weightKg = weightKg),
)
