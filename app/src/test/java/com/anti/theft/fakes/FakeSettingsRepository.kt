package com.anti.theft.fakes

import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.ProtectionFeature
import com.anti.theft.domain.models.ProtectionSettingsModel
import com.anti.theft.domain.models.Sensitivity
import com.anti.theft.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(
    initial: ProtectionSettingsModel = ProtectionSettingsModel(),
    requested: Set<AppPermission> = emptySet(),
) : SettingsRepository {
    override val settings = MutableStateFlow(initial)
    override val requestedPermissions = MutableStateFlow(requested)
    override val batteryTipDismissed = MutableStateFlow(false)

    override suspend fun setFeatureEnabled(feature: ProtectionFeature, enabled: Boolean) {
        settings.update {
            when (feature) {
                ProtectionFeature.CHARGING -> it.copy(chargingEnabled = enabled)
                ProtectionFeature.MOTION -> it.copy(motionEnabled = enabled)
                ProtectionFeature.CLAP -> it.copy(clapEnabled = enabled)
            }
        }
    }

    override suspend fun setSensitivity(feature: ProtectionFeature, sensitivity: Sensitivity) {
        settings.update {
            when (feature) {
                ProtectionFeature.MOTION -> it.copy(motionSensitivity = sensitivity)
                ProtectionFeature.CLAP -> it.copy(clapSensitivity = sensitivity)
                ProtectionFeature.CHARGING -> it
            }
        }
    }

    override suspend fun markPermissionRequested(permission: AppPermission) {
        requestedPermissions.update { it + permission }
    }

    override suspend fun dismissBatteryTip() {
        batteryTipDismissed.value = true
    }
}
