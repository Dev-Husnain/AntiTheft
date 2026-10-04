package com.anti.theft.presentation.alarm

import com.anti.theft.domain.models.AlarmTrigger

data class AlarmUiState(
    /** What set the alarm off; null once it has stopped (the screen is then being popped). */
    val trigger: AlarmTrigger? = null,
)
