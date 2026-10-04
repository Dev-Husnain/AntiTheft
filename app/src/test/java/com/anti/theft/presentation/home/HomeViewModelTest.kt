package com.anti.theft.presentation.home

import androidx.lifecycle.SavedStateHandle
import com.anti.theft.domain.models.AlarmTrigger
import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.MotionState
import com.anti.theft.domain.models.ProtectionFeature
import com.anti.theft.domain.usecase.ObserveEffectiveProtectionUseCase
import com.anti.theft.domain.usecase.ResolvePermissionStatusUseCase
import com.anti.theft.domain.usecase.SyncProtectionUseCase
import com.anti.theft.domain.usecase.ToggleFeatureUseCase
import com.anti.theft.fakes.FakeDeviceCapabilitiesRepository
import com.anti.theft.fakes.FakePermissionRepository
import com.anti.theft.fakes.FakeProtectionRepository
import com.anti.theft.fakes.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val settings = FakeSettingsRepository()
    private val permissions = FakePermissionRepository(granted = setOf(AppPermission.NOTIFICATIONS))
    private val capabilities = FakeDeviceCapabilitiesRepository()
    private val protection = FakeProtectionRepository()
    private val savedState = SavedStateHandle()
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = createViewModel()
        viewModel.onEvent(HomeEvent.OnResumed(emptyMap()))
    }

    private fun createViewModel() = HomeViewModel(
            settings = settings,
            protection = protection,
            capabilities = capabilities,
            toggleFeature = ToggleFeatureUseCase(settings, capabilities),
            resolvePermissions = ResolvePermissionStatusUseCase(permissions, settings),
            syncProtection = SyncProtectionUseCase(
                ObserveEffectiveProtectionUseCase(settings, permissions, capabilities),
                protection,
            ),
            savedState = savedState,
        )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.homeUiState.value
    private fun card(feature: ProtectionFeature) = state.features.first { it.feature == feature }

    @Test
    fun `starts with everything off once permissions are known`() {
        assertFalse(state.isLoading)
        assertEquals(HeroUiModel.Off, state.hero)
        assertTrue(state.features.none { it.checked })
    }

    @Test
    fun `enabling charging with notifications granted starts protection`() {
        viewModel.onEvent(HomeEvent.OnFeatureToggled(ProtectionFeature.CHARGING, true))

        assertTrue(card(ProtectionFeature.CHARGING).checked)
        assertTrue(protection.applied.last().charging)
        assertEquals(HeroUiModel.Protected(1), state.hero)
    }

    @Test
    fun `clap without microphone shows the rationale then requests the permission`() = runTest {
        val effects = mutableListOf<HomeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onEvent(HomeEvent.OnFeatureToggled(ProtectionFeature.CLAP, true))
        val sheet = state.permissionSheet
        assertNotNull(sheet)
        assertFalse(sheet!!.opensSettings)

        viewModel.onEvent(HomeEvent.OnSheetConfirmed)

        assertNull(state.permissionSheet)
        assertEquals(HomeEffect.RequestPermission(AppPermission.MICROPHONE), effects.last())
    }

    @Test
    fun `granting the microphone finishes enabling clap detection`() {
        viewModel.onEvent(HomeEvent.OnFeatureToggled(ProtectionFeature.CLAP, true))
        viewModel.onEvent(HomeEvent.OnSheetConfirmed)

        permissions.granted = permissions.granted + AppPermission.MICROPHONE
        viewModel.onEvent(HomeEvent.OnPermissionResult(granted = true, rationale = emptyMap()))

        assertTrue(card(ProtectionFeature.CLAP).checked)
        assertTrue(protection.applied.last().clap)
    }

    @Test
    fun `second denial without rationale offers settings`() = runTest {
        val effects = mutableListOf<HomeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onEvent(HomeEvent.OnFeatureToggled(ProtectionFeature.CLAP, true))
        viewModel.onEvent(HomeEvent.OnSheetConfirmed)
        viewModel.onEvent(HomeEvent.OnPermissionResult(granted = false, rationale = emptyMap()))

        assertEquals(NoticeAction.FIX_PERMISSION, card(ProtectionFeature.CLAP).notice?.action)

        viewModel.onEvent(HomeEvent.OnNoticeAction(ProtectionFeature.CLAP, NoticeAction.FIX_PERMISSION))
        assertTrue(state.permissionSheet!!.opensSettings)

        viewModel.onEvent(HomeEvent.OnSheetConfirmed)
        assertEquals(HomeEffect.OpenAppSettings, effects.last())
    }

    @Test
    fun `returning from settings with the permission enables the pending guard`() {
        settings.requestedPermissions.value = setOf(AppPermission.MICROPHONE)
        viewModel.onEvent(HomeEvent.OnResumed(emptyMap()))
        viewModel.onEvent(HomeEvent.OnFeatureToggled(ProtectionFeature.CLAP, true))
        viewModel.onEvent(HomeEvent.OnSheetConfirmed) // opens Settings

        permissions.granted = permissions.granted + AppPermission.MICROPHONE
        viewModel.onEvent(HomeEvent.OnResumed(emptyMap()))

        assertTrue(card(ProtectionFeature.CLAP).checked)
    }

    @Test
    fun `permission result after process death still enables the pending guard`() {
        viewModel.onEvent(HomeEvent.OnFeatureToggled(ProtectionFeature.CLAP, true))
        viewModel.onEvent(HomeEvent.OnSheetConfirmed)

        // Process dies while the system dialog is showing; a new ViewModel gets the result.
        val restored = createViewModel()
        permissions.granted = permissions.granted + AppPermission.MICROPHONE
        restored.onEvent(HomeEvent.OnPermissionResult(granted = true, rationale = emptyMap()))

        assertTrue(restored.homeUiState.value.features.first { it.feature == ProtectionFeature.CLAP }.checked)
    }

    @Test
    fun `hero follows arming and ringing from the service`() {
        viewModel.onEvent(HomeEvent.OnFeatureToggled(ProtectionFeature.MOTION, true))

        protection.status.value = protection.status.value.copy(serviceRunning = true, motion = MotionState.Arming(3))
        assertEquals(HeroUiModel.Arming(3), state.hero)

        protection.status.value = protection.status.value.copy(motion = MotionState.Off, ringing = AlarmTrigger.MOTION)
        assertEquals(HeroUiModel.Ringing(AlarmTrigger.MOTION), state.hero)

        viewModel.onEvent(HomeEvent.OnStopAlarmClick)
        assertEquals(1, protection.stopAlarmCalls)
    }

    @Test
    fun `failed start shows paused hero and a message`() = runTest {
        val effects = mutableListOf<HomeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        protection.applyResult = Result.failure(IllegalStateException("background start"))

        viewModel.onEvent(HomeEvent.OnFeatureToggled(ProtectionFeature.MOTION, true))

        assertEquals(HeroUiModel.Paused, state.hero)
        assertTrue(effects.last() is HomeEffect.ShowMessage)
    }
}
