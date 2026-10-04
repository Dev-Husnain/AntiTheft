package com.anti.theft.data.repo

import android.content.Context
import androidx.core.content.ContextCompat
import com.anti.theft.data.local.ProtectionStateHolder
import com.anti.theft.data.service.ProtectionService
import com.anti.theft.domain.models.EffectiveProtectionModel
import com.anti.theft.domain.models.ProtectionStatusModel
import com.anti.theft.domain.repo.ProtectionRepository
import kotlinx.coroutines.flow.StateFlow

class ProtectionRepositoryImpl(
    private val context: Context,
    stateHolder: ProtectionStateHolder,
) : ProtectionRepository {

    override val status: StateFlow<ProtectionStatusModel> = stateHolder.protectionStatusState

    override fun apply(protection: EffectiveProtectionModel): Result<Unit> {
        if (!protection.anyActive) {
            context.stopService(ProtectionService.intent(context))
            return Result.success(Unit)
        }
        return try {
            ContextCompat.startForegroundService(context, ProtectionService.startIntent(context, protection.clap))
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) or background start limits (API 26+).
            Result.failure(e)
        }
    }

    override fun stopAlarm() {
        if (!status.value.serviceRunning) return
        try {
            context.startService(ProtectionService.stopAlarmIntent(context))
        } catch (e: IllegalStateException) {
            // Not reachable while the foreground service is running; nothing to stop otherwise.
        }
    }
}
