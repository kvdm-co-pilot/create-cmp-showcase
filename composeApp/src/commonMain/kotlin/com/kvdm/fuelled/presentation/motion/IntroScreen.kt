package com.kvdm.fuelled.presentation.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvdm.fuelled.presentation.brand.FuelledMark
import com.kvdm.fuelled.presentation.components.ProgressRing
import com.kvdm.fuelled.presentation.components.sharedHero
import com.kvdm.fuelled.presentation.theme.FuelledColors
import com.kvdm.fuelled.presentation.theme.FuelledMotion
import com.kvdm.fuelled.presentation.theme.LocalMotion
import com.kvdm.fuelled.presentation.theme.MotionScheme
import com.kvdm.fuelled.presentation.theme.moves
import com.kvdm.fuelled.presentation.theme.spring
import com.kvdm.fuelled.presentation.theme.staggerDelayMs
import com.kvdm.fuelled.presentation.theme.tween
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Ignition: the app's first frame (motion D18, MOTION-13) ─────────────────────────────
// An instrument powers up. The intro is built from the app's OWN signature parts — no new
// asset, nothing the rest of the app does not already own:
//   1. a single lime spark ignites at center (`Lively`);
//   2. it sweeps the day ring — the real ProgressRing, glow head and all — from 0 to full;
//   3. the mark is revealed from the spark outward and settles in (`Emphasized`/`Lively`);
//   4. the seven letters of the name rise, 40 ms apart (`Standard`, the arrival stagger);
//   5. the ring HANDS OFF: it is a shared element with Today's hero ring, so as the app
//      dissolves in, the big ring flies into its place on the dashboard.
// A tap anywhere skips. The whole thing runs under 2 s on Full; under Reduced it is a Quick
// fade of the assembled mark; under Instant (tests, previews) it is over on frame 0. It is
// also the loading screen: `App` keeps it on stage, at its end state, while the start gate
// is still resolving — the instrument stays lit until the data arrives.

/** The shared-element key the intro's ring and Today's hero ring meet under. */
const val HERO_RING_KEY = "hero-ring"

/**
 * How long the app must have been away before returning to the foreground replays the
 * ignition (MOTION-13). Long enough that glancing at a notification and coming straight back
 * does not replay it; short enough that "I opened the app" means you see it. A product rule,
 * not an animation spec — which is why it lives here with the surface it governs rather than
 * in the motion token catalog.
 */
val IntroReplayAfter: Duration = 60.seconds

/**
 * How long the assembled mark is HELD under the Reduced scheme (MOTION-15). Reduced motion
 * removes movement, not the moment: the first draft faded the whole ignition out in 120 ms,
 * which deleted the brand beat for exactly the people who cannot watch it move.
 */
val IntroHold: Duration = 900.milliseconds

/**
 * Whether returning to the foreground replays the ignition (MOTION-13). Pure, so the rule is
 * testable without a lifecycle: [awayFor] is how long the app was actually away.
 *
 * `null` means the app was never away (a cold start, or a configuration change) — the caller
 * decides those; this answers only the RETURN. Instant never replays: tests and previews get
 * exactly one end state.
 */
fun shouldReplayIntro(awayFor: Duration?, motion: MotionScheme): Boolean =
    awayFor != null && motion != MotionScheme.Instant && awayFor >= IntroReplayAfter

/**
 * The ignition. Stateless and sample-free — it has no data to show — so the registry renders
 * it directly (`intro`). [onDone] is called exactly once: when the choreography completes,
 * or on a tap, whichever comes first.
 */
@Composable
fun IntroScreen(
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val start = if (motion.moves) 0f else 1f
    val spark = remember { Animatable(start) }
    var ringProgress by remember { mutableFloatStateOf(start) }
    val reveal = remember { Animatable(start) }
    val settle = remember { Animatable(if (motion.moves) 0.8f else 1f) }
    val letters = remember { Animatable(start) }
    val fade = remember { Animatable(if (motion == MotionScheme.Reduced) 0f else 1f) }
    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            onDone()
        }
    }

    val letterWindowMs = FuelledMotion.Duration.Standard + FuelledMotion.staggerDelayMs(FuelledMotion.StaggerCap)
    LaunchedEffect(Unit) {
        when (motion) {
            MotionScheme.Instant -> finish()
            MotionScheme.Reduced -> {
                // MOTION-15: fade the assembled mark in, HOLD it so the moment lands, then go.
                fade.animateTo(1f, motion.tween(FuelledMotion.Duration.Quick))
                delay(IntroHold)
                finish()
            }
            MotionScheme.Full -> {
                spark.animateTo(1f, motion.spring(FuelledMotion.Springs.Lively))
                ringProgress = 1f // the ring sweeps itself on Weighty (ProgressRing's own motion)
                delay(FuelledMotion.Duration.Expressive.toLong())
                launch { settle.animateTo(1f, motion.spring(FuelledMotion.Springs.Lively)) }
                reveal.animateTo(1f, motion.tween(FuelledMotion.Duration.Emphasized, FuelledMotion.Easings.Enter))
                letters.animateTo(1f, motion.tween(letterWindowMs, FuelledMotion.Easings.Linear))
                delay(FuelledMotion.Duration.Quick.toLong())
                finish()
            }
        }
    }

    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FuelledColors.Background)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = "Skip intro", onClick = finish)
            .semantics {
                testTag = "intro_screen"
                contentDescription = "Fuelled"
            }
            .graphicsLayer { alpha = fade.value }
            // The one ambient glow (D11), behind the ring, on the token background.
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(FuelledColors.Primary.copy(alpha = 0.06f), FuelledColors.Primary.copy(alpha = 0f)),
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = size.minDimension * 0.7f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ProgressRing(
                progress = ringProgress,
                sweepFrom = 0f,
                stroke = 16.dp,
                modifier = Modifier
                    .size(196.dp)
                    .sharedHero(HERO_RING_KEY),
            ) {
                // The spark, then the mark revealed from it outward.
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .graphicsLayer {
                                val s = spark.value * (1f - reveal.value)
                                scaleX = s; scaleY = s
                                alpha = if (reveal.value >= 1f) 0f else 1f
                            }
                            .background(FuelledColors.Primary, MaterialTheme.shapes.extraLarge),
                    )
                    FuelledMark(
                        size = 72.dp,
                        modifier = Modifier
                            .graphicsLayer { scaleX = settle.value; scaleY = settle.value }
                            .drawWithContent {
                                val r = reveal.value
                                if (r >= 1f) {
                                    drawContent()
                                } else if (r > 0f) {
                                    val path = Path().apply {
                                        addOval(
                                            androidx.compose.ui.geometry.Rect(
                                                center = Offset(size.width / 2f, size.height / 2f),
                                                radius = size.maxDimension * 0.75f * r,
                                            ),
                                        )
                                    }
                                    clipPath(path) { this@drawWithContent.drawContent() }
                                }
                            },
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Wordmark(progress = letters.value, windowMs = letterWindowMs)
        }
    }
}

/**
 * "Fuelled", one letter at a time: letter `i` rises over `Standard` starting at its stagger
 * delay, all driven by ONE linear timeline so the choreography stays a pure function of it.
 */
@Composable
private fun Wordmark(progress: Float, windowMs: Int) {
    val density = LocalDensity.current
    val rise = with(density) { FuelledMotion.EnterRise.toPx() }
    val name = "Fuelled"
    Row {
        name.forEachIndexed { i, ch ->
            val startMs = FuelledMotion.staggerDelayMs(i)
            val local = ((progress * windowMs - startMs) / FuelledMotion.Duration.Standard).coerceIn(0f, 1f)
            val eased = FuelledMotion.Easings.Enter.transform(local)
            Text(
                text = ch.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer {
                    alpha = eased
                    translationY = (1f - eased) * rise
                },
            )
        }
    }
}
