package com.anti.theft.presentation.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anti.theft.domain.repo.ProtectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class AlarmViewModel(
    private val protection: ProtectionRepository,
) : ViewModel() {

    private val _alarmUiState = MutableStateFlow(AlarmUiState(trigger = protection.status.value.ringing))
    val alarmUiState: StateFlow<AlarmUiState> = _alarmUiState.asStateFlow()

    init {
        protection.status
            .onEach { status ->
                // Keep showing the last trigger while the screen animates out after the alarm stops.
                status.ringing?.let { trigger -> _alarmUiState.update { it.copy(trigger = trigger) } }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: AlarmEvent) {
        when (event) {
            AlarmEvent.OnStopClick -> protection.stopAlarm()
        }
    }
}
