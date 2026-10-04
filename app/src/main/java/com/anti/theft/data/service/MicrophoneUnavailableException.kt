package com.anti.theft.data.service

/** The microphone could not be opened: permission revoked, no hardware, or busy. */
class MicrophoneUnavailableException(message: String) : Exception(message)
