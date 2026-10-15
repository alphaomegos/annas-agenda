package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.MetroStyle
import com.alphaomegos.annasagenda.support.metroEraColor

/** Line colours from the era of a style (15.10). */
class MetroEraColorsTest {

    // Moscow's red, green, ring brown and grey line.
    private val sokolnicheskaya = 0xFFE42313
    private val zamoskvoretskaya = 0xFF4BAF4F
    private val ring = 0xFF915133
    private val serpukhovskaya = 0xFFADACAC

    @Test
    fun todaysStyle_keepsEveryColour() {
        listOf(sokolnicheskaya, zamoskvoretskaya, ring).forEach { assertEquals(it, metroEraColor(MetroStyle.MODERN, it)) }
    }

    @Test
    fun windows_takesTheNearestOfItsSixteen_neverWhiteOrSilver() {
        assertEquals(0xFFFF0000, metroEraColor(MetroStyle.WIN95, sokolnicheskaya))
        assertEquals(0xFF808000, metroEraColor(MetroStyle.WIN95, ring))
        // Grey goes to grey, not to the silver of the windows.
        assertEquals(0xFF808080, metroEraColor(MetroStyle.WIN95, serpukhovskaya))
        assertTrue(metroEraColor(MetroStyle.WIN95, 0xFFFFFFFF) != 0xFFFFFFFF)
    }

    @Test
    fun dos_takesTheNearestOfCga_neverThePanelsBlue() {
        assertEquals(0xFFAA5500, metroEraColor(MetroStyle.DOS, ring))
        assertTrue(metroEraColor(MetroStyle.DOS, 0xFF0000AA) != 0xFF0000AA)
    }

    @Test
    fun theTerminal_isGreen_brighterForABrighterLine() {
        val dark = metroEraColor(MetroStyle.TERMINAL, 0xFF000080)
        val light = metroEraColor(MetroStyle.TERMINAL, 0xFFFFFF00)
        assertTrue(dark != light)
        listOf(dark, light).forEach { assertTrue((it shr 8 and 0xFF) > (it shr 16 and 0xFF)) }
    }
}
