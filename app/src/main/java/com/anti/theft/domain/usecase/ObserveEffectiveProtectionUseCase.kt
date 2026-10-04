package com.anti.theft.domain.usecase

import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.EffectiveProtectionModel
import com.anti.theft.domain.repo.DeviceCapabilitiesRepository
import com.anti.theft.domain.repo.PermissionRepository
import com.anti.theft.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Which guards can actually run. Permissions are read when settings change; a revoked
 * runtime permission kills the process, so a stale "granted" can't outlive it.
 */
class ObserveEffectiveProtectionUseCase(
    private val settings: SettingsRepository,
    private val permissions: PermissionRepository,
    private val capabilities: DeviceCapabilitiesRepository,
) {
    operator fun invoke(): Flow<EffectiveProtectionModel> = settings.settings
        .map { s ->
            EffectiveProtectionModel.from(
                settings = s,
                device = capabilities.capabilities(),
                notificationsGranted = permissions.isGranted(AppPermission.NOTIFICATIONS),
                microphoneGranted = permissions.isGranted(AppPermission.MICROPHONE),
            )
        }
        .distinctUntilChanged()
}
