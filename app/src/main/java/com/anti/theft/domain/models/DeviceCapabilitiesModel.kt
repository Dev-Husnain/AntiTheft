package com.anti.theft.domain.models

data class DeviceCapabilitiesModel(
    val hasAccelerometer: Boolean,
    val hasMicrophone: Boolean,
    val restrictiveManufacturer: String?,
) {
    fun supports(feature: ProtectionFeature): Boolean = when (feature) {
        ProtectionFeature.CHARGING -> true
        ProtectionFeature.MOTION -> hasAccelerometer
        ProtectionFeature.CLAP -> hasMicrophone
    }
}
