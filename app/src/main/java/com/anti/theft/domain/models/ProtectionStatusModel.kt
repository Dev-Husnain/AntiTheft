package com.anti.theft.domain.models

data class ProtectionStatusModel(
    val serviceRunning: Boolean = false,
    val chargingWatching: Boolean = false,
    val motion: MotionState = MotionState.Off,
    val clap: ClapState = ClapState.OFF,
    val pocket: PocketState = PocketState.Off,
    val ringing: AlarmTrigger? = null,
)
