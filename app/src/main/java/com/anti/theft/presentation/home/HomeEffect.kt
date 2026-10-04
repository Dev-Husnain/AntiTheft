package com.anti.theft.presentation.home

import androidx.annotation.StringRes
import com.anti.theft.domain.models.AppPermission

sealed interface HomeEffect {
    data class RequestPermission(val permission: AppPermission) : HomeEffect
    data object OpenAppSettings : HomeEffect
    data object OpenNotificationSettings : HomeEffect
    data class ShowMessage(@param:StringRes val messageRes: Int) : HomeEffect
}
