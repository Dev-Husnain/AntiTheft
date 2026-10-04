package com.anti.theft.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anti.theft.domain.repo.ProtectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App-level navigation state: whether the alarm destination should be on top. */
class AppNavViewModel(
    protection: ProtectionRepository,
) : ViewModel() {

    val alarmRingingState: StateFlow<Boolean> = protection.status
        .map { it.ringing != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), protection.status.value.ringing != null)
}
