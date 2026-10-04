package com.anti.theft.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Translucent strip behind the status bar so its icons stay legible while content scrolls
 * underneath. Exactly as tall as the status bar inset.
 */
@Composable
fun StatusBarScrim(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(color),
    )
}
