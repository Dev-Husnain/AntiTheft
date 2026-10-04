package com.anti.theft.data.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.anti.theft.core.constants.NotificationIds
import com.anti.theft.core.dispatchers.AppDispatchers
import com.anti.theft.data.local.ProtectionStateHolder
import com.anti.theft.domain.detector.ClapDetector
import com.anti.theft.domain.detector.MotionDetector
import com.anti.theft.domain.detector.PocketDetector
import com.anti.theft.domain.detector.PocketPhase
import com.anti.theft.domain.detector.StillnessTracker
import com.anti.theft.domain.models.AlarmTrigger
import com.anti.theft.domain.models.ClapState
import com.anti.theft.domain.models.EffectiveProtectionModel
import com.anti.theft.domain.models.LightSampleModel
import com.anti.theft.domain.models.MotionSampleModel
import com.anti.theft.domain.models.MotionState
import com.anti.theft.domain.models.PocketState
import com.anti.theft.domain.models.ProximitySampleModel
import com.anti.theft.domain.models.Sensitivity
import com.anti.theft.domain.usecase.ObserveEffectiveProtectionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class ProtectionService : Service() {

    private val dispatchers: AppDispatchers by inject()
    private val stateHolder: ProtectionStateHolder by inject()
    private val notifier: ProtectionNotifier by inject()
    private val alarmPlayer: AlarmPlayer by inject()
    private val chargingMonitor: ChargingMonitor by inject()
    private val motionSource: MotionSensorSource by inject()
    private val microphoneSource: MicrophoneSource by inject()
    private val proximitySource: ProximitySource by inject()
    private val lightSource: LightSource by inject()
    private val observeEffectiveProtection: ObserveEffectiveProtectionUseCase by inject()

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Protection stopped after an unexpected error", error)
        protectionJob = null
        shutDown()
    }
    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.main + exceptionHandler) }
    private var protectionJob: Job? = null
    private var lastStartId = 0

    private val ringing = MutableStateFlow<AlarmTrigger?>(null)
    private val micAllowed = MutableStateFlow(false)
    private val inPocket = MutableStateFlow(false)

    private val refresh = MutableStateFlow(0)

    override fun onCreate() {
        super.onCreate()
        notifier.cancelAlarm()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            if (protectionJob == null) stopSelfResult(startId)
            return START_STICKY
        }

        val fromUi = intent?.action == ACTION_START
        val wantsMicrophone = fromUi && intent.getBooleanExtra(EXTRA_CLAP, false)
        val microphoneIncluded = enterForeground(wantsMicrophone)
        if (microphoneIncluded == null) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (fromUi) micAllowed.value = microphoneIncluded
        refresh.update { it + 1 }
        if (protectionJob?.isActive != true) protectionJob = scope.launch { runProtection() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        alarmPlayer.stop()
        notifier.cancelAlarm()
        stateHolder.reset()
        super.onDestroy()
    }

    private fun enterForeground(wantsMicrophone: Boolean): Boolean? = when {
        wantsMicrophone && tryStartForeground(includeMicrophone = true) -> true
        tryStartForeground(includeMicrophone = false) -> false
        else -> null
    }

    private fun tryStartForeground(includeMicrophone: Boolean): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NotificationIds.PROTECTION,
            notifier.protectionNotification(null),
            foregroundTypes(includeMicrophone),
        )
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "startForeground refused (microphone=$includeMicrophone): ${e.javaClass.simpleName}")
        false
    }

    private fun foregroundTypes(includeMicrophone: Boolean): Int {
        var types = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        if (includeMicrophone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return types
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun runProtection() = coroutineScope {
        stateHolder.update { it.copy(serviceRunning = true) }
        val evaluations = refresh.flatMapLatest { observeEffectiveProtection() }
        val protection: StateFlow<EffectiveProtectionModel> = evaluations.stateIn(this)
        val isRinging = ringing.map { it != null }.distinctUntilChanged()

        launch {
            evaluations.collect { current ->
                if (current.anyActive) notifier.updateProtection(current) else shutDown()
            }
        }
        launch {
            protection.map { it.charging }.distinctUntilChanged().collectLatest { on ->
                if (on) isolated("charging") { watchCharging() }
            }
        }
        launch {
            combine(protection.map { it.motion to it.motionSensitivity }.distinctUntilChanged(), isRinging) { motion, ring ->
                motion to ring
            }.collectLatest { (motion, ring) ->
                val (on, sensitivity) = motion
                if (on && !ring) {
                    isolated("motion") { watchMotion(sensitivity) }
                }
                stateHolder.update { it.copy(motion = MotionState.Off) }
            }
        }
        launch {
            protection.map { (it.motion || it.pocket) && it.canSensePocket }.distinctUntilChanged().collectLatest { on ->
                inPocket.value = false
                if (on) isolated("pocket presence") { trackPocketPresence() }
            }
        }
        launch {
            combine(protection.map { it.pocket }.distinctUntilChanged(), isRinging) { on, ring -> on && !ring }
                .collectLatest { run ->
                    if (run) isolated("pocket") { watchPocket() }
                    stateHolder.update { it.copy(pocket = PocketState.Off) }
                }
        }
        launch {
            combine(
                protection.map { it.clap to it.clapSensitivity }.distinctUntilChanged(),
                isRinging,
                micAllowed,
            ) { clap, ring, allowed -> Triple(clap, ring, allowed) }
                .collectLatest { (clap, ring, allowed) ->
                    val (on, sensitivity) = clap
                    when {
                        !on || ring -> stateHolder.update { it.copy(clap = ClapState.OFF) }
                        !allowed -> stateHolder.update { it.copy(clap = ClapState.PAUSED_IN_BACKGROUND) }
                        else -> isolated("clap") { listenForClaps(sensitivity) }
                    }
                }
        }
    }

    private suspend inline fun isolated(guard: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$guard guard stopped: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun watchCharging() {
        stateHolder.update { it.copy(chargingWatching = true) }
        try {
            chargingMonitor.events().collect(notifier::showChargingAlert)
        } finally {
            stateHolder.update { it.copy(chargingWatching = false) }
        }
    }

    private suspend fun watchMotion(sensitivity: Sensitivity) = holdingWakeLock(MOTION_WAKE_LOCK_TAG) {
        var shown: MotionState = MotionState.Arming(ARMING_SECONDS)
        stateHolder.update { it.copy(motion = shown) }
        var stillness = StillnessTracker(ARMING_SECONDS * 1_000L)
        var detector: MotionDetector? = null
        fun publish(state: MotionState) {
            if (state != shown) {
                shown = state
                stateHolder.update { it.copy(motion = state) }
            }
        }
        withContext(dispatchers.default) {
            motionSource.samples().first { sample ->
                detector?.let { return@first it.onSample(sample.x, sample.y, sample.z) }
                if (inPocket.value) {
                    stillness = StillnessTracker(ARMING_SECONDS * 1_000L)
                    publish(MotionState.PausedInPocket)
                    return@first false
                }
                val millisLeft = stillness.onSample(sample.x, sample.y, sample.z, sample.timestampNanos)
                val secondsLeft = ((millisLeft + 999) / 1_000).toInt()
                if (secondsLeft == 0) {
                    detector = MotionDetector(sensitivity)
                    publish(MotionState.Armed)
                } else {
                    publish(MotionState.Arming(secondsLeft))
                }
                false
            }
        }
        triggerAlarm(AlarmTrigger.MOTION)
        awaitCancellation()
    }

    private suspend fun trackPocketPresence() = withContext(dispatchers.default) {
        val detector = PocketDetector()
        merge(motionSource.samples(), proximitySource.samples(), lightSource.samples()).collect { sample ->
            when (sample) {
                is MotionSampleModel -> detector.onAcceleration(sample.x, sample.y, sample.z, sample.timestampNanos)
                is ProximitySampleModel -> detector.onProximity(sample.covered, sample.timestampNanos)
                is LightSampleModel -> detector.onLight(sample.lux, sample.timestampNanos)
            }
            inPocket.value = detector.inPocket
        }
    }

    private suspend fun watchPocket() = holdingWakeLock(POCKET_WAKE_LOCK_TAG) {
        val detector = PocketDetector()
        var shown: PocketState = PocketState.WaitingForPocket
        stateHolder.update { it.copy(pocket = shown) }
        withContext(dispatchers.default) {
            merge(motionSource.samples(), proximitySource.samples(), lightSource.samples()).first { sample ->
                val phase = when (sample) {
                    is MotionSampleModel -> detector.onAcceleration(sample.x, sample.y, sample.z, sample.timestampNanos)
                    is ProximitySampleModel -> detector.onProximity(sample.covered, sample.timestampNanos)
                    is LightSampleModel -> detector.onLight(sample.lux, sample.timestampNanos)
                    else -> return@first false
                }
                val state = when (phase) {
                    PocketPhase.WaitingForPocket -> PocketState.WaitingForPocket
                    is PocketPhase.Arming -> PocketState.Arming(((phase.millisLeft + 999) / 1_000).toInt())
                    PocketPhase.Armed, PocketPhase.Triggered -> PocketState.Armed
                }
                if (state != shown) {
                    shown = state
                    stateHolder.update { it.copy(pocket = state) }
                }
                phase == PocketPhase.Triggered
            }
        }
        triggerAlarm(AlarmTrigger.POCKET)
        awaitCancellation()
    }

    private suspend fun holdingWakeLock(tag: String, block: suspend CoroutineScope.() -> Unit): Unit = coroutineScope {
        val lock = getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
            ?.apply { setReferenceCounted(false) }
        val renewal = launch {
            while (true) {
                lock?.acquire(WAKE_LOCK_TIMEOUT_MS)
                delay(WAKE_LOCK_RENEW_MS)
            }
        }
        try {
            block()
        } finally {
            renewal.cancel()
            lock?.takeIf { it.isHeld }?.release()
        }
    }

    private suspend fun listenForClaps(sensitivity: Sensitivity) {
        delay(CLAP_RESUME_DELAY_MS)
        stateHolder.update { it.copy(clap = ClapState.LISTENING) }
        try {
            val detector = ClapDetector(sensitivity)
            withContext(dispatchers.default) {
                microphoneSource.frames().first { detector.onFrame(it) }
            }
            triggerAlarm(AlarmTrigger.CLAP)
            awaitCancellation()
        } catch (e: MicrophoneUnavailableException) {
            stateHolder.update { it.copy(clap = ClapState.UNAVAILABLE) }
        }
    }

    private fun triggerAlarm(trigger: AlarmTrigger) {
        if (ringing.value != null) return
        getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, ALARM_START_WAKE_LOCK_TAG)
            ?.acquire(ALARM_START_WAKE_LOCK_MS)
        ringing.value = trigger
        stateHolder.update { it.copy(ringing = trigger) }
        alarmPlayer.start()
        notifier.showAlarm(trigger)
    }

    private fun stopAlarm() {
        alarmPlayer.stop()
        notifier.cancelAlarm()
        ringing.value = null
        stateHolder.update { it.copy(ringing = null) }
    }

    private fun shutDown() {
        stopAlarm()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(lastStartId)
    }

    companion object {
        const val ARMING_SECONDS = 5
        private const val TAG = "ProtectionService"
        private const val CLAP_RESUME_DELAY_MS = 800L
        private const val MOTION_WAKE_LOCK_TAG = "AntiTheft:motion"
        private const val POCKET_WAKE_LOCK_TAG = "AntiTheft:pocket"
        private const val ALARM_START_WAKE_LOCK_TAG = "AntiTheft:alarmStart"
        private const val ALARM_START_WAKE_LOCK_MS = 10_000L

        private const val WAKE_LOCK_TIMEOUT_MS = 2 * 60 * 60 * 1000L
        private const val WAKE_LOCK_RENEW_MS = 60 * 60 * 1000L

        private const val ACTION_START = "com.anti.theft.action.START"
        private const val ACTION_STOP_ALARM = "com.anti.theft.action.STOP_ALARM"
        private const val EXTRA_CLAP = "clap"

        fun startIntent(context: Context, clap: Boolean): Intent =
            Intent(context, ProtectionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CLAP, clap)

        fun stopAlarmIntent(context: Context): Intent =
            Intent(context, ProtectionService::class.java).setAction(ACTION_STOP_ALARM)
    }
}
