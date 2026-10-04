package com.anti.theft.domain.detector

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

class PocketDetector(
    private val armMillis: Long = 3_000,
    private val triggerMillis: Long = 1_000,
) {
    private val gravity = FloatArray(3)
    private var hasGravity = false
    private var covered: Boolean? = null
    private var pocketSinceNanos: Long? = null
    private var uncoveredSinceNanos: Long? = null
    private var armed = false
    private var triggered = false

    val inPocket: Boolean
        get() = covered == true && hasGravity && !isFlat()

    fun onProximity(covered: Boolean, timestampNanos: Long): PocketPhase {
        this.covered = covered
        return evaluate(timestampNanos)
    }

    fun onAcceleration(x: Float, y: Float, z: Float, timestampNanos: Long): PocketPhase {
        if (!hasGravity) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
            hasGravity = true
        } else {
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * x
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * y
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * z
        }
        return evaluate(timestampNanos)
    }

    private fun evaluate(now: Long): PocketPhase {
        if (triggered) return PocketPhase.Triggered
        if (armed) {
            if (covered == false) {
                val since = uncoveredSinceNanos ?: now.also { uncoveredSinceNanos = it }
                if (now - since >= triggerMillis * NANOS_PER_MILLI) {
                    triggered = true
                    return PocketPhase.Triggered
                }
            } else {
                uncoveredSinceNanos = null
            }
            return PocketPhase.Armed
        }
        if (!inPocket) {
            pocketSinceNanos = null
            return PocketPhase.WaitingForPocket
        }
        val since = pocketSinceNanos ?: now.also { pocketSinceNanos = it }
        val millisLeft = armMillis - (now - since) / NANOS_PER_MILLI
        if (millisLeft <= 0) {
            armed = true
            return PocketPhase.Armed
        }
        return PocketPhase.Arming(millisLeft)
    }

    private fun isFlat(): Boolean {
        val norm = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
        if (norm < MIN_GRAVITY_NORM) return false
        return abs(gravity[2]) / norm >= FLAT_COS
    }

    private companion object {
        const val ALPHA = 0.8f
        const val NANOS_PER_MILLI = 1_000_000L
        const val MIN_GRAVITY_NORM = 1f
        val FLAT_COS = cos(25.0 * PI / 180.0).toFloat()
    }
}
