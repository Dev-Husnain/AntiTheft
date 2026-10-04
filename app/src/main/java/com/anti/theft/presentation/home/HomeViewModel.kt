package com.anti.theft.presentation.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anti.theft.R
import com.anti.theft.core.ui.NoticeTone
import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.ClapState
import com.anti.theft.domain.models.EffectiveProtectionModel
import com.anti.theft.domain.models.MotionState
import com.anti.theft.domain.models.PermissionStateModel
import com.anti.theft.domain.models.PermissionStatus
import com.anti.theft.domain.models.PocketState
import com.anti.theft.domain.models.ProtectionFeature
import com.anti.theft.domain.models.ProtectionSettingsModel
import com.anti.theft.domain.models.ProtectionStatusModel
import com.anti.theft.domain.repo.DeviceCapabilitiesRepository
import com.anti.theft.domain.repo.ProtectionRepository
import com.anti.theft.domain.repo.SettingsRepository
import com.anti.theft.domain.usecase.ResolvePermissionStatusUseCase
import com.anti.theft.domain.usecase.SyncProtectionUseCase
import com.anti.theft.domain.usecase.ToggleFeatureUseCase
import com.anti.theft.domain.usecase.ToggleResult
import com.anti.theft.domain.usecase.requiredPermission
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val settings: SettingsRepository,
    private val protection: ProtectionRepository,
    private val capabilities: DeviceCapabilitiesRepository,
    private val toggleFeature: ToggleFeatureUseCase,
    private val resolvePermissions: ResolvePermissionStatusUseCase,
    private val syncProtection: SyncProtectionUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    private val _permissionsState = MutableStateFlow<PermissionStateModel?>(null)
    private val _permissionSheetState = MutableStateFlow<PermissionSheetUiModel?>(null)
    private val _syncFailedState = MutableStateFlow(false)

    private var lastRationale: Map<AppPermission, Boolean> = emptyMap()

    private var pendingFeature: ProtectionFeature?
        get() = savedState.get<String>(KEY_PENDING_FEATURE)?.let(ProtectionFeature::valueOf)
        set(value) {
            savedState[KEY_PENDING_FEATURE] = value?.name
        }

    private var pendingPermission: AppPermission?
        get() = savedState.get<String>(KEY_PENDING_PERMISSION)?.let(AppPermission::valueOf)
        set(value) {
            savedState[KEY_PENDING_PERMISSION] = value?.name
        }

    init {
        combine(
            settings.settings,
            protection.status,
            _permissionsState.filterNotNull(),
            settings.batteryTipDismissed,
            combine(_permissionSheetState, _syncFailedState, ::Pair),
        ) { settings, status, permissions, tipDismissed, (sheet, syncFailed) ->
            buildUiState(settings, status, permissions, tipDismissed, sheet, syncFailed)
        }
            .onEach { state -> _homeUiState.update { state } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.OnResumed -> onResumed(event.rationale)
            is HomeEvent.OnFeatureToggled -> toggle(event.feature, event.enabled)
            is HomeEvent.OnSensitivityChanged -> viewModelScope.launch {
                settings.setSensitivity(event.feature, event.sensitivity)
            }
            is HomeEvent.OnNoticeAction -> when (event.action) {
                NoticeAction.FIX_PERMISSION -> toggle(event.feature, enabled = true)
                NoticeAction.OPEN_NOTIFICATION_SETTINGS -> send(HomeEffect.OpenNotificationSettings)
                NoticeAction.RETRY -> sync()
            }
            is HomeEvent.OnPermissionResult -> onPermissionResult(event.granted, event.rationale)
            HomeEvent.OnSheetConfirmed -> onSheetConfirmed()
            HomeEvent.OnSheetDismissed -> {
                _permissionSheetState.value = null
                pendingFeature = null
            }
            HomeEvent.OnNotificationsBannerClick -> onNotificationsBannerClick()
            HomeEvent.OnBatteryTipOpen -> send(HomeEffect.OpenAppSettings)
            HomeEvent.OnBatteryTipDismiss -> viewModelScope.launch { settings.dismissBatteryTip() }
            HomeEvent.OnResumeProtectionClick -> sync()
            HomeEvent.OnStopAlarmClick -> protection.stopAlarm()
        }
    }

    private fun onResumed(rationale: Map<AppPermission, Boolean>) {
        lastRationale = rationale
        viewModelScope.launch {
            val permissions = refreshPermissions()
            val feature = pendingFeature
            val required = feature?.requiredPermission()
            if (feature != null && required != null && pendingPermission == null &&
                permissions.statusOf(required) == PermissionStatus.GRANTED
            ) {
                pendingFeature = null
                applyToggle(feature, enabled = true, permissions)
            } else {
                runSync()
            }
        }
    }

    private fun toggle(feature: ProtectionFeature, enabled: Boolean) {
        viewModelScope.launch {
            val permissions = _permissionsState.value ?: refreshPermissions()
            applyToggle(feature, enabled, permissions)
        }
    }

    private suspend fun applyToggle(feature: ProtectionFeature, enabled: Boolean, permissions: PermissionStateModel) {
        when (val result = toggleFeature(feature, enabled, permissions)) {
            ToggleResult.Enabled -> {
                runSync()
                if (feature == ProtectionFeature.MOTION && permissions.notifications == PermissionStatus.NOT_REQUESTED) {
                    _permissionSheetState.value = sheetFor(AppPermission.NOTIFICATIONS, opensSettings = false)
                }
            }
            ToggleResult.Disabled -> runSync()
            ToggleResult.Unsupported -> send(HomeEffect.ShowMessage(R.string.msg_unsupported))
            is ToggleResult.PermissionRequired -> {
                pendingFeature = feature
                _permissionSheetState.value = sheetFor(
                    result.permission,
                    opensSettings = result.status == PermissionStatus.PERMANENTLY_DENIED,
                )
            }
        }
    }

    private fun onSheetConfirmed() {
        val sheet = _permissionSheetState.value ?: return
        _permissionSheetState.value = null
        when {
            !sheet.opensSettings -> requestPermission(sheet.permission)
            sheet.permission == AppPermission.NOTIFICATIONS -> send(HomeEffect.OpenNotificationSettings)
            else -> send(HomeEffect.OpenAppSettings)
        }
    }

    private fun onNotificationsBannerClick() {
        val status = _permissionsState.value?.notifications
        if (status == PermissionStatus.PERMANENTLY_DENIED) {
            send(HomeEffect.OpenNotificationSettings)
        } else {
            requestPermission(AppPermission.NOTIFICATIONS)
        }
    }

    private fun requestPermission(permission: AppPermission) {
        pendingPermission = permission
        send(HomeEffect.RequestPermission(permission))
    }

    private fun onPermissionResult(granted: Boolean, rationale: Map<AppPermission, Boolean>) {
        val permission = pendingPermission ?: return
        pendingPermission = null
        lastRationale = rationale
        viewModelScope.launch {
            settings.markPermissionRequested(permission)
            val permissions = refreshPermissions()
            val feature = pendingFeature
            pendingFeature = null
            if (granted && feature != null && feature.requiredPermission() == permission) {
                applyToggle(feature, enabled = true, permissions)
            } else {
                runSync()
            }
        }
    }

    private suspend fun refreshPermissions(): PermissionStateModel =
        resolvePermissions(lastRationale).also { _permissionsState.value = it }

    private fun sync() {
        viewModelScope.launch { runSync() }
    }

    private suspend fun runSync() {
        val failed = syncProtection().isFailure
        _syncFailedState.value = failed
        if (failed) send(HomeEffect.ShowMessage(R.string.msg_start_failed))
    }

    private fun send(effect: HomeEffect) {
        _effects.trySend(effect)
    }

    private fun buildUiState(
        settings: ProtectionSettingsModel,
        status: ProtectionStatusModel,
        permissions: PermissionStateModel,
        batteryTipDismissed: Boolean,
        sheet: PermissionSheetUiModel?,
        syncFailed: Boolean,
    ): HomeUiState {
        val device = capabilities.capabilities()
        val effective = EffectiveProtectionModel.from(
            settings = settings,
            device = device,
            notificationsGranted = permissions.notifications == PermissionStatus.GRANTED,
            microphoneGranted = permissions.microphone == PermissionStatus.GRANTED,
        )
        val ringing = status.ringing
        val motion = status.motion
        val hero = when {
            ringing != null -> HeroUiModel.Ringing(ringing)
            effective.anyActive && syncFailed && !status.serviceRunning -> HeroUiModel.Paused
            motion is MotionState.Arming -> HeroUiModel.Arming(motion.secondsLeft)
            effective.anyActive -> HeroUiModel.Protected(effective.activeCount)
            else -> HeroUiModel.Off
        }
        val anyEnabled = settings.chargingEnabled || settings.motionEnabled || settings.clapEnabled ||
            settings.pocketEnabled
        return HomeUiState(
            isLoading = false,
            hero = hero,
            features = listOf(
                chargingCard(settings, effective, status, permissions),
                motionCard(settings, effective, status, permissions, device.hasAccelerometer),
                clapCard(settings, effective, status, permissions, device.hasMicrophone),
                pocketCard(effective, status, device.supports(ProtectionFeature.POCKET)),
            ),
            showNotificationsBanner = permissions.notifications == PermissionStatus.DENIED ||
                permissions.notifications == PermissionStatus.PERMANENTLY_DENIED,
            batteryTipManufacturer = device.restrictiveManufacturer.takeIf { anyEnabled && !batteryTipDismissed },
            permissionSheet = sheet,
        )
    }

    private fun chargingCard(
        settings: ProtectionSettingsModel,
        effective: EffectiveProtectionModel,
        status: ProtectionStatusModel,
        permissions: PermissionStateModel,
    ) = FeatureUiModel(
        feature = ProtectionFeature.CHARGING,
        iconRes = R.drawable.ic_bolt,
        titleRes = R.string.feature_charging_title,
        descriptionRes = R.string.feature_charging_body,
        checked = effective.charging,
        available = true,
        status = if (effective.charging && status.chargingWatching) {
            FeatureStatusUiModel(R.string.status_watching, tone = StatusTone.Active)
        } else {
            null
        },
        notice = when {
            permissions.notifications == PermissionStatus.PERMANENTLY_DENIED && settings.chargingEnabled ->
                fixNotice(R.string.notice_notifications_blocked, R.string.action_open_settings)
            permissions.notifications == PermissionStatus.DENIED ->
                fixNotice(R.string.notice_notifications_needed, R.string.action_allow)
            effective.charging && permissions.chargingChannelBlocked -> FeatureNoticeUiModel(
                messageRes = R.string.notice_charging_channel_blocked,
                tone = NoticeTone.Warning,
                actionRes = R.string.action_open_settings,
                action = NoticeAction.OPEN_NOTIFICATION_SETTINGS,
            )
            else -> null
        },
    )

    private fun motionCard(
        settings: ProtectionSettingsModel,
        effective: EffectiveProtectionModel,
        status: ProtectionStatusModel,
        permissions: PermissionStateModel,
        hasAccelerometer: Boolean,
    ) = FeatureUiModel(
        feature = ProtectionFeature.MOTION,
        iconRes = R.drawable.ic_vibration,
        titleRes = R.string.feature_motion_title,
        descriptionRes = R.string.feature_motion_body,
        checked = effective.motion,
        available = hasAccelerometer,
        status = when (val motion = status.motion) {
            is MotionState.Arming -> FeatureStatusUiModel(R.string.status_arming, motion.secondsLeft, StatusTone.Pending)
            MotionState.Armed -> FeatureStatusUiModel(R.string.status_armed, tone = StatusTone.Active)
            MotionState.PausedInPocket -> FeatureStatusUiModel(R.string.status_paused_pocket, tone = StatusTone.Pending)
            MotionState.Off -> null
        },
        sensitivity = settings.motionSensitivity,
        notice = when {
            !hasAccelerometer -> FeatureNoticeUiModel(R.string.notice_no_accelerometer, NoticeTone.Info)
            effective.motion && permissions.alarmChannelBlocked -> FeatureNoticeUiModel(
                messageRes = R.string.notice_alarm_channel_blocked,
                tone = NoticeTone.Warning,
                actionRes = R.string.action_open_settings,
                action = NoticeAction.OPEN_NOTIFICATION_SETTINGS,
            )
            effective.motion && status.motion == MotionState.PausedInPocket ->
                FeatureNoticeUiModel(R.string.notice_motion_pocket, NoticeTone.Info)
            effective.motion -> FeatureNoticeUiModel(R.string.notice_motion_battery, NoticeTone.Info)
            else -> null
        },
    )

    private fun clapCard(
        settings: ProtectionSettingsModel,
        effective: EffectiveProtectionModel,
        status: ProtectionStatusModel,
        permissions: PermissionStateModel,
        hasMicrophone: Boolean,
    ) = FeatureUiModel(
        feature = ProtectionFeature.CLAP,
        iconRes = R.drawable.ic_mic,
        titleRes = R.string.feature_clap_title,
        descriptionRes = R.string.feature_clap_body,
        checked = effective.clap,
        available = hasMicrophone,
        status = when (status.clap) {
            ClapState.LISTENING -> FeatureStatusUiModel(R.string.status_listening, tone = StatusTone.Active)
            ClapState.PAUSED_IN_BACKGROUND, ClapState.UNAVAILABLE ->
                FeatureStatusUiModel(R.string.status_paused, tone = StatusTone.Pending)
            ClapState.OFF -> null
        },
        sensitivity = settings.clapSensitivity,
        notice = when {
            !hasMicrophone -> FeatureNoticeUiModel(R.string.notice_no_microphone, NoticeTone.Info)
            permissions.microphone == PermissionStatus.PERMANENTLY_DENIED ->
                fixNotice(R.string.notice_mic_blocked, R.string.action_open_settings)
            permissions.microphone == PermissionStatus.DENIED ->
                fixNotice(R.string.notice_mic_needed, R.string.action_allow)
            effective.clap && status.clap == ClapState.PAUSED_IN_BACKGROUND ->
                FeatureNoticeUiModel(R.string.notice_mic_paused, NoticeTone.Info)
            effective.clap && status.clap == ClapState.UNAVAILABLE -> FeatureNoticeUiModel(
                messageRes = R.string.notice_mic_unavailable,
                tone = NoticeTone.Warning,
                actionRes = R.string.action_retry,
                action = NoticeAction.RETRY,
            )
            else -> null
        },
    )

    private fun pocketCard(
        effective: EffectiveProtectionModel,
        status: ProtectionStatusModel,
        supported: Boolean,
    ) = FeatureUiModel(
        feature = ProtectionFeature.POCKET,
        iconRes = R.drawable.ic_front_hand,
        titleRes = R.string.feature_pocket_title,
        descriptionRes = R.string.feature_pocket_body,
        checked = effective.pocket,
        available = supported,
        status = when (val pocket = status.pocket) {
            PocketState.WaitingForPocket -> FeatureStatusUiModel(R.string.status_waiting_pocket, tone = StatusTone.Pending)
            is PocketState.Arming -> FeatureStatusUiModel(R.string.status_arming, pocket.secondsLeft, StatusTone.Pending)
            PocketState.Armed -> FeatureStatusUiModel(R.string.status_armed, tone = StatusTone.Active)
            PocketState.Off -> null
        },
        notice = when {
            !supported -> FeatureNoticeUiModel(R.string.notice_no_proximity, NoticeTone.Info)
            effective.pocket -> FeatureNoticeUiModel(R.string.notice_pocket_info, NoticeTone.Info)
            else -> null
        },
    )

    private fun fixNotice(messageRes: Int, actionRes: Int) = FeatureNoticeUiModel(
        messageRes = messageRes,
        tone = NoticeTone.Warning,
        actionRes = actionRes,
        action = NoticeAction.FIX_PERMISSION,
    )

    private fun sheetFor(permission: AppPermission, opensSettings: Boolean) = when (permission) {
        AppPermission.NOTIFICATIONS -> PermissionSheetUiModel(
            permission = permission,
            opensSettings = opensSettings,
            iconRes = if (opensSettings) R.drawable.ic_notifications_off else R.drawable.ic_shield_check,
            titleRes = if (opensSettings) R.string.settings_notifications_title else R.string.rationale_notifications_title,
            messageRes = if (opensSettings) R.string.settings_notifications_body else R.string.rationale_notifications_body,
            confirmRes = if (opensSettings) R.string.action_open_settings else R.string.rationale_continue,
        )
        AppPermission.MICROPHONE -> PermissionSheetUiModel(
            permission = permission,
            opensSettings = opensSettings,
            iconRes = R.drawable.ic_mic,
            titleRes = if (opensSettings) R.string.settings_mic_title else R.string.rationale_mic_title,
            messageRes = if (opensSettings) R.string.settings_mic_body else R.string.rationale_mic_body,
            confirmRes = if (opensSettings) R.string.action_open_settings else R.string.rationale_continue,
        )
    }

    private companion object {
        const val KEY_PENDING_FEATURE = "pending_feature"
        const val KEY_PENDING_PERMISSION = "pending_permission"
    }
}
