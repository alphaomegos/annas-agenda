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
 * The promise worth pinning is the one Eduard made the rule for every new
 * look (19.10): it comes on at once — after an update, from an old backup,
 * from a later version's payload — and the old one stays a choice away,
 * kept by name, so a later change of default never moves anybody who chose.
 */
class PaletteTest {

    @Test
    fun theNewLookComesOnAtOnce() {
        assertEquals(AppPalette.GREEN, AppState().palette)

        val withoutTheField = """{"v":$CURRENT_SCHEMA_VERSION,"tasks":[],"subtasks":[]}"""
        val restored = appStateStoreJson.decodeFromString<AppStateDto>(withoutTheField).toDomain()
        assertEquals(AppPalette.GREEN, restored.palette)
    }

    @Test
    fun anUnknownPaletteIsTheDefault() {
        assertEquals(AppPalette.GREEN, parseAppPalette("SEPIA"))
        assertEquals(AppPalette.GREEN, parseAppPalette(""))
        assertEquals(AppPalette.GREEN, parseAppPalette("classic"))
    }

    /** The old look, once chosen, is written down and read back as chosen. */
    @Test
    fun choosingTheOldLookSticks() {
        val json = appStateStoreJson.encodeToString(AppState(palette = AppPalette.CLASSIC).toDto())
        assertTrue(json, json.contains("\"palette\":\"CLASSIC\""))
        assertEquals(AppPalette.CLASSIC, appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain().palette)
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
