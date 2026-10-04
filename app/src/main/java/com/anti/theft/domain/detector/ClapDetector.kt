package com.anti.theft.domain.detector

import com.anti.theft.domain.models.Sensitivity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Detects a hand clap in a stream of 16-bit PCM frames (~20 ms each).
 *
 * A clap is a sharp transient: a frame far louder than the background (the adaptive noise
 * floor) that dies away again within [MAX_CLAP_FRAMES] frames. Sounds that stay loud
 * (music, speech, a vacuum cleaner) are rejected, and the floor adapts to them. After a
 * clap, [COOLDOWN_FRAMES] frames are ignored so its echo doesn't count twice.
 */
class ClapDetector(sensitivity: Sensitivity) {

    private val peakThreshold: Float
    private val onsetRatio: Float

    init {
        val (peak, ratio) = when (sensitivity) {
            Sensitivity.LOW -> 0.55f to 8f
            Sensitivity.MEDIUM -> 0.35f to 5f
            Sensitivity.HIGH -> 0.20f to 3f
        }
        peakThreshold = peak
        onsetRatio = ratio
    }

    private enum class Phase { IDLE, ONSET, SUSTAINED }

    private var phase = Phase.IDLE
    private var noiseFloor = INITIAL_FLOOR
    private var onsetRms = 0f
    private var loudFrames = 0
    private var cooldown = 0

    /** Returns true when the frame completes a clap. */
    fun onFrame(buffer: ShortArray, length: Int = buffer.size): Boolean {
        if (length <= 0) return false
        var peak = 0
        var sumSquares = 0.0
        for (i in 0 until length) {
            val s = buffer[i].toInt()
            peak = max(peak, abs(s))
            sumSquares += s.toDouble() * s
        }
        val peakNorm = peak / FULL_SCALE
        val rms = (sqrt(sumSquares / length) / FULL_SCALE).toFloat()

        if (cooldown > 0) {
            cooldown--
            return false
        }
        when (phase) {
            Phase.IDLE -> {
                if (peakNorm >= peakThreshold && rms >= noiseFloor * onsetRatio) {
                    phase = Phase.ONSET
                    onsetRms = rms
                    loudFrames = 1
                } else {
                    adaptFloor(rms)
                }
            }
            Phase.ONSET -> {
                if (rms < onsetRms * DECAY_RATIO) {
                    phase = Phase.IDLE
                    cooldown = COOLDOWN_FRAMES
                    return true
                }
                onsetRms = max(onsetRms, rms)
                loudFrames++
                if (loudFrames > MAX_CLAP_FRAMES) phase = Phase.SUSTAINED
            }
            Phase.SUSTAINED -> {
                adaptFloor(rms)
                if (rms < onsetRms * DECAY_RATIO) phase = Phase.IDLE
            }
        }
        return false
    }

    private fun adaptFloor(rms: Float) {
        noiseFloor = max(MIN_FLOOR, noiseFloor * (1 - FLOOR_RATE) + rms * FLOOR_RATE)
    }

    private companion object {
        const val FULL_SCALE = 32768f
        const val INITIAL_FLOOR = 0.01f
        const val MIN_FLOOR = 0.002f
        const val FLOOR_RATE = 0.05f
        const val DECAY_RATIO = 0.5f
        const val MAX_CLAP_FRAMES = 4
        const val COOLDOWN_FRAMES = 15
    }
}
