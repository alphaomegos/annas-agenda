package com.alphaomegos.annasagenda

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colours the app names for itself, in both schemes.
 *
 * A second palette is the kind of thing that gets added by copying the first
 * and changing the two values somebody happened to be looking at. These tests
 * ask the questions that would catch that: is every colour actually different,
 * and does each one go the right way.
 */
class AppExtraColorsTest {

    private val light = extraColorsFor(dark = false)
    private val dark = extraColorsFor(dark = true)

    @Test
    fun theTwoSchemesAgreeOnNothing() {
        assertNotEquals(light.positive, dark.positive)
        assertNotEquals(light.gentleHighlight, dark.gentleHighlight)
        assertNotEquals(light.bonusHighlight, dark.bonusHighlight)
        assertNotEquals(light.dayMarker, dark.dayMarker)
        assertNotEquals(light.chartGrid, dark.chartGrid)
        assertNotEquals(light.chartLabel, dark.chartLabel)
    }

    @Test
    fun theSchemeIsPickedByTheFlagItIsGiven() {
        assertEquals(light, extraColorsFor(dark = false))
        assertEquals(dark, extraColorsFor(dark = true))
        assertNotEquals(extraColorsFor(dark = false), extraColorsFor(dark = true))
    }

    /**
     * Text and marks have to be lighter than the surface they sit on, and the
     * dark scheme's surface is the dark one. A dark green "you are on track"
     * on a dark background is the exact failure this is here to prevent.
     */
    @Test
    fun whatIsDrawnOnTopGetsLighterInTheDarkScheme() {
        assertTrue(
            "positive: ${light.positive.luminance()} -> ${dark.positive.luminance()}",
            dark.positive.luminance() > light.positive.luminance(),
        )
        assertTrue(dark.dayMarker.luminance() > 0.15f)
        assertTrue(dark.chartLabel.luminance() > light.chartLabel.luminance())
    }

    /**
     * A wash is a wash: the pale fills exist to say "this one", not to be a
     * block of colour. On a dark surface that means dark, not pale.
     */
    @Test
    fun theWashesGetDarkerInTheDarkScheme() {
        assertTrue(
            "gentle: ${light.gentleHighlight.luminance()} -> ${dark.gentleHighlight.luminance()}",
            dark.gentleHighlight.luminance() < light.gentleHighlight.luminance(),
        )
        assertTrue(dark.bonusHighlight.luminance() < light.bonusHighlight.luminance())
        assertTrue("a wash must stay quiet", dark.gentleHighlight.luminance() < 0.15f)
        assertTrue("a wash must stay quiet", dark.bonusHighlight.luminance() < 0.15f)
    }

    /** Selected and bonus mean different things, and must not look the same. */
    @Test
    fun theTwoWashesStayTellableApart() {
        assertNotEquals(light.gentleHighlight, light.bonusHighlight)
        assertNotEquals(dark.gentleHighlight, dark.bonusHighlight)
    }

    /** The grid is a hint, not a line: it must stay faint in both schemes. */
    @Test
    fun theChartGridStaysFaint() {
        assertTrue(light.chartGrid.alpha < 0.2f)
        assertTrue(dark.chartGrid.alpha < 0.2f)
    }
}
