package com.inventry.pos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer

/** Shrinks a little while pressed and springs back, so buttons feel alive. Put it first in the modifier chain. */
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.95f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this.scale(s)
}

/** Fades in and rises a little the first time it appears. Use [delayMillis] to stagger several of them. */
@Composable
fun Appear(delayMillis: Int = 0, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(420, delayMillis)) +
            slideInVertically(tween(480, delayMillis, easing = FastOutSlowInEasing)) { it / 7 },
    ) { content() }
}

/** A tap target that also gets the press-and-spring effect and keeps the normal ripple. */
fun Modifier.bouncyClickable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this.clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
}

/** Fades in and rises when first shown, after [delayMillis]. Give neighbours rising delays for a staggered entrance. */
fun Modifier.appear(delayMillis: Int = 0): Modifier = composed {
    val a = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        a.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 40f }
}
