package com.kvdm.fuelled.presentation.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.domain.model.ReminderLead
import com.kvdm.fuelled.domain.model.Supplement
import com.kvdm.fuelled.domain.model.SupplementSchedule
import com.kvdm.fuelled.domain.model.SupplementTiming
import com.kvdm.fuelled.domain.model.WorkoutDay
import com.kvdm.fuelled.domain.model.WorkoutDayPlan
import com.kvdm.fuelled.presentation.supplements.RestingSupplement
import com.kvdm.fuelled.presentation.supplements.SupplementGroup
import com.kvdm.fuelled.presentation.supplements.SupplementStackUi
import com.kvdm.fuelled.presentation.supplements.SupplementsScreen
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rendered halves of the two features — what a user actually sees (WORK-03/WORK-04,
 * SUPP-09/SUPP-10). Stateless screens, fixture-driven, no VM and no Koin.
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutSurfacesTest {

    private val monday = LocalDate(2026, 8, 3)

    // SPEC: WORK-03
    @Test
    fun `a training day shows the card, and a rest day shows nothing at all`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TodayWorkoutCard(
                    day = WorkoutDay(
                        date = monday,
                        plan = WorkoutDayPlan("Upper body", LocalTime(18, 0), ReminderLead.DEFAULT),
                        done = false,
                    ),
                )
            }
        }

        onAllNodesWithTag("today_workout").assertCountEquals(1)
        onAllNodesWithText("Upper body").assertCountEquals(1)
        onAllNodesWithText("Today's training  ·  reminds 18:00").assertCountEquals(1)
    }

    // SPEC: WORK-03
    @Test
    fun `a rest day renders no card - rest is the plan working, not a zero to stare at`() =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    TodayWorkoutCard(day = WorkoutDay(monday, WorkoutDayPlan(), done = false))
                }
            }

            onAllNodesWithTag("today_workout").assertCountEquals(0)
        }

    // SPEC: WORK-04
    @Test
    fun `the done control reports the flipped state, and a done session says so`() =
        runComposeUiTest {
            var reported: Boolean? = null
            setContent {
                MaterialTheme {
                    TodayWorkoutCard(
                        day = WorkoutDay(monday, WorkoutDayPlan("Cardio", LocalTime(18, 0)), done = true),
                        onToggleDone = { reported = it },
                    )
                }
            }

            // Already done, so the caption says so rather than advertising a reminder that has
            // nothing left to remind about.
            onAllNodesWithText("Today's training  ·  done").assertCountEquals(1)

            onNodeWithTag("today_workout_done").performClick()
            assertEquals(false, reported, "tapping a done session undoes it — a mis-tap is not permanent")
        }

    // SPEC: SUPP-09
    @Test
    fun `a resting supplement is shown with its next date and no take control`() = runComposeUiTest {
        setContent { MaterialTheme { SupplementsScreen(stack = restingOnly()) } }

        onAllNodesWithTag("supplements_resting").assertCountEquals(1)
        onAllNodesWithTag("supplements_resting_pen").assertCountEquals(1)
        onAllNodesWithText("Next · Wed 5 Aug").assertCountEquals(1)
        onAllNodesWithText("0.5 ml  ·  Every 2 days").assertCountEquals(1)
        // No take control on an off-day row: an off-schedule dose is a decision made in the
        // editor, not a mis-tap on the screen opened every morning.
        onAllNodesWithTag("supplements_take_pen").assertCountEquals(0)
    }

    // SPEC: SUPP-09
    @Test
    fun `the summary counts only what is due today`() = runComposeUiTest {
        setContent { MaterialTheme { SupplementsScreen(stack = restingOnly()) } }

        // Nothing due — the denominator is zero, NOT one. An off-day pen counted in the total
        // would render every off day as a dose missed.
        onAllNodesWithText("of 0 due today taken").assertCountEquals(1)
    }

    // SPEC: SUPP-10
    @Test
    fun `a daily row carries no schedule caption - the feature costs the common case nothing`() =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SupplementsScreen(
                        stack = SupplementStackUi(
                            groups = listOf(
                                SupplementGroup(
                                    "Morning",
                                    listOf(
                                        Supplement("c", "Creatine", "5 g", SupplementTiming.MORNING, taken = false),
                                        Supplement(
                                            id = "t",
                                            name = "Testosterone",
                                            dose = "100 mg",
                                            timing = SupplementTiming.MORNING,
                                            taken = false,
                                            schedule = SupplementSchedule.OnDays(setOf(DayOfWeek.MONDAY)),
                                            remindAt = LocalTime(8, 0),
                                            leads = ReminderLead.DEFAULT,
                                        ),
                                    ),
                                ),
                            ),
                            takenCount = 0,
                            total = 2,
                            today = monday,
                        ),
                    )
                }
            }

            // The daily row renders its dose and NOTHING else — byte-identical to what it was
            // before schedules existed.
            onAllNodesWithText("5 g").assertCountEquals(1)
            // The scheduled row is the only one that pays for the feature.
            onAllNodesWithText("100 mg  ·  Mon  ·  reminds 08:00").assertCountEquals(1)
        }

    private fun restingOnly() = SupplementStackUi(
        groups = emptyList(),
        takenCount = 0,
        total = 0,
        resting = listOf(
            RestingSupplement(
                supplement = Supplement(
                    id = "pen",
                    name = "Injection pen",
                    dose = "0.5 ml",
                    timing = SupplementTiming.MORNING,
                    taken = false,
                    schedule = SupplementSchedule.EveryNDays(2, LocalDate(2026, 8, 5)),
                ),
                nextDue = LocalDate(2026, 8, 5),
            ),
        ),
        today = monday,
    )
}
