package com.anti.theft.domain.usecase

import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.PermissionStateModel
import com.anti.theft.domain.models.PermissionStatus
import com.anti.theft.domain.models.ProtectionFeature
import com.anti.theft.domain.repo.DeviceCapabilitiesRepository
import com.anti.theft.domain.repo.SettingsRepository

/** Turns a guard on or off, refusing to enable it without its hardware or permission. */
class ToggleFeatureUseCase(
    private val settings: SettingsRepository,
    private val capabilities: DeviceCapabilitiesRepository,
) {
    suspend operator fun invoke(
        feature: ProtectionFeature,
        enable: Boolean,
        permissions: PermissionStateModel,
    ): ToggleResult {
        if (!enable) {
            settings.setFeatureEnabled(feature, false)
            return ToggleResult.Disabled
        }
        if (!capabilities.capabilities().supports(feature)) return ToggleResult.Unsupported

        val required = feature.requiredPermission()
        if (required != null) {
            val status = permissions.statusOf(required)
            if (status != PermissionStatus.GRANTED) return ToggleResult.PermissionRequired(required, status)
        }
        settings.setFeatureEnabled(feature, true)
        return ToggleResult.Enabled
    }
}

/** Charging alerts are notifications; clap detection needs the microphone. */
fun ProtectionFeature.requiredPermission(): AppPermission? = when (this) {
    ProtectionFeature.CHARGING -> AppPermission.NOTIFICATIONS
    ProtectionFeature.MOTION -> null
    ProtectionFeature.CLAP -> AppPermission.MICROPHONE
}
