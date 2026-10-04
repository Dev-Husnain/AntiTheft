package com.anti.theft.data.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.anti.theft.core.dispatchers.AppDispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.max

/**
 * 20 ms frames of 16 kHz mono PCM from the microphone. Audio is analysed in memory only and
 * never stored. Throws [MicrophoneUnavailableException] if the microphone can't be opened.
 */
class MicrophoneSource(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {

    // Permission is checked explicitly on the first line of the flow.
    @SuppressLint("MissingPermission")
    fun frames(): Flow<ShortArray> = flow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw MicrophoneUnavailableException("RECORD_AUDIO not granted")
        }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) throw MicrophoneUnavailableException("Unsupported audio format")

        val record = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, max(minBuffer, FRAME_SIZE * 2 * 4))
        } catch (e: SecurityException) {
            throw MicrophoneUnavailableException("Microphone access denied: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw MicrophoneUnavailableException("Invalid audio parameters: ${e.message}")
        }
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw MicrophoneUnavailableException("AudioRecord failed to initialise")
            }
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw MicrophoneUnavailableException("Microphone is in use")
            }
            val buffer = ShortArray(FRAME_SIZE)
            while (currentCoroutineContext().isActive) {
                val read = record.read(buffer, 0, FRAME_SIZE)
                if (read < 0) throw MicrophoneUnavailableException("AudioRecord read error $read")
                if (read > 0) emit(buffer.copyOf(read))
            }
        } finally {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
        }
    }.flowOn(dispatchers.io)

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_SIZE = 320 // 20 ms
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
