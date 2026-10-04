package com.anti.theft.domain.detector

import com.anti.theft.domain.models.Sensitivity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

class MotionDetector(sensitivity: Sensitivity) {

    private val linearThreshold: Float
    private val minTiltCos: Float

    init {
        val (linear, tiltDegrees) = when (sensitivity) {
            Sensitivity.LOW -> 2.5f to 30.0
            Sensitivity.MEDIUM -> 1.2f to 15.0
            Sensitivity.HIGH -> 0.6f to 8.0
        }
        linearThreshold = linear
        minTiltCos = cos(tiltDegrees * PI / 180.0).toFloat()
    }

    private val gravity = FloatArray(3)
    private var baseline: FloatArray? = null
    private var samples = 0
    private var consecutive = 0

    fun onSample(x: Float, y: Float, z: Float): Boolean {
        samples++
        if (samples == 1) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
        } else {
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * x
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * y
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * z
        }
        if (samples <= WARM_UP_SAMPLES) return false
        val reference = baseline ?: gravity.copyOf().also { baseline = it }

        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]
        val linear = sqrt(lx * lx + ly * ly + lz * lz)

        val moving = linear > linearThreshold || tiltCos(reference) < minTiltCos
        consecutive = if (moving) consecutive + 1 else 0
        return consecutive >= REQUIRED_CONSECUTIVE
    }

    private fun tiltCos(reference: FloatArray): Float {
        val gNorm = norm(gravity)
        val rNorm = norm(reference)
        if (gNorm < MIN_GRAVITY_NORM || rNorm < MIN_GRAVITY_NORM) return 1f
        val dot = gravity[0] * reference[0] + gravity[1] * reference[1] + gravity[2] * reference[2]
        return dot / (gNorm * rNorm)
    }

    private fun norm(v: FloatArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    private companion object {
        const val ALPHA = 0.8f
        const val WARM_UP_SAMPLES = 25
        const val REQUIRED_CONSECUTIVE = 3
        const val MIN_GRAVITY_NORM = 1f
    }
}
