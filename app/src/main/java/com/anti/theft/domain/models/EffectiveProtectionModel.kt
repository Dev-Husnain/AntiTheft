package com.anti.theft.domain.models

data class EffectiveProtectionModel(
    val charging: Boolean,
    val motion: Boolean,
    val clap: Boolean,
    val motionSensitivity: Sensitivity,
    val clapSensitivity: Sensitivity,
) {
    val anyActive: Boolean get() = charging || motion || clap

    val activeCount: Int get() = listOf(charging, motion, clap).count { it }

    fun isActive(feature: ProtectionFeature): Boolean = when (feature) {
        ProtectionFeature.CHARGING -> charging
        ProtectionFeature.MOTION -> motion
        ProtectionFeature.CLAP -> clap
    }

    companion object {
        fun from(
            settings: ProtectionSettingsModel,
            device: DeviceCapabilitiesModel,
            notificationsGranted: Boolean,
            microphoneGranted: Boolean,
        ) = EffectiveProtectionModel(
            charging = settings.chargingEnabled && notificationsGranted,
            motion = settings.motionEnabled && device.hasAccelerometer,
            clap = settings.clapEnabled && device.hasMicrophone && microphoneGranted,
            motionSensitivity = settings.motionSensitivity,
            clapSensitivity = settings.clapSensitivity,
        )
    }
}
