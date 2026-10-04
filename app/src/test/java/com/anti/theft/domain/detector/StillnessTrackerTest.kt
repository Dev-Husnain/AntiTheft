package com.anti.theft.domain.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class StillnessTrackerTest {

    private val g = 9.81f
    private val stepNanos = 20_000_000L
    private val random = Random(3)
    private var clock = 0L

    private fun noise(amplitude: Float = 0.05f) = (random.nextFloat() * 2 - 1) * amplitude

    private fun StillnessTracker.feed(seconds: Double, sample: (Double) -> Triple<Float, Float, Float>): List<Long> {
        val count = (seconds * 50).toInt()
        return List(count) { i ->
            val (x, y, z) = sample(i / 50.0)
            clock += stepNanos
            onSample(x, y, z, clock)
        }
    }

    private fun StillnessTracker.lieFlat(seconds: Double) = feed(seconds) { Triple(noise(), noise(), g + noise()) }

    @Test
    fun `phone lying still settles after the required time`() {
        val tracker = StillnessTracker(5_000)

        val left = tracker.lieFlat(6.0)

        assertTrue(left[(4.0 * 50).toInt()] > 0)
        assertEquals(0L, left.last())
    }

    @Test
    fun `any movement restarts the countdown`() {
        val tracker = StillnessTracker(5_000)
        tracker.lieFlat(3.5)

        tracker.feed(0.3) { Triple(2f, 0f, g) }
        val afterMove = tracker.lieFlat(3.0)

        assertTrue(afterMove.last() > 0)
        assertEquals(0L, tracker.lieFlat(2.5).last())
    }

    @Test
    fun `walking with the phone in a pocket never settles`() {
        val tracker = StillnessTracker(5_000)

        val left = tracker.feed(30.0) { t ->
            val step = (2.5 * sin(2 * PI * 1.8 * t)).toFloat()
            val sway = (0.25 * sin(2 * PI * 0.9 * t))
            Triple(step * 0.3f + noise(), (g * sin(sway)).toFloat(), (g * cos(sway)).toFloat() + step)
        }

        assertTrue(left.all { it > 0 })
    }

    @Test
    fun `a slow continuous tilt never settles`() {
        val tracker = StillnessTracker(5_000)

        val left = tracker.feed(20.0) { t ->
            val angle = t * 2.0 * PI / 180.0
            Triple(noise(), (g * sin(angle)).toFloat(), (g * cos(angle)).toFloat())
        }

        assertTrue(left.all { it > 0 })
    }

    @Test
    fun `putting the phone away then setting it down arms only after it is still`() {
        val tracker = StillnessTracker(5_000)

        val handling = tracker.feed(4.0) { t ->
            val angle = (t / 4.0) * (PI / 2)
            Triple(noise(0.6f), (g * sin(angle)).toFloat(), (g * cos(angle)).toFloat())
        }
        val settled = tracker.feed(5.5) { Triple(noise(), g + noise(), noise()) }

        assertTrue(handling.all { it > 0 })
        assertEquals(0L, settled.last())
    }
}
