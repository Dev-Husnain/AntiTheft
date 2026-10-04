package com.anti.theft.domain.repo

import com.anti.theft.domain.models.EffectiveProtectionModel
import com.anti.theft.domain.models.ProtectionStatusModel
import kotlinx.coroutines.flow.StateFlow

interface ProtectionRepository {
    val status: StateFlow<ProtectionStatusModel>

    /** Starts, updates or stops background protection to match [protection]. */
    fun apply(protection: EffectiveProtectionModel): Result<Unit>

    fun stopAlarm()
}
