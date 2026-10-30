package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.SOMEDAY_SHADES_DARK
import com.alphaomegos.annasagenda.support.SOMEDAY_SHADES_LIGHT
import com.alphaomegos.annasagenda.support.firstThatFits
import com.alphaomegos.annasagenda.support.somedayContrast
import com.alphaomegos.annasagenda.support.somedayCountLabel
import com.alphaomegos.annasagenda.support.somedayInk
import com.alphaomegos.annasagenda.support.somedayLuminance
import com.alphaomegos.annasagenda.support.somedayShade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The month's «Someday» circle (0358): sixteen reds, a step per task, a number that reads. */
class SomedayShadesTest {

    private fun red(argb: Long) = (argb shr 16) and 0xFF
    private fun green(argb: Long) = (argb shr 8) and 0xFF
    private fun blue(argb: Long) = argb and 0xFF

    @Test
    fun sixteenShadesInEachTheme() {
        assertEquals(16, SOMEDAY_SHADES_LIGHT.size)
        assertEquals(16, SOMEDAY_SHADES_DARK.size)
        assertEquals(16, SOMEDAY_SHADES_LIGHT.toSet().size)
        assertEquals(16, SOMEDAY_SHADES_DARK.toSet().size)
    }

    @Test
    fun eachTaskIsOneStepRedder_andTheLastStepHolds() {
        listOf(false, true).forEach { dark ->
            val ramp = if (dark) SOMEDAY_SHADES_DARK else SOMEDAY_SHADES_LIGHT
            assertEquals(ramp.first(), somedayShade(0, dark))
            (0..15).forEach { n -> assertEquals(ramp[n], somedayShade(n, dark)) }
            assertEquals(ramp.last(), somedayShade(16, dark))
            assertEquals(ramp.last(), somedayShade(250, dark))
            assertEquals(ramp.first(), somedayShade(-1, dark))
        }
    }

    @Test
    fun everyShadeIsARed_andEachStepIsMoreSo() {
        listOf(SOMEDAY_SHADES_LIGHT, SOMEDAY_SHADES_DARK).forEach { ramp ->
            ramp.forEach { c -> assertTrue("not a red: ${c.toString(16)}", red(c) > green(c) && red(c) > blue(c)) }
            val redness = ramp.map { red(it) - maxOf(green(it), blue(it)) }
            assertTrue("redness does not grow: $redness", redness.zipWithNext().all { (a, b) -> b > a })
        }
    }

    @Test
    fun lightGoesFromPaleToBright_darkFromDimToBright() {
        val light = SOMEDAY_SHADES_LIGHT.map(::somedayLuminance)
        assertTrue("the light ramp does not darken: $light", light.zipWithNext().all { (a, b) -> b < a })
        assertTrue(light.first() > 0.8)
        val dark = SOMEDAY_SHADES_DARK.map(::somedayLuminance)
        assertTrue("the dark ramp does not brighten: $dark", dark.zipWithNext().all { (a, b) -> b > a })
        assertTrue(dark.first() < 0.05)
    }

    @Test
    fun theNumberReadsOnEveryShade() {
        (SOMEDAY_SHADES_LIGHT + SOMEDAY_SHADES_DARK).forEach { shade ->
            val ink = somedayInk(shade)
            assertTrue(ink == 0xFF000000 || ink == 0xFFFFFFFF)
            val contrast = somedayContrast(ink, shade)
            assertTrue("contrast ${"%.2f".format(java.util.Locale.ROOT, contrast)} on ${shade.toString(16)}", contrast >= 4.5)
        }
        assertEquals(0xFF000000, somedayInk(SOMEDAY_SHADES_LIGHT.first()))
        assertEquals(0xFFFFFFFF, somedayInk(SOMEDAY_SHADES_DARK.first()))
    }

    @Test
    fun theLabelIsTheCount_upToNinetyNine() {
        val plain: (Int) -> String = { it.toString() }
        assertEquals("0", somedayCountLabel(0, plain))
        assertEquals("7", somedayCountLabel(7, plain))
        assertEquals("99", somedayCountLabel(99, plain))
        assertEquals("99+", somedayCountLabel(100, plain))
        assertEquals("٩٩+", somedayCountLabel(1000) { n -> n.toString().map { '٠' + (it - '0') }.joinToString("") })
    }

    @Test
    fun theFirstTitleThatFits_elseTheLast() {
        val widths = mapOf("Сентябрь 2026 (big)" to 180, "Сентябрь 2026" to 120, "Сент. 2026" to 80)
        val choices = widths.keys.toList()
        assertEquals("Сентябрь 2026 (big)", firstThatFits(choices) { widths.getValue(it) <= 200 })
        assertEquals("Сентябрь 2026", firstThatFits(choices) { widths.getValue(it) <= 150 })
        assertEquals("Сент. 2026", firstThatFits(choices) { widths.getValue(it) <= 90 })
        assertEquals("Сент. 2026", firstThatFits(choices) { widths.getValue(it) <= 10 })
    }
}
