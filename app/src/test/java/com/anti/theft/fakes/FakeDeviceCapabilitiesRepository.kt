package com.anti.theft.fakes

import com.anti.theft.domain.models.DeviceCapabilitiesModel
import com.anti.theft.domain.repo.DeviceCapabilitiesRepository

class FakeDeviceCapabilitiesRepository(
    var model: DeviceCapabilitiesModel = DeviceCapabilitiesModel(
        hasAccelerometer = true,
        hasMicrophone = true,
        hasProximity = true,
        hasLight = true,
        restrictiveManufacturer = null,
    ),
) : DeviceCapabilitiesRepository {
    override fun capabilities() = model
}
