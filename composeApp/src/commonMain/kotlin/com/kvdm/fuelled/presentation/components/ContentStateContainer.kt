package com.kvdm.fuelled.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.kvdm.fuelled.presentation.theme.FuelledTokens

/**
 * The state dispatcher for any data-backed screen: folds a [ContentUiState] into one of
 * four rendered arms — loading, error, empty, content. The container owns the three
 * non-content arms' UI and their derived testTags; the screen supplies only its content
 * shape in the trailing slot. Every screen that loads data uses it; a feature whose
 * state machine genuinely outgrows the four arms defines its own sealed type and skips
 * the container — that divergence is visible in review.
 *
 * @param state Current arm. The fold is exhaustive; exactly one arm renders.
 * @param screenTag Feature slug ("home"). Required, not defaulted: tests, golden trees,
 *   and E2E flows all key on the derived tags `<screenTag>_loading` / `_error` /
 *   `_retry` / `_empty`, so tagging cannot be left to each caller.
 * @param onRetry Non-null renders a retry control (`<screenTag>_retry`) in the default
 *   error arm.
 * @param loading Loading slot; defaults to a list-shaped skeleton
 *   (`ContentStateDefaults.ListSkeleton`).
 * @param error Error slot; receives the message carried by [ContentUiState.Error].
 * @param empty Empty slot; defaults to [EmptyState] with its generic copy.
 * @param content Content arm; receives the loaded data.
 */
@Composable
fun <T> ContentStateContainer(
    state: ContentUiState<T>,
    screenTag: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    loading: @Composable () -> Unit = { ContentStateDefaults.ListSkeleton(screenTag) },
    error: @Composable (message: String) -> Unit = { ErrorState(message = it, screenTag = screenTag, onRetry = onRetry) },
    empty: @Composable () -> Unit = { EmptyState(screenTag = screenTag) },
    content: @Composable (data: T) -> Unit,
) {
    // MOTION-12: an arm change fades the container in on Standard/Enter — the skeleton shares
    // the rows' geometry, so the swap reads as a cross-fade with no jump. Applied to the
    // container's own node, never a wrapper, so the semantics tree is exactly the arm's.
    Box(modifier.fillMaxSize().armFade(state::class.simpleName)) {
        when (state) {
            is ContentUiState.Loading -> loading()
            is ContentUiState.Error -> error(state.message)
            is ContentUiState.Empty -> empty()
            is ContentUiState.Content -> content(state.data)
        }
    }
}

/** Fade in whenever [armKey] changes after the first composition; a snap under Instant. */
private fun Modifier.armFade(armKey: String?): Modifier = composed {
    val motion = LocalMotion.current
    val alpha = remember { Animatable(1f) }
    val first = remember { arrayOf(true) }
    LaunchedEffect(armKey) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        alpha.snapTo(0f)
        alpha.animateTo(1f, motion.tween(FuelledMotion.Duration.Standard, FuelledMotion.Easings.Enter))
    }
    graphicsLayer { this.alpha = alpha.value }
}

/** Default slot implementations for [ContentStateContainer]. */
object ContentStateDefaults {

    /**
     * The default loading slot: skeleton rows shaped like [ListItemCard], so the loaded
     * list replaces them without a layout jump. The container node carries the
     * `<screenTag>_loading` tag and a "Loading" `contentDescription`; the bars
     * themselves are decorative and stay semantics-silent.
     *
     * @param screenTag Feature slug; tags the container `<screenTag>_loading`.
     * @param rows Skeleton rows to render while loading.
     */
    @Composable
    fun ListSkeleton(screenTag: String, rows: Int = 3) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    testTag = "${screenTag}_loading"
                    contentDescription = "Loading"
                },
            verticalArrangement = Arrangement.spacedBy(FuelledTokens.GapCard),
        ) {
            repeat(rows) { ListItemSkeleton() }
        }
    }

    /**
     * A centered spinner, for waits that are not content-shaped (a short, single-value
     * operation where a skeleton would promise the wrong layout). Carries the same
     * `<screenTag>_loading` tag as the skeleton.
     *
     * @param screenTag Feature slug; tags the container `<screenTag>_loading`.
     */
    @Composable
    fun Spinner(screenTag: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    testTag = "${screenTag}_loading"
                    contentDescription = "Loading"
                },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}
