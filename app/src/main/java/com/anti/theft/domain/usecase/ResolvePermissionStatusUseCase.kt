package com.anti.theft.domain.usecase

import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.PermissionStateModel
import com.anti.theft.domain.models.PermissionStatus
import com.anti.theft.domain.repo.PermissionRepository
import com.anti.theft.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Works out each permission's status. Android only reports "granted" and "should show
 * rationale", so permanent denial is inferred: asked before, not granted, and no rationale.
 */
class ResolvePermissionStatusUseCase(
    private val permissions: PermissionRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(shouldShowRationale: Map<AppPermission, Boolean>): PermissionStateModel {
        val requested = settings.requestedPermissions.first()
        fun statusOf(permission: AppPermission): PermissionStatus = when {
            permissions.isGranted(permission) -> PermissionStatus.GRANTED
            !permissions.canRequestAtRuntime(permission) -> PermissionStatus.PERMANENTLY_DENIED
            permission !in requested -> PermissionStatus.NOT_REQUESTED
            shouldShowRationale[permission] == true -> PermissionStatus.DENIED
            else -> PermissionStatus.PERMANENTLY_DENIED
        }
        return PermissionStateModel(
            notifications = statusOf(AppPermission.NOTIFICATIONS),
            microphone = statusOf(AppPermission.MICROPHONE),
            chargingChannelBlocked = permissions.isChargingChannelBlocked(),
            alarmChannelBlocked = permissions.isAlarmChannelBlocked(),
        )
    }
}
