package com.anti.theft.domain.usecase

import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.DeviceCapabilitiesModel
import com.anti.theft.domain.models.PermissionStateModel
import com.anti.theft.domain.models.PermissionStatus
import com.anti.theft.domain.models.ProtectionFeature
import com.anti.theft.domain.models.ProtectionSettingsModel
import com.anti.theft.fakes.FakeDeviceCapabilitiesRepository
import com.anti.theft.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleFeatureUseCaseTest {

    private val settings = FakeSettingsRepository()
    private val capabilities = FakeDeviceCapabilitiesRepository()
    private val toggle = ToggleFeatureUseCase(settings, capabilities)

    private fun permissions(
        notifications: PermissionStatus = PermissionStatus.GRANTED,
        microphone: PermissionStatus = PermissionStatus.GRANTED,
    ) = PermissionStateModel(notifications, microphone, chargingChannelBlocked = false, alarmChannelBlocked = false)

    @Test
    fun `enables motion without any permission`() = runTest {
        val result = toggle(ProtectionFeature.MOTION, true, permissions(PermissionStatus.DENIED, PermissionStatus.DENIED))

        assertEquals(ToggleResult.Enabled, result)
        assertTrue(settings.settings.value.motionEnabled)
    }

    @Test
    fun `clap needs the microphone and stays off without it`() = runTest {
        val result = toggle(ProtectionFeature.CLAP, true, permissions(microphone = PermissionStatus.PERMANENTLY_DENIED))

        assertEquals(
            ToggleResult.PermissionRequired(AppPermission.MICROPHONE, PermissionStatus.PERMANENTLY_DENIED),
            result,
        )
        assertFalse(settings.settings.value.clapEnabled)
    }

    @Test
    fun `charging alerts need notifications`() = runTest {
        val result = toggle(ProtectionFeature.CHARGING, true, permissions(notifications = PermissionStatus.NOT_REQUESTED))

        assertEquals(
            ToggleResult.PermissionRequired(AppPermission.NOTIFICATIONS, PermissionStatus.NOT_REQUESTED),
            result,
        )
    }

    @Test
    fun `refuses motion on a device without an accelerometer`() = runTest {
        capabilities.model = capabilities.model.copy(hasAccelerometer = false)

        assertEquals(ToggleResult.Unsupported, toggle(ProtectionFeature.MOTION, true, permissions()))
        assertFalse(settings.settings.value.motionEnabled)
    }

    @Test
    fun `disabling always succeeds even without permission`() = runTest {
        settings.settings.value = ProtectionSettingsModel(clapEnabled = true)
        capabilities.model = DeviceCapabilitiesModel(hasAccelerometer = false, hasMicrophone = false, hasProximity = false, restrictiveManufacturer = null)

        val result = toggle(ProtectionFeature.CLAP, false, permissions(microphone = PermissionStatus.DENIED))

        assertEquals(ToggleResult.Disabled, result)
        assertFalse(settings.settings.value.clapEnabled)
    }
}
