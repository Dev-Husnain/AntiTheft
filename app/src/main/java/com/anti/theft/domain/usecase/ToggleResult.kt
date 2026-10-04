package com.anti.theft.domain.usecase

import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.PermissionStatus

sealed interface ToggleResult {
    data object Enabled : ToggleResult
    data object Disabled : ToggleResult
    data object Unsupported : ToggleResult
    data class PermissionRequired(val permission: AppPermission, val status: PermissionStatus) : ToggleResult
}
