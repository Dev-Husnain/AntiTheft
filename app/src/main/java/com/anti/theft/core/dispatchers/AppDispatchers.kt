package com.anti.theft.core.dispatchers

import kotlinx.coroutines.CoroutineDispatcher

/** Injected so tests can substitute a test dispatcher. */
data class AppDispatchers(
    val main: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val io: CoroutineDispatcher,
)
