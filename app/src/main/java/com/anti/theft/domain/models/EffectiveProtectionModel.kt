package com.anti.theft.domain.models

/** What can actually run: switched on, permitted and supported by the hardware. */
data class EffectiveProtectionModel(
    val charging: Boolean,
    val motion: Boolean,
    val clap: Boolean,
    val motionSensitivity: Sensitivity,
    val clapSensitivity: Sensitivity,
) {
    val anyActive: Boolean get() = charging || motion || clap
}
