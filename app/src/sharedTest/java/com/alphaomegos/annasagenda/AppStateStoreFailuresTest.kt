package com.alphaomegos.annasagenda

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import java.util.UUID

/**
 * What the store does when what it finds is not what it can read.
 *
 * Four different wrong things, and the app owes a different answer to each:
 * a payload from a newer build is intact and must be left alone; an
 * unreadable payload is the user's data and must be kept and copied aside;
 * an unparseable DataStore file must not kill the app on launch; and a
 * payload from before the schema existed must be migrated rather than
 * refused. Collapsing any two of those is how a year of somebody's data
 * disappears quietly.
 *
 * Moved here from src/androidTest, where it ran only when somebody started an
 * emulator. Nothing in it needed a device: a Context and a real file are all
 * it ever wanted, and Robolectric has both.
 *
 * Three of its tests went the other way — the empty store, the round trip and
 * the rebuilt days are asked better by AppStateStoreContractTest, which gives
 * every test its own file instead of sharing the app's one. The same is done
 * here, so these four no longer depend on the order they run in.
 */
@RunWith(AndroidJUnit4::class)
class AppStateStoreFailuresTest {

    private lateinit var context: Context
    private lateinit var storeDir: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: AppStateStore

    private val payloadKey = stringPreferencesKey(AppStateStore.APP_STATE_KEY_NAME)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppStateStoreCorruption.clear()

        storeDir = File(context.cacheDir, "store-failures-${UUID.randomUUID()}")
        storeDir.mkdirs()

        dataStore = buildAppStateDataStore(
            context = context,
            file = File(storeDir, "app_state_store.preferences_pb"),
        )
        store = AppStateStore(context, dataStore)
    }

    @After
    fun tearDown() {
        storeDir.deleteRecursively()
        File(context.filesDir, AppStateStore.QUARANTINE_DIR_NAME)
            .listFiles()
            ?.forEach { it.delete() }
        AppStateStoreCorruption.clear()
    }

    /**
     * A payload from a newer version is not damaged, so it must not be
     * reported as damaged — and above all it must still be on disk afterwards,
     * exactly as it was, for that newer version to read.
     */
    @Test
    fun aPayloadFromANewerBuildIsRefusedAndLeftWhereItIs() = runBlocking {
        val fromTheFuture = """{"v":${CURRENT_SCHEMA_VERSION + 1},"tasks":[],"somethingNew":42}"""

        dataStore.edit { prefs -> prefs[payloadKey] = fromTheFuture }

        val loaded = store.load()

        assertTrue("expected TooNew, got $loaded", loaded is AppStateLoadResult.TooNew)
        loaded as AppStateLoadResult.TooNew
        assertEquals(CURRENT_SCHEMA_VERSION + 1, loaded.payloadVersion)
        assertEquals(CURRENT_SCHEMA_VERSION, loaded.supportedVersion)

        assertEquals(
            "the payload must be left exactly as it was",
            fromTheFuture,
            dataStore.data.first()[payloadKey],
        )
    }

    @Test
    fun anUnreadablePayloadIsKeptAndCopiedAside() = runBlocking {
        val unreadable = """{"v":3,"tasks":[{"id":1,"order":0}"""

        dataStore.edit { prefs -> prefs[payloadKey] = unreadable }

        val loaded = store.load()

        assertTrue("expected Corrupted, got $loaded", loaded is AppStateLoadResult.Corrupted)

        // The unreadable payload is still on disk, untouched.
        assertEquals(unreadable, dataStore.data.first()[payloadKey])

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
    fun anUnparseableFileIsQuarantinedAndTheStoreWorksAgainAfterwards() = runBlocking {
        // Leading 0x00 is an invalid protobuf tag, so this can never parse.
        val garbage = byteArrayOf(0) + "definitely not a preferences file".toByteArray()

        val file = File(storeDir, "corrupt_probe.preferences_pb")
        file.writeBytes(garbage)

        AppStateStoreCorruption.clear()
        val probeStore = AppStateStore(context, buildAppStateDataStore(context, file))

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
    fun aPayloadFromBeforeTheSchemaExistedIsMigrated() {
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
}
