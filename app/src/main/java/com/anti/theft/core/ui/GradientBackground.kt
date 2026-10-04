package com.anti.theft.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.anti.theft.core.theme.AntiTheftTheme

@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    glowColor: Color = MaterialTheme.colorScheme.primary,
    secondaryGlowColor: Color = MaterialTheme.colorScheme.secondary,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.26f), Color.Transparent),
                        center = Offset(size.width * 0.2f, 0f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(secondaryGlowColor.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width, size.height * 0.6f),
                        radius = size.maxDimension * 0.6f,
                    ),
                )
            },
        content = content,
    )
}

@Preview
@Composable
private fun GradientBackgroundPreview() {
    AntiTheftTheme(darkTheme = true) {
        GradientBackground(modifier = Modifier.fillMaxSize()) {}
    }
}
