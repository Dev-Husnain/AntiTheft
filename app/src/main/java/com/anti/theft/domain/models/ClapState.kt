package com.anti.theft.domain.models

enum class ClapState {
    OFF,
    LISTENING,

    /** The service was restarted from the background, where Android forbids microphone use. */
    PAUSED_IN_BACKGROUND,

    /** The microphone could not be opened (busy, or the permission was revoked). */
    UNAVAILABLE,
}
