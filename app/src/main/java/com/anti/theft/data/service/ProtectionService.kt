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
import com.anti.theft.domain.models.AlarmTrigger
import com.anti.theft.domain.models.ClapState
import com.anti.theft.domain.models.EffectiveProtectionModel
import com.anti.theft.domain.models.MotionState
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
    private val observeEffectiveProtection: ObserveEffectiveProtectionUseCase by inject()

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Protection stopped after an unexpected error", error)
        protectionJob = null
        shutDown()
    }
    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.main + exceptionHandler) }
    private var protectionJob: Job? = null
    private var lastStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null

    private val ringing = MutableStateFlow<AlarmTrigger?>(null)
    private val micAllowed = MutableStateFlow(false)

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
        releaseWakeLock()
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

    private suspend fun watchMotion(sensitivity: Sensitivity): Unit = coroutineScope {
        val wakeLockRenewal = launch {
            while (true) {
                acquireWakeLock()
                delay(WAKE_LOCK_RENEW_MS)
            }
        }
        try {
            for (secondsLeft in ARMING_SECONDS downTo 1) {
                stateHolder.update { it.copy(motion = MotionState.Arming(secondsLeft)) }
                delay(1_000)
            }
            stateHolder.update { it.copy(motion = MotionState.Armed) }
            val detector = MotionDetector(sensitivity)
            withContext(dispatchers.default) {
                motionSource.samples().first { detector.onSample(it[0], it[1], it[2]) }
            }
            triggerAlarm(AlarmTrigger.MOTION)
            awaitCancellation()
        } finally {
            wakeLockRenewal.cancel()
            releaseWakeLock()
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

    private fun acquireWakeLock() {
        val lock = wakeLock ?: getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }
            ?.also { wakeLock = it }
        lock?.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val ARMING_SECONDS = 5
        private const val TAG = "ProtectionService"
        private const val CLAP_RESUME_DELAY_MS = 800L
        private const val WAKE_LOCK_TAG = "AntiTheft:motion"
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
