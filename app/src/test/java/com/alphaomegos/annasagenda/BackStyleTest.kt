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
}
