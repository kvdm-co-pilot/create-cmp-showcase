package com.kvdm.fuelled.presentation.progress

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.kvdm.fuelled.presentation.navigation.Routes
import com.kvdm.fuelled.testing.awaitNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

/**
 * The Progress surface's doors (HIST-02). The finding this whole slice came from: the day
 * cards stated a verdict on a day and offered no way to reach it.
 */
@OptIn(ExperimentalTestApi::class)
class ProgressScreenTest {

    // SPEC: HIST-02
    @Test
    fun `a day card opens that logical day's plan, carrying its own date`() = runComposeUiTest {
        var opened: LocalDate? = null
        setContent { MaterialTheme { ProgressScreen(onOpenDay = { opened = it }) } }

        awaitNode(hasTestTag("week_day_2026-07-19"))
        // Sunday — the weak day in the fixture, and the one you would actually open.
        onNodeWithTag("week_day_2026-07-19").performScrollTo().performClick()
        assertEquals(LocalDate(2026, 7, 19), opened, "the card carries its OWN date")
        assertEquals("plan/2026-07-19", Routes.mealPlan(opened!!), "and it opens that day's plan")

        // A second card answers differently — the proof a defaulted control could not give.
        onNodeWithTag("week_day_2026-07-17").performScrollTo().performClick()
        assertEquals(LocalDate(2026, 7, 17), opened)
    }

    // SPEC: HIST-02
    @Test
    fun `every day in the week section is a door, today included`() = runComposeUiTest {
        val opened = mutableListOf<LocalDate>()
        setContent { MaterialTheme { ProgressScreen(onOpenDay = { opened += it }) } }

        awaitNode(hasTestTag("week_day_2026-07-16"))
        val week = ProgressUi().history.week.days
        week.forEach { day ->
            onNodeWithTag("week_day_${day.date}").performScrollTo().performClick()
        }
        // Including today: the current day is as worth opening as any other — it is the one
        // you are still filling in.
        assertEquals(week.map { it.date }, opened)
    }
}
