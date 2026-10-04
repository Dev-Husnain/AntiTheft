package com.anti.theft.domain.models

data class EffectiveProtectionModel(
    val charging: Boolean,
    val motion: Boolean,
    val clap: Boolean,
    val pocket: Boolean,
    val motionSensitivity: Sensitivity,
    val clapSensitivity: Sensitivity,
    val canSensePocket: Boolean,
) {
    val anyActive: Boolean get() = charging || motion || clap || pocket

    val activeCount: Int get() = listOf(charging, motion, clap, pocket).count { it }

    fun isActive(feature: ProtectionFeature): Boolean = when (feature) {
        ProtectionFeature.CHARGING -> charging
        ProtectionFeature.MOTION -> motion
        ProtectionFeature.CLAP -> clap
        ProtectionFeature.POCKET -> pocket
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
            pocket = settings.pocketEnabled && device.supports(ProtectionFeature.POCKET),
            motionSensitivity = settings.motionSensitivity,
            clapSensitivity = settings.clapSensitivity,
            canSensePocket = device.supports(ProtectionFeature.POCKET),
        )
    }
}
