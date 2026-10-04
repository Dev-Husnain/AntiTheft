package com.anti.theft.data.service

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.anti.theft.R
import java.io.IOException

class AlarmPlayer(private val context: Context) {

    private val audioManager = context.getSystemService<AudioManager>()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService<Vibrator>()
    }

    private var player: MediaPlayer? = null
    private var previousAlarmVolume: Int? = null
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    val isPlaying: Boolean get() = player != null

    fun start() {
        if (player != null) return
        raiseAlarmVolume()
        requestFocus()
        play(sirenUri(), fallback = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM))
        startVibration()
    }

    fun stop() {
        player?.run {
            runCatching { if (isPlaying) stop() }
            release()
        }
        player = null
        vibrator?.cancel()
        abandonFocus()
        restoreAlarmVolume()
    }

    private fun play(uri: Uri, fallback: Uri?) {
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        try {
            mediaPlayer.setAudioAttributes(ALARM_ATTRIBUTES)
            mediaPlayer.setDataSource(context, uri)
            mediaPlayer.isLooping = true
            mediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            mediaPlayer.setOnPreparedListener { it.start() }
            mediaPlayer.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (player === mp) player = null
                if (fallback != null && fallback != uri) play(fallback, fallback = null)
                true
            }
            mediaPlayer.prepareAsync()
        } catch (e: IOException) {
            retryWith(mediaPlayer, uri, fallback)
        } catch (e: IllegalStateException) {
            retryWith(mediaPlayer, uri, fallback)
        } catch (e: IllegalArgumentException) {
            retryWith(mediaPlayer, uri, fallback)
        } catch (e: SecurityException) {
            retryWith(mediaPlayer, uri, fallback)
        }
    }

    private fun retryWith(failed: MediaPlayer, uri: Uri, fallback: Uri?) {
        failed.release()
        player = null
        if (fallback != null && fallback != uri) play(fallback, fallback = null)
    }

    private fun sirenUri(): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(R.raw.alarm_siren.toString())
        .build()

    private fun raiseAlarmVolume() {
        val audio = audioManager ?: return
        previousAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        setAlarmVolume(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM))
    }

    private fun restoreAlarmVolume() {
        previousAlarmVolume?.let(::setAlarmVolume)
        previousAlarmVolume = null
    }

    private fun setAlarmVolume(volume: Int) {
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
        } catch (e: SecurityException) {
        }
    }

    private fun requestFocus() {
        val audio = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(ALARM_ATTRIBUTES)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            audio.requestAudioFocus(request)
            focusRequest = request
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(focusListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun abandonFocus() {
        val audio = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audio::abandonAudioFocusRequest)
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(focusListener)
        }
    }

    private fun startVibration() {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> v.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, 0),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> @Suppress("DEPRECATION") v.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, 0),
                ALARM_ATTRIBUTES,
            )
            else -> @Suppress("DEPRECATION") v.vibrate(VIBRATION_PATTERN, 0, ALARM_ATTRIBUTES)
        }
    }

    private companion object {
        val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val VIBRATION_PATTERN = longArrayOf(0, 800, 400)
    }
}
