package com.example.timetablescraper.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Motion tokens, modelled on how iOS animates.
 *
 * The single biggest difference from the Material defaults the app shipped with is that **nothing
 * is linear-ish**: iOS uses springs everywhere, so movement accelerates immediately and settles
 * without the mechanical feel of a fixed-duration ease. A `tween` at a fixed 300 ms is exactly
 * what makes an Android app read as "not iOS" — every element arrives, stops dead, and waits.
 *
 * Springs are specified the way iOS specifies them: a response (how long the spring takes) and a
 * damping fraction (how much it overshoots). Material exposes stiffness/damping ratio instead, so
 * the numbers below are the equivalents.
 */
object Motion {

    /** Response 0.35, damping 0.85 — the workhorse for layout and colour changes. */
    fun <T> gentle(): AnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /** Response 0.28, damping 0.8 — for small, direct manipulations (press, toggle). */
    fun <T> snappy(): AnimationSpec<T> = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMedium)

    /** Slightly under-damped, for elements that should feel light: selection thumbs, chevrons. */
    fun <T> bouncy(): AnimationSpec<T> = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)

    /** Content swaps (week changes, list replacements): a short cross-fade, never a slide. */
    val contentFade: FiniteAnimationSpec<Float> = tween(durationMillis = 180)

    /**
     * Push/pop screen transitions. A spring, so a tap and a release in flight both feel right,
     * and typed generically because slides animate a pixel offset, not a float.
     */
    fun <T> screen(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

    /** How far a pushed screen travels as a fraction of its width, and how far the one behind it
     *  parallaxes the other way. iOS moves the outgoing screen a third of the distance. */
    const val screenParallax: Float = 0.3f
}

/**
 * Apple's press feedback: the element itself scales down slightly and dims, instead of Material's
 * ink ripple spreading from the touch point.
 *
 * @param pressedScale how far to shrink while held. 0.97 for large surfaces, 0.92 for small ones.
 * @param haptic whether to give a light haptic tick on press, as iOS does for most controls.
 */
fun Modifier.iosPressable(
    pressedScale: Float = 0.97f,
    dim: Float = 0.6f,
    haptic: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.snappy(),
        label = "iosPressScale",
    )

    if (haptic) {
        androidx.compose.runtime.LaunchedEffect(pressed) {
            if (pressed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            Modifier.graphicsLayer {
                alpha = if (pressed) dim else 1f
            },
        )
}

/** A light haptic tick, for selection changes and toggles. */
@Composable
fun rememberHapticTick(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}
