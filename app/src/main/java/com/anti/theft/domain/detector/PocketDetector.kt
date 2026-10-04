package com.anti.theft.domain.detector

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

class PocketDetector(
    private val armMillis: Long = 3_000,
    private val triggerMillis: Long = 1_000,
) {
    private val gravity = FloatArray(3)
    private var hasGravity = false

    private var proximityNear: Boolean? = null
    private var proximityProvenWorking = false
    private var lux: Float? = null
    private var pocketLux = 0f

    private var pocketSinceNanos: Long? = null
    private var outSinceNanos: Long? = null
    private var armed = false
    private var triggered = false

    val inPocket: Boolean
        get() = hasGravity && !isFlat() && (proximitySaysCovered() || lightSaysDark())

    fun onProximity(near: Boolean, timestampNanos: Long): PocketPhase {
        proximityNear = near
        if (near) proximityProvenWorking = true
        return evaluate(timestampNanos)
    }

    fun onLight(lux: Float, timestampNanos: Long): PocketPhase {
        this.lux = lux
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
            if (takenOut()) {
                val since = outSinceNanos ?: now.also { outSinceNanos = it }
                if (now - since >= triggerMillis * NANOS_PER_MILLI) {
                    triggered = true
                    return PocketPhase.Triggered
                }
            } else {
                outSinceNanos = null
            }
            return PocketPhase.Armed
        }
        if (!inPocket) {
            pocketSinceNanos = null
            pocketLux = 0f
            return PocketPhase.WaitingForPocket
        }
        lux?.let { pocketLux = max(pocketLux, it) }
        val since = pocketSinceNanos ?: now.also { pocketSinceNanos = it }
        val millisLeft = armMillis - (now - since) / NANOS_PER_MILLI
        if (millisLeft <= 0) {
            armed = true
            return PocketPhase.Armed
        }
        return PocketPhase.Arming(millisLeft)
    }

    private fun proximitySaysCovered() = proximityProvenWorking && proximityNear == true

    private fun lightSaysDark() = lux?.let { it <= DARK_LUX } == true

    private fun takenOut(): Boolean {
        val proximityOut = proximityProvenWorking && proximityNear == false
        val lightOut = lux?.let { it >= max(MIN_OUT_LUX, pocketLux * OUT_LUX_FACTOR) } == true
        return proximityOut || lightOut
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
        const val DARK_LUX = 5f
        const val MIN_OUT_LUX = 20f
        const val OUT_LUX_FACTOR = 8f
        val FLAT_COS = cos(25.0 * PI / 180.0).toFloat()
    }
}
