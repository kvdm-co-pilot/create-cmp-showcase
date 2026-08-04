package com.kvdm.fuelled.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kvdm.fuelled.core.time.parseIsoDateOrNull
import com.kvdm.fuelled.domain.model.ReminderLead
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.SupplementSchedule
import com.kvdm.fuelled.domain.model.SupplementTiming
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

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
    // ── The schedule (SUPP-08, schema v15) ───────────────────────────────────────────────
    // A sealed hierarchy flattened into columns rather than stored as JSON in one: typed
    // columns cannot hold a malformed shape, they are queryable, and Room can migrate them.
    // Only the columns the kind actually uses carry meaning — `scheduleDays` is empty for a
    // cadence, `scheduleN`/`scheduleAnchor` are unused for weekdays — which is the ordinary
    // cost of flattening a sum type and is contained entirely by [toSchedule].
    val scheduleKind: String = ScheduleKind.DAILY.name,
    /** CSV of [DayOfWeek] names for [SupplementSchedule.OnDays]; empty otherwise. */
    val scheduleDays: String = "",
    /** The cadence for [SupplementSchedule.EveryNDays]. */
    val scheduleN: Int = 2,
    /** ISO date the cadence counts from; empty when the kind does not use one. */
    val scheduleAnchor: String = "",
    // ── The reminder ladder (SUPP-12, schema v15) ────────────────────────────────────────
    /** Minute of the day the reminders hang off; null means this supplement never reminds. */
    val remindAtMinute: Int? = null,
    /** CSV of [ReminderLead] names. Empty means no rung is armed. */
    val leads: String = "",
)

/** The stored discriminator for [SupplementSchedule]'s three shapes. */
enum class ScheduleKind { DAILY, ON_DAYS, EVERY_N_DAYS }

/**
 * One supplement taken on one logical day (SUPP-07) — the same shape as a water tick, for the
 * same reason.
 *
 * A supplement stack was a DAILY routine when this table was made: creatine this morning says
 * nothing about tomorrow morning. Held as a `taken` flag on the catalog row it never reset, so
 * Thursday opened still claiming Wednesday's "2 of 3 taken" and the count could only ever go up
 * (observed on-device, 2026-07-29). Per-day by construction: a new logical day simply has no
 * rows, so it starts at none-taken with nothing to reset and no boundary job to run.
 *
 * SUPP-08 made the stack non-daily, and this shape absorbed that without a change: a dose is
 * still a fact about a date, and a date the schedule does not fall due on simply never gets a
 * row.
 */
@Entity(tableName = "supplement_taken", primaryKeys = ["logicalDate", "supplementId"])
data class SupplementTakenEntity(
    val logicalDate: String,
    val supplementId: String,
)

/** Map a catalog row plus the day's doses into the domain model (SUPP-07/SUPP-08). */
fun SupplementEntity.toDomain(takenToday: Set<String>): Supplement = Supplement(
    id = id,
    name = name,
    dose = dose,
    // SET-06: an unrecognised stored value reads as MORNING rather than throwing — a row
    // written by an older build must not be able to crash the stack it belongs to.
    timing = SupplementTiming.of(timing),
    taken = id in takenToday,
    schedule = toSchedule(),
    remindAt = remindAtMinute?.let { LocalTime.fromSecondOfDay(it.coerceIn(0, MINUTES_IN_DAY - 1) * 60) },
    leads = leads.decodeLeads(),
)

/**
 * Read the flattened columns back into the sealed schedule (SUPP-08).
 *
 * Every unreadable case falls back to [SupplementSchedule.Daily] rather than throwing, on the
 * same principle as [SupplementTiming.of]: a row written by a future build, a hand-edited
 * database or a half-finished migration must not be able to crash the Supplements tab. Daily
 * is the safe fallback specifically because it OVER-shows — the user sees a dose on a day it
 * is not due, which is visible and correctable, rather than silently never seeing it again.
 */
fun SupplementEntity.toSchedule(): SupplementSchedule =
    when (ScheduleKind.entries.firstOrNull { it.name == scheduleKind }) {
        ScheduleKind.ON_DAYS -> SupplementSchedule.OnDays(scheduleDays.decodeDays())
        ScheduleKind.EVERY_N_DAYS -> {
            val anchor = parseIsoDateOrNull(scheduleAnchor)
            if (anchor == null) SupplementSchedule.Daily
            else SupplementSchedule.EveryNDays(
                n = scheduleN.coerceIn(SupplementSchedule.CADENCE_RANGE),
                anchor = anchor,
            )
        }
        ScheduleKind.DAILY, null -> SupplementSchedule.Daily
    }

/**
 * Map a supplement back to its row (SET-04/SET-05, SUPP-08).
 *
 * `timingOrder` is written from the SAME enum the `timing` column holds, so the ordering the
 * DAO sorts by and the group the screen buckets on are one fact expressed twice — they cannot
 * drift, which is the whole reason SET-06 closed the set.
 */
fun Supplement.toEntity(): SupplementEntity = SupplementEntity(
    id = id,
    name = name,
    dose = dose,
    timing = timing.name,
    timingOrder = timing.ordinal,
    scheduleKind = when (schedule) {
        is SupplementSchedule.Daily -> ScheduleKind.DAILY
        is SupplementSchedule.OnDays -> ScheduleKind.ON_DAYS
        is SupplementSchedule.EveryNDays -> ScheduleKind.EVERY_N_DAYS
    }.name,
    scheduleDays = (schedule as? SupplementSchedule.OnDays)?.days.encodeDays(),
    scheduleN = (schedule as? SupplementSchedule.EveryNDays)?.n ?: 2,
    scheduleAnchor = (schedule as? SupplementSchedule.EveryNDays)?.anchor?.toString().orEmpty(),
    remindAtMinute = remindAt?.let { it.hour * 60 + it.minute },
    leads = leads.encodeLeads(),
)

/** Map a dose into its row. */
fun supplementTakenEntity(date: LocalDate, id: String): SupplementTakenEntity =
    SupplementTakenEntity(logicalDate = date.toString(), supplementId = id)

// ── CSV codecs (SUPP-08/SUPP-12) ─────────────────────────────────────────────────────────
// Small, total, and shared by the supplement rows and the workout week: both store a set of
// enum names, and one pair of functions means one place where an unknown name is dropped.

internal const val MINUTES_IN_DAY = 24 * 60

internal fun Set<DayOfWeek>?.encodeDays(): String =
    this.orEmpty().sortedBy { it.ordinal }.joinToString(",") { it.name }

internal fun String.decodeDays(): Set<DayOfWeek> = split(',')
    .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name.trim() } }
    .toSet()

internal fun Set<ReminderLead>.encodeLeads(): String =
    sortedBy { it.ordinal }.joinToString(",") { it.name }

internal fun String.decodeLeads(): Set<ReminderLead> = split(',')
    .mapNotNull { ReminderLead.of(it.trim()) }
    .toSet()
