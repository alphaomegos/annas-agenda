package com.alphaomegos.annasagenda

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/**
 * The colours the app is drawn in (0289, 19.10).
 *
 * The one promise worth pinning is the one Eduard made the condition of any
 * new look: nobody gets it without choosing it. An update, an old backup, a
 * payload from a later version — each of them has to come out Classic.
 */
class PaletteTest {

    @Test
    fun nobodyIsMovedOffClassicWithoutChoosing() {
        assertEquals(AppPalette.CLASSIC, AppState().palette)

        val withoutTheField = """{"v":$CURRENT_SCHEMA_VERSION,"tasks":[],"subtasks":[]}"""
        val restored = appStateStoreJson.decodeFromString<AppStateDto>(withoutTheField).toDomain()
        assertEquals(AppPalette.CLASSIC, restored.palette)
    }

    @Test
    fun anUnknownPaletteIsClassic() {
        assertEquals(AppPalette.CLASSIC, parseAppPalette("SEPIA"))
        assertEquals(AppPalette.CLASSIC, parseAppPalette(""))
        assertEquals(AppPalette.CLASSIC, parseAppPalette("green"))
    }

    @Test
    fun theChoiceSurvivesTheStoreByName() {
        AppPalette.entries.forEach { palette ->
            val json = appStateStoreJson.encodeToString(AppState(palette = palette).toDto())
            val restored = appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain()
            assertEquals(palette, restored.palette)
        }
        val json = appStateStoreJson.encodeToString(AppState(palette = AppPalette.WALLPAPER).toDto())
        assertTrue(json, json.contains("\"palette\":\"WALLPAPER\""))
    }
}
