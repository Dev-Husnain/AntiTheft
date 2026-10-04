package com.anti.theft.domain.usecase

import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.EffectiveProtectionModel
import com.anti.theft.domain.repo.DeviceCapabilitiesRepository
import com.anti.theft.domain.repo.PermissionRepository
import com.anti.theft.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Which guards can actually run: switched on, permitted and supported by the hardware. */
class ObserveEffectiveProtectionUseCase(
    private val settings: SettingsRepository,
    private val permissions: PermissionRepository,
    private val capabilities: DeviceCapabilitiesRepository,
) {
    operator fun invoke(): Flow<EffectiveProtectionModel> = settings.settings
        .map { s ->
            val device = capabilities.capabilities()
            EffectiveProtectionModel(
                charging = s.chargingEnabled && permissions.isGranted(AppPermission.NOTIFICATIONS),
                motion = s.motionEnabled && device.hasAccelerometer,
                clap = s.clapEnabled && device.hasMicrophone && permissions.isGranted(AppPermission.MICROPHONE),
                motionSensitivity = s.motionSensitivity,
                clapSensitivity = s.clapSensitivity,
            )
        }
        .distinctUntilChanged()
}
