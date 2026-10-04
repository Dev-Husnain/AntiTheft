package com.anti.theft.domain.models

/** Live state of the protection service. */
data class ProtectionStatusModel(
    val serviceRunning: Boolean = false,
    val chargingWatching: Boolean = false,
    val motion: MotionState = MotionState.Off,
    val clap: ClapState = ClapState.OFF,
    val ringing: AlarmTrigger? = null,
)
