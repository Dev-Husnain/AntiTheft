package com.anti.theft.core.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.anti.theft.presentation.alarm.AlarmScreen
import com.anti.theft.presentation.home.HomeScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    viewModel: AppNavViewModel = koinViewModel(),
) {
    val backStack = rememberNavBackStack(Route.Home)
    val alarmRinging by viewModel.alarmRingingState.collectAsStateWithLifecycle()

    LaunchedEffect(alarmRinging) {
        val alarmOnTop = backStack.lastOrNull() == Route.Alarm
        when {
            alarmRinging && !alarmOnTop -> backStack.add(Route.Alarm)
            !alarmRinging -> backStack.removeAll { it == Route.Alarm }
        }
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { forwardTransition() },
        popTransitionSpec = { backTransition() },
        predictivePopTransitionSpec = { backTransition() },
        entryProvider = entryProvider {
            entry<Route.Home> { HomeScreen() }
            entry<Route.Alarm> { AlarmScreen() }
        },
    )
}

private const val TRANSITION_MS = 320

private fun forwardTransition(): ContentTransform =
    (slideInHorizontally(tween(TRANSITION_MS)) { it / 6 } + fadeIn(tween(TRANSITION_MS))) togetherWith
        (slideOutHorizontally(tween(TRANSITION_MS)) { -it / 10 } + fadeOut(tween(TRANSITION_MS)))

private fun backTransition(): ContentTransform =
    (slideInHorizontally(tween(TRANSITION_MS)) { -it / 10 } + fadeIn(tween(TRANSITION_MS))) togetherWith
        (slideOutHorizontally(tween(TRANSITION_MS)) { it / 6 } + fadeOut(tween(TRANSITION_MS)))
