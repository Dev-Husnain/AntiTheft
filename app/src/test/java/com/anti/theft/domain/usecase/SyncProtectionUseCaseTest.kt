package com.anti.theft.domain.usecase

import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.ProtectionSettingsModel
import com.anti.theft.domain.models.Sensitivity
import com.anti.theft.fakes.FakeDeviceCapabilitiesRepository
import com.anti.theft.fakes.FakePermissionRepository
import com.anti.theft.fakes.FakeProtectionRepository
import com.anti.theft.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProtectionUseCaseTest {

    private val settings = FakeSettingsRepository(
        ProtectionSettingsModel(
            chargingEnabled = true,
            motionEnabled = true,
            clapEnabled = true,
            motionSensitivity = Sensitivity.HIGH,
        ),
    )
    private val permissions = FakePermissionRepository(granted = AppPermission.entries.toSet())
    private val capabilities = FakeDeviceCapabilitiesRepository()
    private val protection = FakeProtectionRepository()
    private val sync = SyncProtectionUseCase(
        ObserveEffectiveProtectionUseCase(settings, permissions, capabilities),
        protection,
    )

    @Test
    fun `runs every enabled guard when everything is permitted`() = runTest {
        sync()

        val applied = protection.applied.single()
        assertTrue(applied.charging && applied.motion && applied.clap)
        assertEquals(Sensitivity.HIGH, applied.motionSensitivity)
    }

    @Test
    fun `a revoked permission switches only its guard off`() = runTest {
        permissions.granted = setOf(AppPermission.NOTIFICATIONS)

        sync()

        val applied = protection.applied.single()
        assertTrue(applied.charging && applied.motion)
        assertFalse(applied.clap)
    }

    @Test
    fun `nothing enabled means nothing runs`() = runTest {
        settings.settings.value = ProtectionSettingsModel()

        sync()

        assertFalse(protection.applied.single().anyActive)
    }
}
