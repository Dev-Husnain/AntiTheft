package com.anti.theft.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.anti.theft.core.theme.AntiTheftTheme

@Composable
fun PulseRings(
    color: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    rings: Int = 3,
    periodMillis: Int = 2400,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseProgress",
    )
    Canvas(modifier = modifier) {
        val maxRadius = size.minDimension / 2
        val base = if (animate) progress.value else 0f
        repeat(rings) { index ->
            val phase = if (animate) (base + index.toFloat() / rings) % 1f else (index + 1f) / (rings + 1)
            drawCircle(
                color = color.copy(alpha = (1f - phase) * 0.22f),
                radius = maxRadius * (0.5f + 0.5f * phase),
            )
        }
    }
}

@Preview
@Composable
private fun PulseRingsPreview() {
    AntiTheftTheme(darkTheme = true) {
        PulseRings(color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(240.dp), animate = false)
    }
}
