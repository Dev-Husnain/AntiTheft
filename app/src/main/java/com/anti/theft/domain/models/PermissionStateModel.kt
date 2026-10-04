package com.anti.theft.domain.models

data class PermissionStateModel(
    val notifications: PermissionStatus,
    val microphone: PermissionStatus,
    val chargingChannelBlocked: Boolean,
    val alarmChannelBlocked: Boolean,
) {
    fun statusOf(permission: AppPermission): PermissionStatus = when (permission) {
        AppPermission.NOTIFICATIONS -> notifications
        AppPermission.MICROPHONE -> microphone
    }
}
