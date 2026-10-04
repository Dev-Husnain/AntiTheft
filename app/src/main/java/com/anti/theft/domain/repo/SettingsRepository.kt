package com.anti.theft.domain.repo

import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.ProtectionFeature
import com.anti.theft.domain.models.ProtectionSettingsModel
import com.anti.theft.domain.models.Sensitivity
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<ProtectionSettingsModel>

    val requestedPermissions: Flow<Set<AppPermission>>

    val batteryTipDismissed: Flow<Boolean>

    suspend fun setFeatureEnabled(feature: ProtectionFeature, enabled: Boolean)

    suspend fun setSensitivity(feature: ProtectionFeature, sensitivity: Sensitivity)

    suspend fun markPermissionRequested(permission: AppPermission)

    suspend fun dismissBatteryTip()
}
