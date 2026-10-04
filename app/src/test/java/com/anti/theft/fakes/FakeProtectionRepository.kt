package com.anti.theft.fakes

import com.anti.theft.domain.models.EffectiveProtectionModel
import com.anti.theft.domain.models.ProtectionStatusModel
import com.anti.theft.domain.repo.ProtectionRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeProtectionRepository : ProtectionRepository {
    override val status = MutableStateFlow(ProtectionStatusModel())
    val applied = mutableListOf<EffectiveProtectionModel>()
    var stopAlarmCalls = 0
    var applyResult: Result<Unit> = Result.success(Unit)

    override fun apply(protection: EffectiveProtectionModel): Result<Unit> {
        applied += protection
        return applyResult
    }

    override fun stopAlarm() {
        stopAlarmCalls++
        status.value = status.value.copy(ringing = null)
    }
}
