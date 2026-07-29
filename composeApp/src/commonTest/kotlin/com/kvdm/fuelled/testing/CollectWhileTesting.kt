package com.kvdm.fuelled.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Start collecting [flow] for the duration of the test.
 *
 * `stateIn(WhileSubscribed)` produces NOTHING until something subscribes — that is the whole
 * point in production (a screen that is gone costs nothing) and the standard trap in tests,
 * where reading `state.value` with no collector returns the initial `Loading` forever. This is
 * the documented answer: a background collection that lives as long as the test.
 */
fun TestScope.keepCollecting(flow: Flow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
}
