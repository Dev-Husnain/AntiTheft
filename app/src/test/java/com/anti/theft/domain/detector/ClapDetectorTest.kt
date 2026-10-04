package com.anti.theft.domain.detector

import com.anti.theft.domain.models.Sensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.random.Random

class ClapDetectorTest {

    private val random = Random(7)

    /** One 20 ms frame at 16 kHz of white noise with the given peak amplitude (0..1). */
    private fun frame(amplitude: Float) = ShortArray(320) {
        ((random.nextFloat() * 2 - 1) * amplitude * 32767).toInt().toShort()
    }

    private fun ClapDetector.countClaps(frames: List<ShortArray>) = frames.count { onFrame(it) }

    private fun quiet(n: Int) = List(n) { frame(0.003f) }

    private fun clap(peak: Float = 0.8f) = listOf(frame(peak), frame(peak * 0.08f), frame(0.01f))

    @Test
    fun `background noise alone never triggers`() {
        assertEquals(0, ClapDetector(Sensitivity.HIGH).countClaps(quiet(500)))
    }

    @Test
    fun `a sharp clap in a quiet room triggers once`() {
        val frames = quiet(50) + clap() + quiet(50)

        assertEquals(1, ClapDetector(Sensitivity.MEDIUM).countClaps(frames))
    }

    @Test
    fun `sustained loud sound is not a clap`() {
        val frames = quiet(50) + List(40) { frame(0.6f) } + quiet(10)

        assertEquals(0, ClapDetector(Sensitivity.HIGH).countClaps(frames))
    }

    @Test
    fun `a clap after loud music is still caught once the room quietens`() {
        val frames = quiet(50) + List(40) { frame(0.6f) } + quiet(80) + clap() + quiet(20)

        assertEquals(1, ClapDetector(Sensitivity.MEDIUM).countClaps(frames))
    }

    @Test
    fun `a soft clap is ignored on low sensitivity`() {
        val frames = quiet(50) + clap(peak = 0.3f) + quiet(20)

        assertEquals(0, ClapDetector(Sensitivity.LOW).countClaps(frames))
    }

    @Test
    fun `the echo right after a clap is not a second clap`() {
        val frames = quiet(50) + clap() + clap(peak = 0.6f) + quiet(20)

        assertEquals(1, ClapDetector(Sensitivity.HIGH).countClaps(frames))
    }

    @Test
    fun `two claps a second apart both count`() {
        val frames = quiet(50) + clap() + quiet(50) + clap() + quiet(20)

        assertEquals(2, ClapDetector(Sensitivity.MEDIUM).countClaps(frames))
    }

    @Test
    fun `empty frame is ignored`() {
        assertFalse(ClapDetector(Sensitivity.MEDIUM).onFrame(ShortArray(0)))
    }
}
