package com.anti.theft.core.utils

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.core.app.ActivityCompat
import com.anti.theft.domain.models.AppPermission

fun AppPermission.manifestPermission(): String? = when (this) {
    AppPermission.NOTIFICATIONS ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
    AppPermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
}

fun Activity.permissionRationaleFlags(): Map<AppPermission, Boolean> =
    AppPermission.entries.associateWith { permission ->
        permission.manifestPermission()
            ?.let { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
            ?: false
    }
