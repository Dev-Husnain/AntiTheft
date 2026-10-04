package com.anti.theft.data.repo

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.getSystemService
import com.anti.theft.domain.models.DeviceCapabilitiesModel
import com.anti.theft.domain.repo.DeviceCapabilitiesRepository

class DeviceCapabilitiesRepositoryImpl(
    private val context: Context,
) : DeviceCapabilitiesRepository {

    private val capabilities: DeviceCapabilitiesModel by lazy {
        val sensorManager = context.getSystemService<SensorManager>()
        DeviceCapabilitiesModel(
            hasAccelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
            hasMicrophone = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
            restrictiveManufacturer = RESTRICTIVE_MANUFACTURERS[Build.MANUFACTURER.lowercase()],
        )
    }

    override fun capabilities(): DeviceCapabilitiesModel = capabilities

    private companion object {
        // Vendors whose default battery management kills foreground services (dontkillmyapp.com).
        val RESTRICTIVE_MANUFACTURERS = mapOf(
            "xiaomi" to "Xiaomi",
            "redmi" to "Redmi",
            "poco" to "POCO",
            "huawei" to "Huawei",
            "honor" to "Honor",
            "oppo" to "OPPO",
            "realme" to "realme",
            "vivo" to "vivo",
            "oneplus" to "OnePlus",
            "meizu" to "Meizu",
            "asus" to "ASUS",
            "tecno" to "TECNO",
            "infinix" to "Infinix",
        )
    }
}
