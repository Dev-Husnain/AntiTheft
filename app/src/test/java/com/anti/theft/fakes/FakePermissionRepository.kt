package com.anti.theft.fakes

import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.repo.PermissionRepository

class FakePermissionRepository(
    var granted: Set<AppPermission> = emptySet(),
    var runtimeRequestable: Set<AppPermission> = AppPermission.entries.toSet(),
    var chargingChannelBlocked: Boolean = false,
    var alarmChannelBlocked: Boolean = false,
) : PermissionRepository {
    override fun isGranted(permission: AppPermission) = permission in granted
    override fun canRequestAtRuntime(permission: AppPermission) = permission in runtimeRequestable
    override fun isChargingChannelBlocked() = chargingChannelBlocked
    override fun isAlarmChannelBlocked() = alarmChannelBlocked
}
