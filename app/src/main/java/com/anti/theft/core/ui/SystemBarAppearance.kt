package com.anti.theft.core.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Sets the status-bar icon colour for the screen that calls it. Each screen calls this on
 * entry, so the visible screen always wins; nothing is restored on exit because the next
 * screen sets its own appearance.
 *
 * @param darkIcons true for dark icons (on a light background), false for light icons.
 */
@Composable
fun SystemBarAppearance(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = LocalActivity.current?.window ?: return
    LaunchedEffect(darkIcons) {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
    }
}
