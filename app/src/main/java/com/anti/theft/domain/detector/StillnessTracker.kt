package com.anti.theft.domain.detector

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

class StillnessTracker(private val requiredMillis: Long) {

    private val gravity = FloatArray(3)
    private var anchor: FloatArray? = null
    private var stillSinceNanos = 0L
    private var samples = 0

    fun onSample(x: Float, y: Float, z: Float, timestampNanos: Long): Long {
        samples++
        if (samples == 1) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
        } else {
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * x
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * y
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * z
        }
        val reference = anchor
        if (samples <= WARM_UP_SAMPLES || reference == null) {
            restart(timestampNanos)
            return requiredMillis
        }
        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]
        val linear = sqrt(lx * lx + ly * ly + lz * lz)
        if (linear > MAX_STILL_LINEAR || cosBetween(gravity, reference) < MIN_STILL_TILT_COS) {
            restart(timestampNanos)
            return requiredMillis
        }
        val stillMillis = (timestampNanos - stillSinceNanos) / 1_000_000
        return max(0L, requiredMillis - stillMillis)
    }

    private fun restart(timestampNanos: Long) {
        anchor = gravity.copyOf()
        stillSinceNanos = timestampNanos
    }

    private fun cosBetween(a: FloatArray, b: FloatArray): Float {
        val na = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
        val nb = sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2])
        if (na < MIN_GRAVITY_NORM || nb < MIN_GRAVITY_NORM) return -1f
        return (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (na * nb)
    }

    private companion object {
        const val ALPHA = 0.8f
        const val WARM_UP_SAMPLES = 10
        const val MAX_STILL_LINEAR = 0.5f
        val MIN_STILL_TILT_COS = cos(5.0 * PI / 180.0).toFloat()
        const val MIN_GRAVITY_NORM = 1f
    }
}
