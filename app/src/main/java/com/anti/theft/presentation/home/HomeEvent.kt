package com.anti.theft.presentation.home

import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.ProtectionFeature
import com.anti.theft.domain.models.Sensitivity

sealed interface HomeEvent {
    /** Screen resumed; carries the activity's current rationale flags. */
    data class OnResumed(val rationale: Map<AppPermission, Boolean>) : HomeEvent
    data class OnFeatureToggled(val feature: ProtectionFeature, val enabled: Boolean) : HomeEvent
    data class OnSensitivityChanged(val feature: ProtectionFeature, val sensitivity: Sensitivity) : HomeEvent
    data class OnNoticeAction(val feature: ProtectionFeature, val action: NoticeAction) : HomeEvent
    data class OnPermissionResult(val granted: Boolean, val rationale: Map<AppPermission, Boolean>) : HomeEvent
    data object OnSheetConfirmed : HomeEvent
    data object OnSheetDismissed : HomeEvent
    data object OnNotificationsBannerClick : HomeEvent
    data object OnBatteryTipOpen : HomeEvent
    data object OnBatteryTipDismiss : HomeEvent
    data object OnResumeProtectionClick : HomeEvent
    data object OnStopAlarmClick : HomeEvent
}
