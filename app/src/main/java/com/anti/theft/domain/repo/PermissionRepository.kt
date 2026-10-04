package com.anti.theft.domain.repo

import com.anti.theft.domain.models.AppPermission

interface PermissionRepository {
    fun isGranted(permission: AppPermission): Boolean

    /** False when the permission can only be changed in Settings (notifications below API 33). */
    fun canRequestAtRuntime(permission: AppPermission): Boolean

    fun isChargingChannelBlocked(): Boolean

    fun isAlarmChannelBlocked(): Boolean
}
