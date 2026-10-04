package com.anti.theft.data.local

import com.anti.theft.domain.models.ProtectionStatusModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ProtectionStateHolder {
    private val _protectionStatusState = MutableStateFlow(ProtectionStatusModel())
    val protectionStatusState: StateFlow<ProtectionStatusModel> = _protectionStatusState.asStateFlow()

    @Volatile
    var startRequested: Boolean = false

    fun update(transform: (ProtectionStatusModel) -> ProtectionStatusModel) {
        _protectionStatusState.update(transform)
    }

    fun reset() {
        startRequested = false
        _protectionStatusState.value = ProtectionStatusModel()
    }
}
