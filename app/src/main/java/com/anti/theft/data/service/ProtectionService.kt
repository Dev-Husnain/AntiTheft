package com.anti.theft.data.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service that runs whichever guards are switched on, one coroutine each, and
 * publishes live state to [ProtectionStateHolder]. It stops itself when nothing is enabled.
 *
 * Started only from the UI (a foreground context), which Android 12+ requires for starting a
 * foreground service and Android 11+ requires for background microphone access. After a
 * system-initiated sticky restart the microphone is not allowed, so clap detection reports
 * [ClapState.PAUSED_IN_BACKGROUND] until the app is opened again.
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

    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.main) }
    private var protectionJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val ringing = MutableStateFlow<AlarmTrigger?>(null)
    private val micAllowed = MutableStateFlow(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            if (protectionJob == null) stopSelf()
            return START_STICKY
        }
        // ACTION_START from the UI, or a null intent when the system restarts a sticky service.
        val fromUi = intent?.action == ACTION_START
        val wantsMicrophone = fromUi && intent.getBooleanExtra(EXTRA_CLAP, false)
        if (!enterForeground(includeMicrophone = wantsMicrophone)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (fromUi) micAllowed.value = wantsMicrophone
        if (protectionJob == null) protectionJob = scope.launch { runProtection() }
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

    private fun enterForeground(includeMicrophone: Boolean): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NotificationIds.PROTECTION,
            notifier.protectionNotification(null),
            foregroundTypes(includeMicrophone),
        )
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException (API 31+): started from the background.
        false
    } catch (e: SecurityException) {
        // API 34+: a required type permission (e.g. microphone) is missing or not usable now.
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

    private suspend fun runProtection() = coroutineScope {
        stateHolder.update { it.copy(serviceRunning = true) }
        val protection: StateFlow<EffectiveProtectionModel> = observeEffectiveProtection().stateIn(this)
        val isRinging = ringing.map { it != null }.distinctUntilChanged()

        launch {
            protection.collect { current ->
                if (current.anyActive) notifier.updateProtection(current) else shutDown()
            }
        }
        launch {
            protection.map { it.charging }.distinctUntilChanged().collectLatest { on ->
                if (on) watchCharging()
            }
        }
        launch {
            combine(protection.map { it.motion to it.motionSensitivity }.distinctUntilChanged(), isRinging) { motion, ring ->
                motion to ring
            }.collectLatest { (motion, ring) ->
                val (on, sensitivity) = motion
                if (on && !ring) watchMotion(sensitivity) else stateHolder.update { it.copy(motion = MotionState.Off) }
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
                        else -> listenForClaps(sensitivity)
                    }
                }
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

    private suspend fun watchMotion(sensitivity: Sensitivity) {
        acquireWakeLock()
        try {
            // Give the user time to put the phone down (after enabling, or after stopping an alarm).
            for (secondsLeft in ARMING_SECONDS downTo 1) {
                stateHolder.update { it.copy(motion = MotionState.Arming(secondsLeft)) }
                delay(1_000)
            }
            stateHolder.update { it.copy(motion = MotionState.Armed) }
            val detector = MotionDetector(sensitivity)
            motionSource.samples().first { detector.onSample(it[0], it[1], it[2]) }
            triggerAlarm(AlarmTrigger.MOTION)
            awaitCancellation()
        } finally {
            releaseWakeLock()
        }
    }

    private suspend fun listenForClaps(sensitivity: Sensitivity) {
        // Let the end of a just-stopped siren die away before listening.
        delay(CLAP_RESUME_DELAY_MS)
        stateHolder.update { it.copy(clap = ClapState.LISTENING) }
        try {
            val detector = ClapDetector(sensitivity)
            microphoneSource.frames().first { detector.onFrame(it) }
            triggerAlarm(AlarmTrigger.CLAP)
            awaitCancellation()
        } catch (e: MicrophoneUnavailableException) {
            stateHolder.update { it.copy(clap = ClapState.UNAVAILABLE) }
        }
    }

    private fun triggerAlarm(trigger: AlarmTrigger) {
        if (ringing.value != null) return
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
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val ARMING_SECONDS = 5
        private const val CLAP_RESUME_DELAY_MS = 800L
        private const val WAKE_LOCK_TAG = "AntiTheft:motion"

        // Re-acquired every time motion re-arms; the cap only guards against a leak.
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L

        private const val ACTION_START = "com.anti.theft.action.START"
        private const val ACTION_STOP_ALARM = "com.anti.theft.action.STOP_ALARM"
        private const val EXTRA_CLAP = "clap"

        fun startIntent(context: Context, clap: Boolean): Intent =
            Intent(context, ProtectionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CLAP, clap)

        fun stopAlarmIntent(context: Context): Intent =
            Intent(context, ProtectionService::class.java).setAction(ACTION_STOP_ALARM)

        fun intent(context: Context): Intent = Intent(context, ProtectionService::class.java)
    }
}
