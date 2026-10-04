package com.anti.theft.data.local

import com.anti.theft.domain.models.ProtectionStatusModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory live state of the protection service. The service is the only writer; it lives in
 * the same process, so a fresh process correctly starts from "not running".
 */
class ProtectionStateHolder {
    private val _protectionStatusState = MutableStateFlow(ProtectionStatusModel())
    val protectionStatusState: StateFlow<ProtectionStatusModel> = _protectionStatusState.asStateFlow()

    fun update(transform: (ProtectionStatusModel) -> ProtectionStatusModel) {
        _protectionStatusState.update(transform)
    }

    fun reset() {
        _protectionStatusState.value = ProtectionStatusModel()
    }
}
