package com.kvdm.fuelled.data.remote

import com.kvdm.fuelled.core.time.DEFAULT_DAY_START_HOUR
import com.kvdm.fuelled.core.time.RealTimeSignal
import com.kvdm.fuelled.core.time.TimeSignal
import com.kvdm.fuelled.core.time.currentDay
import com.kvdm.fuelled.core.time.days
import com.kvdm.fuelled.data.local.SupplementDao
import com.kvdm.fuelled.data.local.SupplementEntity
import com.kvdm.fuelled.data.local.supplementTakenEntity
import com.kvdm.fuelled.data.local.toDomain
import com.kvdm.fuelled.data.local.toEntity
import com.kvdm.fuelled.data.asAppResult
import com.kvdm.fuelled.data.suspendRunCatching
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.TimeZone
import com.kvdm.fuelled.domain.model.DomainError
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.SupplementTiming
import com.kvdm.fuelled.domain.repository.SupplementRepository
import com.kvdm.fuelled.domain.result.AppResult

/**
 * The Room-backed supplement stack — the fully-wired data source (mirrors FoodRepositoryImpl).
 * Reads/writes the on-device [SupplementDao] (Room), seeding a realistic stack on first run so
 * the app has content offline from install.
 *
 * **Two tables, one model (SUPP-07).** The catalog says what the user takes; a per-day dose
 * table says what they took today. The domain's `Supplement.taken` is the JOIN of the two at
 * the current logical day — so a new day starts with nothing taken because it has no rows yet,
 * not because anything reset it. The same shape as water ticks (PLAN-10), and for the same
 * reason: a daily fact stored as a flag on a permanent row is a fact with no day attached.
 *
 * **The day in view is derived, never stored (MEAL-02).** The read stream re-derives it from
 * [time], so a screen left open across 04:00 rolls over on its own. A WRITE reads the clock
 * once instead: which day a tap belongs to is decided at the tap.
 *
 * The repository is the ONLY exception-translation point: every DAO call runs inside
 * suspendRunCatching (data/AppResultCatching.kt), which maps infrastructure exceptions to typed
 * [DomainError] values and ALWAYS rethrows CancellationException. Seed data lives here in the
 * data layer (ARCH-09) — it never reaches for the presentation layer's preview fixtures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SupplementRepositoryImpl(
    private val dao: SupplementDao,
    private val time: TimeSignal = RealTimeSignal(),
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
) : SupplementRepository {

    override suspend fun getStack(): AppResult<List<Supplement>> = suspendRunCatching {
        ensureSeeded()
        val today = time.currentDay(dayStartHour, zone)
        val takenToday = dao.takenOn(today.toString()).map { it.supplementId }.toSet()
        dao.getAll().map { it.toDomain(takenToday) }
    }

    /**
     * The stack, observed. Room re-emits on every dose written, so Today's bucket row follows a
     * tap on the Supplements tab; and `days` re-collects at the day boundary, so the count
     * empties at 04:00 without anyone reloading.
     */
    override fun observeStack(): Flow<AppResult<List<Supplement>>> =
        time.days(dayStartHour, zone)
            .flatMapLatest { day ->
                combine(dao.getAllStream(), dao.takenStream(day.toString())) { rows, taken ->
                    val takenToday = taken.map { it.supplementId }.toSet()
                    rows.map { it.toDomain(takenToday) }
                }
            }
            .onStart { ensureSeeded() }
            .asAppResult()

    override suspend fun setTaken(id: String, taken: Boolean): AppResult<Unit> = suspendRunCatching {
        val today = time.currentDay(dayStartHour, zone)
        if (taken) dao.insertTaken(supplementTakenEntity(today, id))
        else dao.clearTaken(today.toString(), id)
    }

    override suspend fun save(supplement: Supplement): AppResult<Unit> = suspendRunCatching {
        dao.upsert(supplement.toEntity())
    }

    override suspend fun delete(id: String): AppResult<Unit> = suspendRunCatching {
        dao.deleteById(id)
    }

    /** Seed the stack on first run so the app ships with content offline (idempotent). */
    private suspend fun ensureSeeded() {
        if (dao.count() == 0) dao.upsertAll(SEED_STACK)
    }

    private companion object {
        // The starter stack, seeded once into Room. Lives in the data layer (the source owns
        // its seed data, ARCH-09); the presentation layer keeps its own preview fixture.
        //
        // Nothing here is pre-taken. A seeded dose would be the app claiming the user swallowed
        // something they did not — the same pretence the meal seed was purged of (PLAN-03), and
        // the reason the doses live in their own table where a fresh install simply has none.
        val SEED_STACK = listOf(
            SupplementEntity("1", "Creatine", "5 g", SupplementTiming.MORNING.name, SupplementTiming.MORNING.ordinal),
            SupplementEntity("2", "Vitamin D3", "2000 IU", SupplementTiming.MORNING.name, SupplementTiming.MORNING.ordinal),
            SupplementEntity("3", "Omega-3", "1 g", SupplementTiming.MORNING.name, SupplementTiming.MORNING.ordinal),
            SupplementEntity("4", "Caffeine", "200 mg", SupplementTiming.PRE_WORKOUT.name, SupplementTiming.PRE_WORKOUT.ordinal),
            SupplementEntity("5", "Beta-alanine", "3 g", SupplementTiming.PRE_WORKOUT.name, SupplementTiming.PRE_WORKOUT.ordinal),
            SupplementEntity("6", "Magnesium", "400 mg", SupplementTiming.EVENING.name, SupplementTiming.EVENING.ordinal),
        )
    }
}
