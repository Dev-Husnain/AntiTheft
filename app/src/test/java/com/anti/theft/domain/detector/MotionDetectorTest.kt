package com.anti.theft.domain.detector

import com.anti.theft.domain.models.Sensitivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MotionDetectorTest {

    private val g = 9.81f
    private val random = Random(42)

    private fun noise(amplitude: Float = 0.05f) = (random.nextFloat() * 2 - 1) * amplitude

    private fun MotionDetector.feed(count: Int, sample: (Int) -> Triple<Float, Float, Float>): Boolean {
        var triggered = false
        repeat(count) { i ->
            val (x, y, z) = sample(i)
            if (onSample(x, y, z)) triggered = true
        }
        return triggered
    }

    private fun MotionDetector.restFlat(count: Int = 50) =
        feed(count) { Triple(noise(), noise(), g + noise()) }

    @Test
    fun `phone lying still never triggers at any sensitivity`() {
        Sensitivity.entries.forEach { sensitivity ->
            assertFalse(sensitivity.name, MotionDetector(sensitivity).restFlat(count = 500))
        }
    }

    @Test
    fun `slow pick-up is caught by the tilt check`() {
        val detector = MotionDetector(Sensitivity.MEDIUM)
        detector.restFlat()

        val triggered = detector.feed(100) { i ->
            val angle = (i / 100.0) * (PI / 4)
            Triple(noise(), (g * sin(angle)).toFloat(), (g * cos(angle)).toFloat())
        }

        assertTrue(triggered)
    }

    @Test
    fun `small tilt is ignored on low sensitivity`() {
        val detector = MotionDetector(Sensitivity.LOW)
        detector.restFlat()

        val triggered = detector.feed(100) { i ->
            val angle = (i / 100.0) * (PI / 18)
            Triple(noise(), (g * sin(angle)).toFloat(), (g * cos(angle)).toFloat())
        }

        assertFalse(triggered)
    }

    @Test
    fun `vigorous shake triggers even on low sensitivity`() {
        val detector = MotionDetector(Sensitivity.LOW)
        detector.restFlat()

        assertTrue(detector.feed(10) { i -> Triple(if (i % 2 == 0) 6f else -6f, 0f, g) })
    }

    @Test
    fun `light vibration does not trigger on medium sensitivity`() {
        val detector = MotionDetector(Sensitivity.MEDIUM)
        detector.restFlat()

        assertFalse(detector.feed(200) { i -> Triple(if (i % 2 == 0) 0.4f else -0.4f, 0f, g) })
    }

    @Test
    fun `a single spike is not enough`() {
        val detector = MotionDetector(Sensitivity.HIGH)
        detector.restFlat()

        assertFalse(detector.onSample(5f, 0f, g))
    }
}
