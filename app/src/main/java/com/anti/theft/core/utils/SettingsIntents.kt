package com.anti.theft.core.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Opens this app's system settings page (permissions, battery, autostart on most OEMs). */
fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    launchOrFallback(intent)
}

/** Opens this app's notification settings, or app settings where that page doesn't exist. */
fun Context.openNotificationSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        launchOrFallback(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    } else {
        openAppSettings()
    }
}

private fun Context.launchOrFallback(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Some OEM builds strip specific settings pages; the generic settings screen always exists.
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
