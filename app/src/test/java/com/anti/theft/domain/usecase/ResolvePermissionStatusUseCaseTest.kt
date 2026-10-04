package com.anti.theft.domain.usecase

import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.PermissionStatus
import com.anti.theft.fakes.FakePermissionRepository
import com.anti.theft.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvePermissionStatusUseCaseTest {

    private val permissions = FakePermissionRepository()
    private val settings = FakeSettingsRepository()
    private val resolve = ResolvePermissionStatusUseCase(permissions, settings)

    @Test
    fun `granted wins regardless of history`() = runTest {
        permissions.granted = setOf(AppPermission.MICROPHONE)
        settings.markPermissionRequested(AppPermission.MICROPHONE)

        assertEquals(PermissionStatus.GRANTED, resolve(emptyMap()).microphone)
    }

    @Test
    fun `never requested is not requested`() = runTest {
        assertEquals(PermissionStatus.NOT_REQUESTED, resolve(emptyMap()).microphone)
    }

    @Test
    fun `requested with rationale is a recoverable denial`() = runTest {
        settings.markPermissionRequested(AppPermission.MICROPHONE)

        val state = resolve(mapOf(AppPermission.MICROPHONE to true))

        assertEquals(PermissionStatus.DENIED, state.microphone)
    }

    @Test
    fun `requested without rationale is permanent denial`() = runTest {
        settings.markPermissionRequested(AppPermission.MICROPHONE)

        val state = resolve(mapOf(AppPermission.MICROPHONE to false))

        assertEquals(PermissionStatus.PERMANENTLY_DENIED, state.microphone)
    }

    @Test
    fun `notifications off below API 33 can only be fixed in settings`() = runTest {
        permissions.runtimeRequestable = setOf(AppPermission.MICROPHONE)

        assertEquals(PermissionStatus.PERMANENTLY_DENIED, resolve(emptyMap()).notifications)
    }

    @Test
    fun `reports blocked channels`() = runTest {
        permissions.chargingChannelBlocked = true

        val state = resolve(emptyMap())

        assertTrue(state.chargingChannelBlocked)
        assertEquals(false, state.alarmChannelBlocked)
    }
}
