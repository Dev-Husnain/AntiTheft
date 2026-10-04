package com.anti.theft.domain.detector

import com.anti.theft.domain.models.Sensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class ClapDetectorTest {

    private val rate = 16_000
    private val random = Random(7)

    private fun silence(ms: Int) = FloatArray(ms * rate / 1000) { (random.nextFloat() * 2 - 1) * 0.003f }

    private fun clap(amplitude: Float = 0.7f): FloatArray {
        val n = 80 * rate / 1000
        var low = 0f
        return FloatArray(n) { i ->
            val t = i / rate.toFloat()
            val envelope = min(1f, t / 0.0005f) * exp(-t / 0.012f)
            val white = random.nextFloat() * 2 - 1
            low += 0.55f * (white - low)
            (0.5f * white + 0.5f * low) * amplitude * envelope * 1.6f
        }
    }

    private fun syllable(amplitude: Float, f0: Double, ms: Int, riseMs: Int): FloatArray {
        val n = ms * rate / 1000
        return FloatArray(n) { i ->
            val t = i / rate.toDouble()
            val attack = min(1.0, t * 1000 / riseMs)
            val release = min(1.0, (n - i) / (0.02 * rate))
            var s = 0.0
            for (k in 1..8) if (k * f0 < 1_500) s += sin(2 * PI * k * f0 * t) / k
            (s / 2.2 * amplitude * attack * release).toFloat()
        }
    }

    private fun thud(amplitude: Float = 0.9f): FloatArray {
        val n = 120 * rate / 1000
        return FloatArray(n) { i ->
            val t = i / rate.toDouble()
            val envelope = min(1.0, t / 0.001) * exp(-t / 0.025)
            ((0.7 * sin(2 * PI * 70 * t) + 0.3 * sin(2 * PI * 180 * t)) * envelope * amplitude).toFloat()
        }
    }

    private fun plosiveSyllable(): FloatArray {
        val burst = FloatArray(5 * rate / 1000) { (random.nextFloat() * 2 - 1) * 0.35f }
        return burst + syllable(0.85f, 180.0, 180, riseMs = 3)
    }

    private fun music(ms: Int) = FloatArray(ms * rate / 1000) { i ->
        val t = i / rate.toDouble()
        ((sin(2 * PI * 262 * t) + sin(2 * PI * 330 * t) + sin(2 * PI * 392 * t)) / 3 * 0.6).toFloat()
    }

    private fun frames(vararg parts: FloatArray): List<ShortArray> {
        val all = parts.reduce { acc, p -> acc + p }
        return all.toList().chunked(320).map { chunk ->
            ShortArray(chunk.size) { (chunk[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
        }
    }

    private fun claps(sensitivity: Sensitivity, vararg parts: FloatArray): Int {
        val detector = ClapDetector(sensitivity)
        return frames(*parts).count { detector.onFrame(it) }
    }

    @Test
    fun `a clap in a quiet room triggers once`() {
        assertEquals(1, claps(Sensitivity.MEDIUM, silence(500), clap(), silence(500)))
    }

    @Test
    fun `two claps a second apart both count`() {
        assertEquals(2, claps(Sensitivity.MEDIUM, silence(500), clap(), silence(1000), clap(), silence(300)))
    }

    @Test
    fun `the echo right after a clap is not a second clap`() {
        assertEquals(1, claps(Sensitivity.HIGH, silence(500), clap(), silence(60), clap(0.5f), silence(300)))
    }

    @Test
    fun `a clap after loud music is caught once the room quietens`() {
        assertEquals(1, claps(Sensitivity.MEDIUM, silence(300), music(1500), silence(1500), clap(), silence(300)))
    }

    @Test
    fun `soft clap counts on high sensitivity but not on low`() {
        assertEquals(1, claps(Sensitivity.HIGH, silence(500), clap(0.25f), silence(300)))
        assertEquals(0, claps(Sensitivity.LOW, silence(500), clap(0.25f), silence(300)))
    }

    @Test
    fun `background noise alone never triggers`() {
        assertEquals(0, claps(Sensitivity.HIGH, silence(10_000)))
    }

    @Test
    fun `loud speech does not trigger`() {
        val sentence = arrayOf(
            silence(400),
            syllable(0.9f, 140.0, 160, riseMs = 18), silence(60),
            syllable(0.8f, 160.0, 120, riseMs = 12), silence(40),
            syllable(0.95f, 130.0, 220, riseMs = 25), silence(400),
        )
        assertEquals(0, claps(Sensitivity.HIGH, *sentence))
    }

    @Test
    fun `a short shout does not trigger`() {
        assertEquals(0, claps(Sensitivity.HIGH, silence(400), syllable(0.95f, 220.0, 90, riseMs = 10), silence(400)))
    }

    @Test
    fun `a plosive syllable does not trigger`() {
        assertEquals(0, claps(Sensitivity.HIGH, silence(400), plosiveSyllable(), silence(400)))
    }

    @Test
    fun `a phone dropped on a mattress does not trigger`() {
        assertEquals(0, claps(Sensitivity.HIGH, silence(400), thud(), silence(400)))
    }

    @Test
    fun `sustained music does not trigger`() {
        assertEquals(0, claps(Sensitivity.HIGH, silence(300), music(3000), silence(300)))
    }

    @Test
    fun `empty frame is ignored`() {
        assertFalse(ClapDetector(Sensitivity.MEDIUM).onFrame(ShortArray(0)))
    }
}
