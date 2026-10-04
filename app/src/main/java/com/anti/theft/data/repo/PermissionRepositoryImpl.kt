package com.anti.theft.data.repo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anti.theft.core.constants.NotificationChannels
import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.repo.PermissionRepository

class PermissionRepositoryImpl(
    private val context: Context,
) : PermissionRepository {

    private val notificationManager = NotificationManagerCompat.from(context)

    override fun isGranted(permission: AppPermission): Boolean = when (permission) {
        // Covers both the API 33+ runtime permission and the app-wide switch in Settings.
        AppPermission.NOTIFICATIONS -> notificationManager.areNotificationsEnabled()
        AppPermission.MICROPHONE -> hasPermission(Manifest.permission.RECORD_AUDIO)
    }

    override fun canRequestAtRuntime(permission: AppPermission): Boolean = when (permission) {
        AppPermission.NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        AppPermission.MICROPHONE -> true
    }

    override fun isChargingChannelBlocked(): Boolean = isChannelBlocked(NotificationChannels.CHARGING)

    override fun isAlarmChannelBlocked(): Boolean = isChannelBlocked(NotificationChannels.ALARM)

    private fun isChannelBlocked(id: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val channel = notificationManager.getNotificationChannelCompat(id) ?: return false
        return channel.importance == NotificationManagerCompat.IMPORTANCE_NONE
    }

    private fun hasPermission(name: String) =
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
}
