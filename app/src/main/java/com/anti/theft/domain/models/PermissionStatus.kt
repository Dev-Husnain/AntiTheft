package com.anti.theft.domain.models

enum class PermissionStatus {
    GRANTED,

    /** Never asked; the system dialog can be shown. */
    NOT_REQUESTED,

    /** Denied, but the system dialog can still be shown again. */
    DENIED,

    /** Only fixable from system Settings. */
    PERMANENTLY_DENIED,
}
