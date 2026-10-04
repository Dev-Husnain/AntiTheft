package com.anti.theft.presentation.alarm.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.anti.theft.core.theme.AntiTheftTheme
import com.anti.theft.core.theme.Color0A0F
import com.anti.theft.core.theme.ColorB312
import com.anti.theft.core.theme.ColorFF4D

@Composable
fun AlarmBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "alarmGlow")
    val glow = transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alarmGlowAlpha",
    )
    Box(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(ColorB312, Color0A0F)))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(ColorFF4D.copy(alpha = glow.value), Color.Transparent),
                        center = Offset(size.width / 2, size.height * 0.4f),
                        radius = size.maxDimension * 0.6f,
                    ),
                )
            },
        content = content,
    )
}

@Preview
@Composable
private fun AlarmBackdropPreview() {
    AntiTheftTheme {
        AlarmBackdrop(modifier = Modifier.fillMaxSize()) {}
    }
}
