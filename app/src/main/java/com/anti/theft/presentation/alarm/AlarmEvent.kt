package com.anti.theft.presentation.alarm

sealed interface AlarmEvent {
    data object OnStopClick : AlarmEvent
}
