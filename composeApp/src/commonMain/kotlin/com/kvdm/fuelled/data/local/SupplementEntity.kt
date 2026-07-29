package com.kvdm.fuelled.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kvdm.fuelled.domain.model.Supplement
import kotlinx.datetime.LocalDate

// ── Room entities for the supplement stack — the on-device SSOT the repository reads/writes ──
// The data layer's own row shapes; they map to the domain `Supplement` at the repository seam so
// domain never sees a Room type. `timingOrder` gives the stack a stable, timing-grouped order
// (SQLite has no inherent row order to lean on); it stays a data concern and never crosses into
// the domain model.
//
// The CATALOG (what you take) and the day's DOSES (what you took today) are two tables, not one
// row with a flag — see [SupplementTakenEntity].
@Entity(tableName = "supplements")
data class SupplementEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dose: String,
    val timing: String,
    val timingOrder: Int,
)

/**
 * One supplement taken on one logical day (SUPP-07) — the same shape as a water tick, for the
 * same reason.
 *
 * A supplement stack is a DAILY routine: creatine this morning says nothing about tomorrow
 * morning. Held as a `taken` flag on the catalog row it never reset, so Thursday opened still
 * claiming Wednesday's "2 of 3 taken" and the count could only ever go up (observed on-device,
 * 2026-07-29). Per-day by construction: a new logical day simply has no rows, so it starts at
 * none-taken with nothing to reset and no boundary job to run.
 */
@Entity(tableName = "supplement_taken", primaryKeys = ["logicalDate", "supplementId"])
data class SupplementTakenEntity(
    val logicalDate: String,
    val supplementId: String,
)

/** Map a catalog row plus the day's doses into the domain model (SUPP-07). */
fun SupplementEntity.toDomain(takenToday: Set<String>): Supplement = Supplement(
    id = id,
    name = name,
    dose = dose,
    timing = timing,
    taken = id in takenToday,
)

/** Map a dose into its row. */
fun supplementTakenEntity(date: LocalDate, id: String): SupplementTakenEntity =
    SupplementTakenEntity(logicalDate = date.toString(), supplementId = id)
