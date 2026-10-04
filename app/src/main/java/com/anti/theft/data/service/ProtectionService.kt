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

/**
 * Foreground service that runs whichever guards are switched on, one coroutine each, and
 * publishes live state to [ProtectionStateHolder].
 *
 * Crash and ANR safety rules (see PROJECT-GUIDE §3):
 * - Every start command calls `startForeground()` first, synchronously, before any other
 *   work. If the microphone type is refused it retries without it, so a service started with
 *   `startForegroundService()` always reaches the foreground well inside Android's 5/10 s limit.
 * - Nobody calls `stopService()`. To stop, the UI sends another start command; the service
 *   enters the foreground, re-reads settings and then stops itself with `stopSelfResult()`,
 *   which keeps the service alive if a newer start is already queued.
 * - Each guard is isolated: a failing sensor or microphone stops only that guard. A scope
 *   exception handler catches anything else and shuts down cleanly instead of crashing.
 * - The main thread only does bookkeeping; sensor math runs on the default dispatcher and audio
 *   capture on IO.
 *
 * After a system-initiated sticky restart the microphone is not allowed (Android 11+), so clap
 * detection reports [ClapState.PAUSED_IN_BACKGROUND] until the app is opened again.
 */
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

    /** Bumped by every start command so settings and permissions are re-evaluated. */
    private val refresh = MutableStateFlow(0)

    override fun onCreate() {
        super.onCreate()
        // A previous process may have died mid-alarm, leaving its ongoing alarm notification behind.
        notifier.cancelAlarm()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_STOP_ALARM) {
            // Sent with startService() from the notification, so no foreground obligation here.
            stopAlarm()
            if (protectionJob == null) stopSelfResult(startId)
            return START_STICKY
        }

        // ACTION_START from the UI, or a null intent when the system restarts a sticky service.
        val fromUi = intent?.action == ACTION_START
        val wantsMicrophone = fromUi && intent.getBooleanExtra(EXTRA_CLAP, false)
        val microphoneIncluded = enterForeground(wantsMicrophone)
        if (microphoneIncluded == null) {
            // Only reachable when Android refuses the foreground outright, which happens for a
            // background restart (not a startForegroundService() call), so stopping is safe.
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

    /**
     * Enters the foreground. Returns whether the microphone type was included, or null if the
     * service could not enter the foreground at all.
     */
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
        // ForegroundServiceStartNotAllowedException / ForegroundServiceTypeException (both
        // IllegalStateException) or SecurityException when a type's permission isn't usable now.
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
        // Every start command re-subscribes, so each one yields a fresh evaluation even when the
        // result is unchanged; otherwise a "re-check and stop" request could be silently ignored.
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

    /** Runs one guard so that its failure stops only that guard, never the service. */
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
        // Renew well before the timeout so an armed phone keeps sensing with the screen off.
        val wakeLockRenewal = launch {
            while (true) {
                acquireWakeLock()
                delay(WAKE_LOCK_RENEW_MS)
            }
        }
        try {
            // Give the user time to put the phone down (after enabling, or after stopping an alarm).
            for (secondsLeft in ARMING_SECONDS downTo 1) {
                stateHolder.update { it.copy(motion = MotionState.Arming(secondsLeft)) }
                delay(1_000)
            }
            stateHolder.update { it.copy(motion = MotionState.Armed) }
            val detector = MotionDetector(sensitivity)
            // ~50 samples a second: keep the maths off the main thread.
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
        // Let the end of a just-stopped siren die away before listening.
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
        // Keep the CPU awake until the siren's own wake lock takes over once playback starts.
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
        // False when a newer start command is queued: that command re-enters the foreground and
        // re-evaluates settings, so the service simply carries on.
        stopSelfResult(lastStartId)
    }

    /** Acquires, or extends an already-held lock to a fresh timeout. */
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

        // The timeout only guards against a leak; the lock is renewed every hour while armed.
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
