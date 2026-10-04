package com.anti.theft.domain.models

sealed interface PocketState {
    data object Off : PocketState
    data object WaitingForPocket : PocketState
    data class Arming(val secondsLeft: Int) : PocketState
    data object Armed : PocketState
}
