package com.anti.theft.domain.detector

import com.anti.theft.domain.models.Sensitivity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class ClapDetector(
    sensitivity: Sensitivity,
    sampleRate: Int = 16_000,
) {
    private val peakThreshold: Float
    private val onsetRatio: Float
    private val minHighFrequencyShare: Float

    init {
        when (sensitivity) {
            Sensitivity.LOW -> {
                peakThreshold = 0.38f; onsetRatio = 8f; minHighFrequencyShare = 0.35f
            }
            Sensitivity.MEDIUM -> {
                peakThreshold = 0.22f; onsetRatio = 6f; minHighFrequencyShare = 0.28f
            }
            Sensitivity.HIGH -> {
                peakThreshold = 0.12f; onsetRatio = 4f; minHighFrequencyShare = 0.22f
            }
        }
    }

    private val highPass = HighPassFilter(HIGH_PASS_HZ, sampleRate)
    private val samplesPerMs = max(1, sampleRate / 1000)

    private enum class Phase { IDLE, EVENT, SUSTAINED }

    private var phase = Phase.IDLE
    private var noiseFloor = INITIAL_FLOOR
    private var cooldown = 0
    private var previousFrame = ShortArray(0)

    private var eventFrames = 0
    private var eventPeakRms = 0f
    private var eventHighEnergy = 0.0
    private var eventTotalEnergy = 0.0
    private var eventSharpAttack = false

    fun onFrame(buffer: ShortArray, length: Int = buffer.size): Boolean {
        if (length <= 0) return false

        var peak = 0
        var total = 0.0
        var high = 0.0
        for (i in 0 until length) {
            val s = buffer[i].toInt()
            peak = max(peak, abs(s))
            val x = s / FULL_SCALE
            total += x * x
            val h = highPass.process(x)
            high += h * h
        }
        val peakNorm = peak / FULL_SCALE
        val rms = sqrt(total / length).toFloat()

        val clap = when (phase) {
            Phase.IDLE -> {
                when {
                    cooldown > 0 -> cooldown--
                    peakNorm >= peakThreshold && rms >= noiseFloor * onsetRatio -> {
                        phase = Phase.EVENT
                        eventFrames = 1
                        eventPeakRms = rms
                        eventHighEnergy = high
                        eventTotalEnergy = total
                        eventSharpAttack = riseTimeMs(previousFrame, buffer, length) <= MAX_RISE_MS
                    }
                    else -> adaptFloor(rms)
                }
                false
            }
            Phase.EVENT -> {
                eventFrames++
                if (eventFrames <= SPECTRUM_FRAMES) {
                    eventHighEnergy += high
                    eventTotalEnergy += total
                }
                eventPeakRms = max(eventPeakRms, rms)
                when {
                    rms < eventPeakRms * DECAY_RATIO -> {
                        phase = Phase.IDLE
                        cooldown = COOLDOWN_FRAMES
                        val share = if (eventTotalEnergy > 0) eventHighEnergy / eventTotalEnergy else 0.0
                        eventSharpAttack && share >= minHighFrequencyShare
                    }
                    eventFrames > MAX_EVENT_FRAMES -> {
                        phase = Phase.SUSTAINED
                        false
                    }
                    else -> false
                }
            }
            Phase.SUSTAINED -> {
                adaptFloor(rms)
                if (rms < eventPeakRms * DECAY_RATIO || rms < noiseFloor * 2) phase = Phase.IDLE
                false
            }
        }

        if (previousFrame.size != length) previousFrame = ShortArray(length)
        buffer.copyInto(previousFrame, endIndex = length)
        return clap
    }

    private fun riseTimeMs(previous: ShortArray, current: ShortArray, length: Int): Int {
        val blockSize = samplesPerMs
        val totalSamples = previous.size + length
        val blocks = totalSamples / blockSize
        if (blocks == 0) return Int.MAX_VALUE
        val envelope = IntArray(blocks)
        for (b in 0 until blocks) {
            var blockPeak = 0
            for (i in b * blockSize until (b + 1) * blockSize) {
                val s = if (i < previous.size) previous[i].toInt() else current[i - previous.size].toInt()
                blockPeak = max(blockPeak, abs(s))
            }
            envelope[b] = blockPeak
        }
        var peakBlock = 0
        for (b in 1 until blocks) if (envelope[b] > envelope[peakBlock]) peakBlock = b
        val quiet = envelope[peakBlock] * RISE_FLOOR
        for (b in peakBlock - 1 downTo 0) {
            if (envelope[b] < quiet) return peakBlock - b
        }
        return Int.MAX_VALUE
    }

    private fun adaptFloor(rms: Float) {
        noiseFloor = max(MIN_FLOOR, noiseFloor * (1 - FLOOR_RATE) + rms * FLOOR_RATE)
    }

    private class HighPassFilter(cutoffHz: Double, sampleRate: Int) {
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        init {
            val w0 = 2 * PI * cutoffHz / sampleRate
            val alpha = sin(w0) / (2 * (1 / sqrt(2.0)))
            val cosW0 = cos(w0)
            val a0 = 1 + alpha
            b0 = (1 + cosW0) / 2 / a0
            b1 = -(1 + cosW0) / a0
            b2 = (1 + cosW0) / 2 / a0
            a1 = -2 * cosW0 / a0
            a2 = (1 - alpha) / a0
        }

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x
            y2 = y1; y1 = y
            return y
        }
    }

    private companion object {
        const val FULL_SCALE = 32768.0
        const val HIGH_PASS_HZ = 2_000.0
        const val INITIAL_FLOOR = 0.01f
        const val MIN_FLOOR = 0.002f
        const val FLOOR_RATE = 0.05f
        const val DECAY_RATIO = 0.3f
        const val MAX_EVENT_FRAMES = 6
        const val SPECTRUM_FRAMES = 2
        const val MAX_RISE_MS = 6
        const val RISE_FLOOR = 0.2
        const val COOLDOWN_FRAMES = 15
    }
}
