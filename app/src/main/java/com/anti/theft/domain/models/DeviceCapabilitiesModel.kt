package com.anti.theft.domain.models

data class DeviceCapabilitiesModel(
    val hasAccelerometer: Boolean,
    val hasMicrophone: Boolean,
    val hasProximity: Boolean,
    val hasLight: Boolean,
    val restrictiveManufacturer: String?,
) {
    fun supports(feature: ProtectionFeature): Boolean = when (feature) {
        ProtectionFeature.CHARGING -> true
        ProtectionFeature.MOTION -> hasAccelerometer
        ProtectionFeature.CLAP -> hasMicrophone
        ProtectionFeature.POCKET -> (hasProximity || hasLight) && hasAccelerometer
    }
}
