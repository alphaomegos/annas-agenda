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

/** Back in the top bars (0291, 19.10): the arrow at once, the word a choice away. */
class BackStyleTest {

    @Test
    fun theArrowComesOnAtOnce() {
        assertEquals(BackStyle.ARROW, AppState().backStyle)
        val withoutTheField = """{"v":$CURRENT_SCHEMA_VERSION,"tasks":[],"subtasks":[]}"""
        assertEquals(BackStyle.ARROW, appStateStoreJson.decodeFromString<AppStateDto>(withoutTheField).toDomain().backStyle)
        assertEquals(BackStyle.ARROW, parseBackStyle("BOTH"))
    }

    @Test
    fun theWordOnceChosenSticks() {
        val json = appStateStoreJson.encodeToString(AppState(backStyle = BackStyle.WORD).toDto())
        assertTrue(json, json.contains("\"backStyle\":\"WORD\""))
        assertEquals(BackStyle.WORD, appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain().backStyle)
    }

    /** The main menu on a narrow screen (0314): the list unless «mini buttons» was chosen; kept by name. */
    @Test
    fun theNarrowMenuIsAListUnlessChosenOtherwise() {
        assertEquals(MainMenuNarrowStyle.LIST, AppState().mainMenuNarrow)
        assertEquals(MainMenuNarrowStyle.LIST, parseMainMenuNarrowStyle("CAROUSEL"))
        val json = appStateStoreJson.encodeToString(AppState(mainMenuNarrow = MainMenuNarrowStyle.ICONS).toDto())
        assertEquals(MainMenuNarrowStyle.ICONS, appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain().mainMenuNarrow)
    }

    /** The day screen (0320): «New» at once, «Summer 2025» a choice away; kept by name, an unknown name is new. */
    @Test
    fun theDayScreenIsNewUnlessSummer2025WasChosen() {
        assertEquals(DayScreenStyle.NEW, AppState().dayScreenStyle)
        assertEquals(DayScreenStyle.NEW, parseDayScreenStyle("WINTER_2027"))
        val json = appStateStoreJson.encodeToString(AppState(dayScreenStyle = DayScreenStyle.SUMMER_2025).toDto())
        assertEquals(DayScreenStyle.SUMMER_2025, appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain().dayScreenStyle)
    }
}
