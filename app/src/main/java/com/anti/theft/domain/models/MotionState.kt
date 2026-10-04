package com.anti.theft.domain.models

sealed interface MotionState {
    data object Off : MotionState
    data object PausedInPocket : MotionState
    data class Arming(val secondsLeft: Int) : MotionState
    data object Armed : MotionState
}
