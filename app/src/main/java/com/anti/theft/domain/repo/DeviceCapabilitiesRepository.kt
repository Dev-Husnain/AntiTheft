package com.anti.theft.domain.repo

import com.anti.theft.domain.models.DeviceCapabilitiesModel

interface DeviceCapabilitiesRepository {
    fun capabilities(): DeviceCapabilitiesModel
}
