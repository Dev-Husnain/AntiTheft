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

    private fun PocketDetector.hold(seconds: Double, orientation: Triple<Float, Float, Float> = upright): List<PocketPhase> =
        List((seconds * 50).toInt()) {
            clock += stepNanos
            onAcceleration(orientation.first, orientation.second, orientation.third, clock)
        }

    private fun PocketDetector.near(near: Boolean) = onProximity(near, clock)

    private fun PocketDetector.light(lux: Float) = onLight(lux, clock)

    private fun inRoom() = PocketDetector().apply {
        near(false)
        light(250f)
        hold(0.5)
    }

    private fun armedWithWorkingProximity() = inRoom().apply {
        near(true)
        light(0f)
        hold(3.5)
    }

    private fun armedWithStuckProximity() = PocketDetector().apply {
        near(false)
        light(250f)
        hold(0.5)
        light(1f)
        hold(3.5)
    }

    @Test
    fun `waits while out in the open`() {
        assertTrue(inRoom().hold(10.0).all { it == PocketPhase.WaitingForPocket })
    }

    @Test
    fun `arms after three seconds in a pocket`() {
        val detector = inRoom()
        detector.near(true)
        detector.light(0f)

        val phases = detector.hold(3.5)

        assertTrue(phases.first() is PocketPhase.Arming)
        assertEquals(PocketPhase.Armed, phases.last())
        assertTrue(detector.inPocket)
    }

    @Test
    fun `works on light alone when the proximity sensor never reports near`() {
        val detector = armedWithStuckProximity()
        assertEquals(PocketPhase.Armed, detector.hold(0.1).last())

        detector.light(180f)

        assertEquals(PocketPhase.Triggered, detector.hold(1.2).last())
    }

    @Test
    fun `a proximity sensor stuck at far never triggers by itself`() {
        val detector = armedWithStuckProximity()

        detector.near(false)

        assertTrue(detector.hold(5.0).all { it == PocketPhase.Armed })
    }

    @Test
    fun `face down on a desk is not a pocket`() {
        val detector = PocketDetector()
        detector.hold(0.5, faceDown)
        detector.near(true)
        detector.light(0f)

        assertTrue(detector.hold(10.0, faceDown).all { it == PocketPhase.WaitingForPocket })
        assertFalse(detector.inPocket)
    }

    @Test
    fun `taking it out while arming starts over`() {
        val detector = inRoom()
        detector.near(true)
        detector.light(0f)
        detector.hold(2.0)
        detector.near(false)
        detector.light(250f)
        detector.hold(0.5)
        detector.near(true)
        detector.light(0f)

        assertTrue(detector.hold(2.0).last() is PocketPhase.Arming)
    }

    @Test
    fun `pulling the phone out triggers after one second`() {
        val detector = armedWithWorkingProximity()
        detector.near(false)
        detector.light(300f)

        val phases = detector.hold(1.2)

        assertEquals(PocketPhase.Armed, phases.first())
        assertEquals(PocketPhase.Triggered, phases.last())
    }

    @Test
    fun `a brief uncover from fabric shifting is ignored`() {
        val detector = armedWithWorkingProximity()
        detector.near(false)
        detector.light(60f)
        detector.hold(0.4)
        detector.near(true)
        detector.light(0f)

        assertTrue(detector.hold(3.0).all { it == PocketPhase.Armed })
    }

    @Test
    fun `sunlight through thin fabric does not count as taken out`() {
        val detector = inRoom()
        detector.near(true)
        detector.light(12f)
        detector.hold(3.5)

        detector.light(45f)
        assertTrue(detector.hold(3.0).all { it == PocketPhase.Armed })

        detector.near(false)
        detector.light(800f)
        assertEquals(PocketPhase.Triggered, detector.hold(1.2).last())
    }

    @Test
    fun `sitting down with the phone in a pocket keeps it armed`() {
        assertTrue(armedWithWorkingProximity().hold(5.0, seated).all { it == PocketPhase.Armed })
    }

    @Test
    fun `stays triggered once triggered`() {
        val detector = armedWithWorkingProximity()
        detector.near(false)
        detector.light(300f)
        detector.hold(1.2)
        detector.near(true)
        detector.light(0f)

        assertEquals(PocketPhase.Triggered, detector.hold(0.1).last())
    }
}
