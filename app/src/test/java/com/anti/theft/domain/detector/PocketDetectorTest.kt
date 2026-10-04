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

        assertEquals(PocketPhase.Triggered, detector.hold(1.6).last())
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
    fun `pulling the phone out triggers after one and a half seconds`() {
        val detector = armedWithWorkingProximity()
        detector.near(false)
        detector.light(300f)

        val phases = detector.hold(1.6)

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
        assertEquals(PocketPhase.Triggered, detector.hold(1.6).last())
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
        detector.hold(1.6)
        detector.near(true)
        detector.light(0f)

        assertEquals(PocketPhase.Triggered, detector.hold(0.1).last())
    }

    @Test
    fun `light flickering into the pocket while walking does not trigger`() {
        val detector = armedWithStuckProximity()

        val phases = (1..40).flatMap { step ->
            detector.light(if (step % 2 == 0) 160f else 2f)
            detector.hold(0.4)
        }

        assertTrue(phases.all { it == PocketPhase.Armed })
    }

    @Test
    fun `a dim leak at the pocket opening for a few seconds does not trigger`() {
        val detector = armedWithStuckProximity()

        detector.light(35f)

        assertTrue(detector.hold(4.0).all { it == PocketPhase.Armed })
    }

    @Test
    fun `a steady moderate leak raises the bar instead of triggering`() {
        val detector = armedWithStuckProximity()

        repeat(60) {
            detector.light(if (it % 2 == 0) 40f else 42f)
            detector.hold(0.2)
        }
        detector.light(120f)
        assertTrue(detector.hold(3.0).all { it == PocketPhase.Armed })

        detector.light(900f)
        assertEquals(PocketPhase.Triggered, detector.hold(1.6).last())
    }

    @Test
    fun `reset keeps sensor readings and starts arming again`() {
        val detector = armedWithStuckProximity()
        detector.light(300f)
        detector.hold(1.6)
        detector.light(0f)

        detector.reset()

        assertTrue(detector.hold(1.0).last() is PocketPhase.Arming)
        assertEquals(PocketPhase.Armed, detector.hold(2.5).last())
    }
}
