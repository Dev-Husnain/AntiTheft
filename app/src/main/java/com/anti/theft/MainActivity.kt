package com.anti.theft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.anti.theft.core.theme.AntiTheftTheme
import com.anti.theft.core.theme.Dimens
import com.anti.theft.core.ui.AppTopBar
import com.anti.theft.core.ui.GradientBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AntiTheftTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    FoundationShell()
                }
            }
        }
    }
}

// Temporary until the Home screen lands in milestone 4.
@Composable
private fun FoundationShell() {
    GradientBackground(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.home_title),
            subtitle = stringResource(R.string.home_subtitle),
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = Dimens.SpaceXl),
        )
    }
}
