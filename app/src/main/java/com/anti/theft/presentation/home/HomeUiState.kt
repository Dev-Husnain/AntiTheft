package com.anti.theft.presentation.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.anti.theft.core.ui.NoticeTone
import com.anti.theft.domain.models.AlarmTrigger
import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.ProtectionFeature
import com.anti.theft.domain.models.Sensitivity

data class HomeUiState(
    val isLoading: Boolean = true,
    val hero: HeroUiModel = HeroUiModel.Off,
    val features: List<FeatureUiModel> = emptyList(),
    val showNotificationsBanner: Boolean = false,
    val batteryTipManufacturer: String? = null,
    val permissionSheet: PermissionSheetUiModel? = null,
)

sealed interface HeroUiModel {
    data object Off : HeroUiModel
    data class Protected(val activeGuards: Int) : HeroUiModel
    data class Arming(val secondsLeft: Int) : HeroUiModel
    data object Paused : HeroUiModel
    data class Ringing(val trigger: AlarmTrigger) : HeroUiModel
}

data class FeatureUiModel(
    val feature: ProtectionFeature,
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val checked: Boolean,
    val available: Boolean,
    val status: FeatureStatusUiModel? = null,
    val sensitivity: Sensitivity? = null,
    val notice: FeatureNoticeUiModel? = null,
)

data class FeatureStatusUiModel(
    @param:StringRes val labelRes: Int,
    val labelArg: Int? = null,
    val tone: StatusTone,
)

enum class StatusTone { Active, Pending }

data class FeatureNoticeUiModel(
    @param:StringRes val messageRes: Int,
    val tone: NoticeTone,
    @param:StringRes val actionRes: Int? = null,
    val action: NoticeAction? = null,
)

enum class NoticeAction { FIX_PERMISSION, OPEN_NOTIFICATION_SETTINGS, RETRY }

data class PermissionSheetUiModel(
    val permission: AppPermission,
    val opensSettings: Boolean,
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val messageRes: Int,
    @param:StringRes val confirmRes: Int,
)
