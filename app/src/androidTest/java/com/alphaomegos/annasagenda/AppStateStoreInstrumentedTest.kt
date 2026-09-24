package com.alphaomegos.annasagenda

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
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

    /**
     * A payload from a newer version is not damaged, so it must not be
     * reported as damaged — and above all it must still be on disk afterwards,
     * exactly as it was, for that newer version to read.
     */
    @Test
    fun load_reportsTooNew_andKeepsPayload_whenStoredJsonIsFromANewerVersion() = runBlocking {
        val fromTheFuture = """{"v":${CURRENT_SCHEMA_VERSION + 1},"tasks":[],"somethingNew":42}"""

        appStateDataStore(appContext).edit { prefs ->
            prefs[stringPreferencesKey(AppStateStore.APP_STATE_KEY_NAME)] = fromTheFuture
        }

        val loaded = store.load()

        assertTrue("expected TooNew, got $loaded", loaded is AppStateLoadResult.TooNew)
        loaded as AppStateLoadResult.TooNew
        assertEquals(CURRENT_SCHEMA_VERSION + 1, loaded.payloadVersion)
        assertEquals(CURRENT_SCHEMA_VERSION, loaded.supportedVersion)

        val stillStored = appStateDataStore(appContext).data.first()[
            stringPreferencesKey(AppStateStore.APP_STATE_KEY_NAME)
        ]
        assertEquals("the payload must be left exactly as it was", fromTheFuture, stillStored)
    }

    @Test
    fun load_reportsCorrupted_andKeepsPayload_whenStoredJsonIsUnreadable() = runBlocking {
        val unreadable = """{"v":3,"tasks":[{"id":1,"order":0}"""

        appStateDataStore(appContext).edit { prefs ->
            prefs[stringPreferencesKey(AppStateStore.APP_STATE_KEY_NAME)] = unreadable
        }

        val loaded = store.load()

        assertTrue("expected Corrupted, got $loaded", loaded is AppStateLoadResult.Corrupted)

        // The unreadable payload is still on disk, untouched.
        val stillStored = appStateDataStore(appContext).data.first()[
            stringPreferencesKey(AppStateStore.APP_STATE_KEY_NAME)
        ]
        assertEquals(unreadable, stillStored)

        // And a verbatim copy was quarantined for manual recovery.
        val quarantined = (loaded as AppStateLoadResult.Corrupted).quarantineFile
        assertNotNull(quarantined)
        assertEquals(unreadable, quarantined!!.readText())
    }

    /**
     * The layer below the JSON one: the Preferences file itself is unparseable.
     *
     * Before the corruption handler existed, DataStore threw CorruptionException
     * straight out of `data.first()`, the exception escaped the coroutine started
     * in AppViewModel's init, and the app died on launch with no way back in.
     */
    @Test
    fun load_reportsCorrupted_andQuarantinesFile_whenDataStoreFileIsUnparseable() = runBlocking {
        // Leading 0x00 is an invalid protobuf tag, so this can never parse.
        val garbage = byteArrayOf(0) + "definitely not a preferences file".toByteArray()

        val file = File(
            appContext.filesDir,
            "datastore/${PROBE_PREFIX}${System.currentTimeMillis()}.preferences_pb"
        )
        file.parentFile?.mkdirs()
        file.writeBytes(garbage)

        AppStateStoreCorruption.clear()
        val probeStore = AppStateStore(appContext, buildAppStateDataStore(appContext, file))

        val loaded = probeStore.load()

        assertTrue("expected Corrupted, got $loaded", loaded is AppStateLoadResult.Corrupted)

        // The bad bytes were copied aside before DataStore replaced the file.
        val quarantined = (loaded as AppStateLoadResult.Corrupted).quarantineFile
        assertNotNull("corrupt file was not quarantined", quarantined)
        assertArrayEquals(garbage, quarantined!!.readBytes())

        // And the store is usable again afterwards, so recovery can persist.
        val recovered = AppState(mainMenuOrder = listOf("calendar", "new_task"))
        probeStore.save(recovered)

        val reloaded = probeStore.load()
        assertTrue("expected Loaded, got $reloaded", reloaded is AppStateLoadResult.Loaded)
        assertEquals(recovered, (reloaded as AppStateLoadResult.Loaded).state)
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
        AppStateStoreCorruption.clear()
        appStateDataStore(appContext).edit { it.clear() }

        File(appContext.filesDir, AppStateStore.QUARANTINE_DIR_NAME)
            .listFiles()
            ?.forEach { it.delete() }

        // Probe files from the file-corruption test, which cannot delete its
        // own file inside the test body: a @Test method must return void, and
        // File.delete() as the last expression would make it return Boolean.
        File(appContext.filesDir, "datastore")
            .listFiles { f -> f.name.startsWith(PROBE_PREFIX) }
            ?.forEach { it.delete() }
        Unit
    }

    private companion object {
        const val PROBE_PREFIX = "corrupt_probe_"
    }
}
