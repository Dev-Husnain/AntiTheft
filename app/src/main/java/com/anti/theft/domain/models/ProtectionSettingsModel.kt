package com.anti.theft.domain.models

data class ProtectionSettingsModel(
    val chargingEnabled: Boolean = false,
    val motionEnabled: Boolean = false,
    val clapEnabled: Boolean = false,
    val pocketEnabled: Boolean = false,
    val motionSensitivity: Sensitivity = Sensitivity.MEDIUM,
    val clapSensitivity: Sensitivity = Sensitivity.MEDIUM,
) {
    fun isEnabled(feature: ProtectionFeature): Boolean = when (feature) {
        ProtectionFeature.CHARGING -> chargingEnabled
        ProtectionFeature.MOTION -> motionEnabled
        ProtectionFeature.CLAP -> clapEnabled
        ProtectionFeature.POCKET -> pocketEnabled
    }
}
