package com.anti.theft.presentation.alarm

import com.anti.theft.domain.models.AlarmTrigger

data class AlarmUiState(
    val trigger: AlarmTrigger? = null,
)
