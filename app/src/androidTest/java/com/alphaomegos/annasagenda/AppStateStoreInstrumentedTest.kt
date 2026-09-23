package com.alphaomegos.annasagenda

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AppStateStoreInstrumentedTest {

    private lateinit var appContext: Context
    private lateinit var store: AppStateStore

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        clearAppStateStore()
        store = AppStateStore(appContext)
    }

    @After
    fun tearDown() {
        clearAppStateStore()
    }

    @Test
    fun load_reportsEmpty_whenNothingWasEverSaved() = runBlocking {
        val loaded = store.load()

        assertEquals(AppStateLoadResult.Empty, loaded)
    }

    @Test
    fun load_reportsCorrupted_andKeepsPayload_whenStoredJsonIsUnreadable() = runBlocking {
        val unreadable = """{"v":3,"tasks":[{"id":1,"order":0}"""

        appContext.appStateDataStore.edit { prefs ->
            prefs[stringPreferencesKey(AppStateStore.APP_STATE_KEY_NAME)] = unreadable
        }

        val loaded = store.load()

        assertTrue("expected Corrupted, got $loaded", loaded is AppStateLoadResult.Corrupted)

        // The unreadable payload is still on disk, untouched.
        val stillStored = appContext.appStateDataStore.data.first()[
            stringPreferencesKey(AppStateStore.APP_STATE_KEY_NAME)
        ]
        assertEquals(unreadable, stillStored)

        // And a verbatim copy was quarantined for manual recovery.
        val quarantined = (loaded as AppStateLoadResult.Corrupted).quarantineFile
        assertNotNull(quarantined)
        assertEquals(unreadable, quarantined!!.readText())
    }

    @Test
    fun saveThenLoad_roundTripsImportantFieldsThroughRealDataStore() = runBlocking {
        val validFieldIds = allAnthropometryFieldIds().take(2).toSet()

        val original = AppState(
            suppressedRecurrences = setOf(
                "T:task-1:20432",
                "S:subtask-1:20433",
            ),
            anthropometryEnabledFieldIds = validFieldIds,
            runningPlanApproved = true,
            mainMenuOrder = listOf(
                "calendar",
                "new_task",
                "running",
                "reading",
            ),
            mainMenuHiddenIds = setOf(
                "someday",
                "counters",
            ),
        )

        store.save(original)
        val restored = store.load()

        assertTrue("expected Loaded, got $restored", restored is AppStateLoadResult.Loaded)
        assertEquals(original, (restored as AppStateLoadResult.Loaded).state)
    }

    @Test
    fun decodeFromJson_migratesLegacyVersion0Payload() {
        val raw = """
            {
              "dailyGoalKcal": 1800,
              "anthropometryEnabledFieldIds": ["bad", "unknown"]
            }
        """.trimIndent()

        val decoded = store.decodeFromJson(raw)

        assertNotNull(decoded)
        assertEquals(
            listOf(
                CalorieGoalChange(
                    date = LocalDate.now(),
                    kcal = 1800,
                )
            ),
            decoded!!.calorieGoalChanges,
        )
        assertEquals(
            defaultAnthropometryFieldIds(),
            decoded.anthropometryEnabledFieldIds,
        )
        assertTrue(decoded.runningPlanEntries.isEmpty())
    }

    /**
     * Clears through the DataStore API rather than deleting the backing file:
     * the file on disk is not the source of truth while an instance is alive,
     * so deleting it leaves the in-memory cache stale and makes the tests order
     * dependent.
     */
    private fun clearAppStateStore() = runBlocking {
        appContext.appStateDataStore.edit { it.clear() }

        File(appContext.filesDir, AppStateStore.QUARANTINE_DIR_NAME)
            .listFiles()
            ?.forEach { it.delete() }
        Unit
    }
}