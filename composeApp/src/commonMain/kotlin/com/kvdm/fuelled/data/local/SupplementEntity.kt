package com.kvdm.fuelled.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kvdm.fuelled.domain.model.Supplement

// ── Room entity for the supplement stack — the on-device SSOT the repository reads/writes ──
// The data layer's own row shape; it maps to the domain `Supplement` at the repository seam so
// domain never sees a Room type. `timingOrder` gives the stack a stable, timing-grouped order
// (SQLite has no inherent row order to lean on); it stays a data concern and never crosses into
// the domain model. `taken` is the persisted tap-to-take state (SUPP-03).
@Entity(tableName = "supplements")
data class SupplementEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dose: String,
    val timing: String,
    val timingOrder: Int,
    val taken: Boolean,
)

fun SupplementEntity.toDomain(): Supplement = Supplement(
    id = id,
    name = name,
    dose = dose,
    timing = timing,
    taken = taken,
)
