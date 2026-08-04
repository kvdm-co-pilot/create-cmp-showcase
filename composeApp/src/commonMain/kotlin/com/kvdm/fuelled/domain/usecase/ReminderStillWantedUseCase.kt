package com.kvdm.fuelled.domain.usecase

import com.kvdm.fuelled.domain.repository.SupplementRepository
import com.kvdm.fuelled.domain.repository.WorkoutRepository
import com.kvdm.fuelled.domain.result.AppResult
import kotlinx.datetime.LocalDate

/**
 * Is this reminder still worth posting, asked at the moment it fires (NOTIF-08)?
 *
 * **Arming and delivering are two different moments.** NOTIF-06 established this for the
 * plan-tomorrow nudge: the plan can change between the alarm being set and the alarm ringing,
 * so the emptiness question is asked again at delivery. Every reminder the ladder arms has the
 * same shape and needed the same guard — a dose alarm set at midnight for 08:00 knows nothing
 * about the dose being swallowed at 07:30, and a "training in 30 minutes" nudge for a session
 * already finished is the app not paying attention.
 *
 * Keyed off the reminder KEY and its due date, because after a process death that is all the
 * receiver has: an intent's extras. Anything this cannot recognise is posted — silence is the
 * worse failure, and an unknown key means a build newer than this code, not a stale reminder.
 *
 * Storage failures also post. "The database would not open" is not evidence the dose was
 * taken, and a reminder suppressed on unknown state is one the user never learns they missed —
 * the mirror of NOTIF-05's refusal to FIRE the nudge on unknown state, and correct for the
 * same reason: in each case the honest default is the one that cannot silently lose a fact.
 */
class ReminderStillWantedUseCase(
    private val supplements: SupplementRepository,
    private val workouts: WorkoutRepository,
    private val tomorrowUnplanned: TomorrowUnplannedUseCase,
) {
    /**
     * @param key the fired reminder's stable key.
     * @param dueDate the logical day it is ABOUT — absent on the daily reminders, which have
     *   no per-day fact to check.
     */
    suspend operator fun invoke(key: String, dueDate: LocalDate?): Boolean = when {
        key == PLAN_TOMORROW_KEY -> tomorrowUnplanned()

        key.startsWith(SUPPLEMENT_PREFIX) -> {
            val id = key.supplementId()
            val date = dueDate
            if (id == null || date == null) true
            else when (val taken = supplements.takenOn(date)) {
                is AppResult.Failure -> true
                is AppResult.Success -> id !in taken.value
            }
        }

        key.startsWith(WORKOUT_PREFIX) -> {
            val date = dueDate
            if (date == null) true
            else when (val done = workouts.doneBetween(date, date)) {
                is AppResult.Failure -> true
                is AppResult.Success -> date !in done.value
            }
        }

        else -> true
    }

    /**
     * The id out of `supp_<id>_<LEAD>`.
     *
     * Split from the END, not the start: ids are user-minted and may themselves contain an
     * underscore, so `split('_')[1]` would truncate `supp_vit_d3_AT_TIME` to `vit`. The rung is
     * a fixed enum name whose own underscores are known, so the tail is what can be trusted —
     * and a key that does not end in a rung this build knows is not ours to interpret.
     */
    private fun String.supplementId(): String? {
        val rung = LEAD_SUFFIXES.firstOrNull { endsWith(it) } ?: return null
        return removePrefix(SUPPLEMENT_PREFIX).removeSuffix(rung).ifEmpty { null }
    }

    private companion object {
        const val PLAN_TOMORROW_KEY = "plan_tomorrow"
        const val SUPPLEMENT_PREFIX = "supp_"
        const val WORKOUT_PREFIX = "workout_"

        /**
         * Written out rather than derived from `ReminderLead.entries` on purpose: these strings
         * are a WIRE FORMAT — they are baked into PendingIntents that survive an app upgrade,
         * so a future rename of the enum must be a deliberate, visible break here rather than
         * a silent one that strands every alarm already sitting in the OS.
         */
        val LEAD_SUFFIXES = listOf("_NIGHT_BEFORE", "_THIRTY_MIN", "_AT_TIME")
    }
}
