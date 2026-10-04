package com.anti.theft.data.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anti.theft.R
import com.anti.theft.core.constants.NotificationChannels
import com.anti.theft.core.constants.NotificationIds
import com.anti.theft.domain.models.AlarmTrigger
import com.anti.theft.domain.models.EffectiveProtectionModel

class ProtectionNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)
    private val accent = ContextCompat.getColor(context, R.color.color_4F7C)

    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(NotificationChannels.PROTECTION, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.channel_protection_name))
                    .setDescription(context.getString(R.string.channel_protection_description))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(NotificationChannels.CHARGING, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_charging_name))
                    .setDescription(context.getString(R.string.channel_charging_description))
                    .build(),
                NotificationChannelCompat.Builder(NotificationChannels.ALARM, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_alarm_name))
                    .setDescription(context.getString(R.string.channel_alarm_description))
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .build(),
            ),
        )
    }

    fun protectionNotification(protection: EffectiveProtectionModel?): Notification {
        val features = protection?.let {
            buildList {
                if (it.charging) add(context.getString(R.string.feature_charging_title))
                if (it.motion) add(context.getString(R.string.feature_motion_title))
                if (it.clap) add(context.getString(R.string.feature_clap_title))
                if (it.pocket) add(context.getString(R.string.feature_pocket_title))
            }.joinToString(context.getString(R.string.notif_feature_separator))
        }
        return NotificationCompat.Builder(context, NotificationChannels.PROTECTION)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setColor(accent)
            .setContentTitle(context.getString(R.string.notif_protection_title))
            .apply { if (!features.isNullOrEmpty()) setContentText(features) }
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateProtection(protection: EffectiveProtectionModel) {
        notify(NotificationIds.PROTECTION, protectionNotification(protection))
    }

    fun showChargingAlert(connected: Boolean) {
        val (title, text) = if (connected) {
            R.string.notif_charger_connected to R.string.notif_charger_connected_text
        } else {
            R.string.notif_charger_disconnected to R.string.notif_charger_disconnected_text
        }
        val notification = NotificationCompat.Builder(context, NotificationChannels.CHARGING)
            .setSmallIcon(R.drawable.ic_bolt)
            .setColor(accent)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        notify(NotificationIds.CHARGING, notification)
    }

    fun showAlarm(trigger: AlarmTrigger) {
        val reason = when (trigger) {
            AlarmTrigger.MOTION -> R.string.alarm_reason_motion
            AlarmTrigger.CLAP -> R.string.alarm_reason_clap
            AlarmTrigger.POCKET -> R.string.alarm_reason_pocket
        }
        val stopIntent = PendingIntent.getService(
            context,
            REQUEST_STOP_ALARM,
            ProtectionService.stopAlarmIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.ALARM)
            .setSmallIcon(R.drawable.ic_crisis_alert)
            .setColor(ContextCompat.getColor(context, R.color.color_FF4D))
            .setContentTitle(context.getString(R.string.alarm_title))
            .setContentText(context.getString(reason))
            .setContentIntent(openAppIntent())
            .addAction(R.drawable.ic_close, context.getString(R.string.alarm_stop), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        notify(NotificationIds.ALARM, notification)
    }

    fun cancelAlarm() = manager.cancel(NotificationIds.ALARM)

    private fun openAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: Notification) {
        if (canPost()) manager.notify(id, notification)
    }

    private fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val REQUEST_OPEN_APP = 0
        const val REQUEST_STOP_ALARM = 1
    }
}
