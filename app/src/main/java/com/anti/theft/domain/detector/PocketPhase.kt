package com.anti.theft.domain.detector

sealed interface PocketPhase {
    data object WaitingForPocket : PocketPhase
    data class Arming(val millisLeft: Long) : PocketPhase
    data object Armed : PocketPhase
    data object Triggered : PocketPhase
}
