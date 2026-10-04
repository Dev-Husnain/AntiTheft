package com.anti.theft.presentation.home

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anti.theft.R
import com.anti.theft.core.theme.Dimens
import com.anti.theft.core.ui.AppTopBar
import com.anti.theft.core.ui.GradientBackground
import com.anti.theft.core.ui.InlineNotice
import com.anti.theft.core.ui.NavigationBarScrim
import com.anti.theft.core.ui.NoticeTone
import com.anti.theft.core.ui.PermissionRationaleSheet
import com.anti.theft.core.ui.StatusBarScrim
import com.anti.theft.core.ui.SystemBarAppearance
import com.anti.theft.core.utils.manifestPermission
import com.anti.theft.core.utils.openAppSettings
import com.anti.theft.core.utils.openNotificationSettings
import com.anti.theft.core.utils.permissionRationaleFlags
import com.anti.theft.presentation.home.components.FeatureToggleCard
import com.anti.theft.presentation.home.components.HomeAdaptiveLayout
import com.anti.theft.presentation.home.components.ProtectionStatusHero
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val rationale = { activity?.permissionRationaleFlags().orEmpty() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onEvent(HomeEvent.OnPermissionResult(granted, rationale()))
    }

    // Re-check permissions every time the user comes back, e.g. from system Settings.
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(HomeEvent.OnResumed(rationale()))
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.RequestPermission -> effect.permission.manifestPermission()
                    ?.let(permissionLauncher::launch)
                    ?: context.openNotificationSettings()
                HomeEffect.OpenAppSettings -> context.openAppSettings()
                HomeEffect.OpenNotificationSettings -> context.openNotificationSettings()
                is HomeEffect.ShowMessage -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getString(effect.messageRes))
                }
            }
        }
    }

    SystemBarAppearance(darkIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f)

    GradientBackground(modifier = modifier.fillMaxSize()) {
        HomeAdaptiveLayout(
            header = {
                AppTopBar(
                    title = stringResource(R.string.home_title),
                    subtitle = stringResource(R.string.home_subtitle),
                )
            },
            hero = {
                ProtectionStatusHero(
                    hero = uiState.hero,
                    onStopAlarm = { viewModel.onEvent(HomeEvent.OnStopAlarmClick) },
                    onResume = { viewModel.onEvent(HomeEvent.OnResumeProtectionClick) },
                )
            },
        ) {
            if (uiState.showNotificationsBanner) {
                InlineNotice(
                    title = stringResource(R.string.banner_notifications_title),
                    message = stringResource(R.string.banner_notifications_body),
                    tone = NoticeTone.Warning,
                    iconRes = R.drawable.ic_notifications_off,
                    actionLabel = stringResource(R.string.action_turn_on),
                    onAction = { viewModel.onEvent(HomeEvent.OnNotificationsBannerClick) },
                )
            }
            uiState.batteryTipManufacturer?.let { manufacturer ->
                InlineNotice(
                    title = stringResource(R.string.tip_battery_title),
                    message = stringResource(R.string.tip_battery_body, manufacturer),
                    iconRes = R.drawable.ic_battery_saver,
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = { viewModel.onEvent(HomeEvent.OnBatteryTipOpen) },
                    onDismiss = { viewModel.onEvent(HomeEvent.OnBatteryTipDismiss) },
                )
            }
            if (uiState.features.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.home_section_guards),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = Dimens.SpaceS)
                        .semantics { heading() },
                )
            }
            uiState.features.forEach { feature ->
                key(feature.feature) {
                    FeatureToggleCard(
                        model = feature,
                        onCheckedChange = { viewModel.onEvent(HomeEvent.OnFeatureToggled(feature.feature, it)) },
                        onSensitivityChange = {
                            viewModel.onEvent(HomeEvent.OnSensitivityChanged(feature.feature, it))
                        },
                        onNoticeAction = { viewModel.onEvent(HomeEvent.OnNoticeAction(feature.feature, it)) },
                    )
                }
            }
        }
        StatusBarScrim(modifier = Modifier.align(Alignment.TopCenter))
        NavigationBarScrim(modifier = Modifier.align(Alignment.BottomCenter))
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(Dimens.SpaceL),
        )
    }

    uiState.permissionSheet?.let { sheet ->
        PermissionRationaleSheet(
            iconRes = sheet.iconRes,
            title = stringResource(sheet.titleRes),
            message = stringResource(sheet.messageRes),
            confirmLabel = stringResource(sheet.confirmRes),
            dismissLabel = stringResource(R.string.rationale_not_now),
            onConfirm = { viewModel.onEvent(HomeEvent.OnSheetConfirmed) },
            onDismiss = { viewModel.onEvent(HomeEvent.OnSheetDismissed) },
        )
    }
}
