package com.alphaomegos.annasagenda

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        clearAppStateStoreFile()
        store = AppStateStore(appContext)
    }

    @After
    fun tearDown() {
        clearAppStateStoreFile()
    }

    @Test
    fun load_returnsDefaultState_whenStoreFileDoesNotExist() = runBlocking {
        val loaded = store.load()

        assertEquals(AppState(), loaded)
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

        assertEquals(original, restored)
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

    private fun clearAppStateStoreFile() {
        val file = File(appContext.filesDir, "datastore/app_state_store.preferences_pb")
        if (file.exists()) {
            file.delete()
        }
    }
}