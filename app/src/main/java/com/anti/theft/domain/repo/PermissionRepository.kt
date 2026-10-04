package com.anti.theft.domain.repo

import com.anti.theft.domain.models.AppPermission

interface PermissionRepository {
    fun isGranted(permission: AppPermission): Boolean

    fun canRequestAtRuntime(permission: AppPermission): Boolean

    fun isChargingChannelBlocked(): Boolean

    fun isAlarmChannelBlocked(): Boolean
}
