package com.anti.theft.domain.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketDetectorTest {

    private val g = 9.81f
    private val stepNanos = 20_000_000L
    private var clock = 0L

    private val upright = Triple(0f, g, 0f)
    private val faceDown = Triple(0f, 0f, -g)
    private val seated = Triple(0f, g * 0.6f, g * 0.8f)

    private fun PocketDetector.hold(seconds: Double, orientation: Triple<Float, Float, Float>): List<PocketPhase> =
        List((seconds * 50).toInt()) {
            clock += stepNanos
            onAcceleration(orientation.first, orientation.second, orientation.third, clock)
        }

    private fun PocketDetector.cover(covered: Boolean): PocketPhase = onProximity(covered, clock)

    private fun armedInPocket(): PocketDetector = PocketDetector().apply {
        hold(0.5, upright)
        cover(true)
        hold(3.5, upright)
    }

    @Test
    fun `waits while the sensor is uncovered`() {
        val detector = PocketDetector()
        detector.cover(false)

        assertTrue(detector.hold(10.0, upright).all { it == PocketPhase.WaitingForPocket })
    }

    @Test
    fun `arms after three seconds upright in a pocket`() {
        val detector = PocketDetector()
        detector.hold(0.5, upright)
        detector.cover(true)

        val phases = detector.hold(3.5, upright)

        assertTrue(phases.first() is PocketPhase.Arming)
        assertEquals(PocketPhase.Armed, phases.last())
        assertTrue(detector.inPocket)
    }

    @Test
    fun `face down on a desk is not a pocket`() {
        val detector = PocketDetector()
        detector.hold(0.5, faceDown)
        detector.cover(true)

        assertTrue(detector.hold(10.0, faceDown).all { it == PocketPhase.WaitingForPocket })
        assertFalse(detector.inPocket)
    }

    @Test
    fun `taking it out while arming starts over`() {
        val detector = PocketDetector()
        detector.hold(0.5, upright)
        detector.cover(true)
        detector.hold(2.0, upright)
        detector.cover(false)
        detector.hold(0.5, upright)
        detector.cover(true)

        val phases = detector.hold(2.0, upright)

        assertTrue(phases.last() is PocketPhase.Arming)
    }

    @Test
    fun `pulling the phone out triggers after one second`() {
        val detector = armedInPocket()
        detector.cover(false)

        val phases = detector.hold(1.2, upright)

        assertEquals(PocketPhase.Armed, phases.first())
        assertEquals(PocketPhase.Triggered, phases.last())
    }

    @Test
    fun `a brief uncover from fabric shifting is ignored`() {
        val detector = armedInPocket()
        detector.cover(false)
        detector.hold(0.4, upright)
        detector.cover(true)

        assertTrue(detector.hold(3.0, upright).all { it == PocketPhase.Armed })
    }

    @Test
    fun `sitting down with the phone in a pocket keeps it armed`() {
        val detector = armedInPocket()

        assertTrue(detector.hold(5.0, seated).all { it == PocketPhase.Armed })
    }

    @Test
    fun `stays triggered once triggered`() {
        val detector = armedInPocket()
        detector.cover(false)
        detector.hold(1.2, upright)
        detector.cover(true)

        assertEquals(PocketPhase.Triggered, detector.hold(0.1, upright).last())
    }
}
