package com.alphaomegos.annasagenda

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colour scheme the app draws itself in.
 *
 * All of this is one `when` and one `valueOf`, which is exactly why it is
 * worth pinning: the decision lived inside a @Composable that defaulted to
 * light and that nothing could reach, and it stayed wrong for the life of the
 * app without a single test noticing.
 */
class ThemeModeTest {

    @Test
    fun followingThePhoneMeansWhateverThePhoneIsDoing() {
        assertTrue(isDarkTheme(AppThemeMode.SYSTEM, systemInDarkTheme = true))
        assertFalse(isDarkTheme(AppThemeMode.SYSTEM, systemInDarkTheme = false))
    }

    @Test
    fun choosingOverrulesThePhoneInBothDirections() {
        assertFalse(isDarkTheme(AppThemeMode.LIGHT, systemInDarkTheme = true))
        assertTrue(isDarkTheme(AppThemeMode.DARK, systemInDarkTheme = false))
    }

    @Test
    fun everyModeSurvivesBeingWrittenDownAndReadBack() {
        AppThemeMode.entries.forEach { mode ->
            assertEquals(mode, parseAppThemeMode(mode.name))
        }
    }

    /**
     * A payload from a later version can name a mode this one has never heard
     * of. Following the phone is what the app did before anyone could choose,
     * so it is the right thing to fall back to — and above all it must not
     * throw on the way in, which happens before the first frame is drawn.
     */
    @Test
    fun anUnknownModeFallsBackToFollowingThePhone() {
        assertEquals(AppThemeMode.SYSTEM, parseAppThemeMode("SEPIA"))
        assertEquals(AppThemeMode.SYSTEM, parseAppThemeMode(""))
        assertEquals(AppThemeMode.SYSTEM, parseAppThemeMode("dark"))
    }

    @Test
    fun theChoiceSurvivesTheStore() {
        AppThemeMode.entries.forEach { mode ->
            val json = appStateStoreJson.encodeToString(AppState(themeMode = mode).toDto())
            val restored = appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain()

            assertEquals(mode, restored.themeMode)
        }
    }

    /**
     * Additive with a default, so a payload written before the setting existed
     * has to decode without a migration — and to the same thing the app did
     * back then.
     */
    @Test
    fun aPayloadFromBeforeTheSettingExistedFollowsThePhone() {
        val withoutTheField = """{"v":$CURRENT_SCHEMA_VERSION,"tasks":[],"subtasks":[]}"""

        val restored = appStateStoreJson
            .decodeFromString<AppStateDto>(withoutTheField)
            .toDomain()

        assertEquals(AppThemeMode.SYSTEM, restored.themeMode)
    }

    /** Stored by name, so that reordering the enum cannot change old payloads. */
    @Test
    fun theModeIsStoredByNameNotByNumber() {
        val json = appStateStoreJson.encodeToString(AppState(themeMode = AppThemeMode.DARK).toDto())

        assertTrue(json, json.contains("\"themeMode\":\"DARK\""))
    }
}
