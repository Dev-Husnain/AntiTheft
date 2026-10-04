package com.anti.theft.presentation.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anti.theft.core.theme.Dimens
import com.anti.theft.core.ui.SystemBarAppearance
import com.anti.theft.presentation.alarm.components.AlarmBackdrop
import com.anti.theft.presentation.alarm.components.AlarmHeadline
import com.anti.theft.presentation.alarm.components.StopAlarmButton
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AlarmScreen(
    modifier: Modifier = Modifier,
    viewModel: AlarmViewModel = koinViewModel(),
) {
    val uiState by viewModel.alarmUiState.collectAsStateWithLifecycle()

    // The backdrop is dark red in every theme, so status-bar icons are always light.
    SystemBarAppearance(darkIcons = false)

    AlarmBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Dimens.SpaceXxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.Space3xl, Alignment.CenterVertically),
        ) {
            AlarmHeadline(trigger = uiState.trigger)
            StopAlarmButton(onClick = { viewModel.onEvent(AlarmEvent.OnStopClick) })
        }
    }
}
