package com.anti.theft.domain.models

/** What the user switched on, as persisted. */
data class ProtectionSettingsModel(
    val chargingEnabled: Boolean = false,
    val motionEnabled: Boolean = false,
    val clapEnabled: Boolean = false,
    val motionSensitivity: Sensitivity = Sensitivity.MEDIUM,
    val clapSensitivity: Sensitivity = Sensitivity.MEDIUM,
) {
    fun isEnabled(feature: ProtectionFeature): Boolean = when (feature) {
        ProtectionFeature.CHARGING -> chargingEnabled
        ProtectionFeature.MOTION -> motionEnabled
        ProtectionFeature.CLAP -> clapEnabled
    }
}
