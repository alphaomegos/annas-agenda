package com.alphaomegos.annasagenda

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/**
 * The saved state, through a real DataStore file.
 *
 * This class lives in src/sharedTest, so it runs twice: on the JVM under
 * Robolectric, and on a device under the instrumentation runner. Nothing in it
 * is written for one of the two — no stub of DataStore, no pretending about
 * files — which is what makes running it in both places worth anything. If the
 * two ever disagree, the disagreement is the finding.
 *
 * It is also the first thing worth proving before moving anything else off the
 * emulator: everything that would follow — the view model, the backup, the
 * dialogs — stands on a Context and a DataStore.
 *
 * Each test gets its own file, built with the store's internal constructor
 * rather than taken from [appStateDataStore]. That is not tidiness: the app's
 * DataStore is one instance for the whole process, held in a variable that
 * outlives any single test, and it keeps what it has read in memory. Deleting
 * the file underneath it changes nothing — the next read still answers from
 * memory, and a test that asks "what does an empty store say?" is answered
 * with the previous test's data.
 *
 * The file goes under [Context.getCacheDir] rather than in a JUnit
 * TemporaryFolder, because the temporary directory a JVM hands out and the one
 * an app may write to on Android are not the same place.
 */
@RunWith(AndroidJUnit4::class)
class AppStateStoreContractTest {

    private lateinit var context: Context
    private lateinit var storeDir: File
    private lateinit var store: AppStateStore

    private val monday = LocalDate.of(2026, 3, 2)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppStateStoreCorruption.clear()

        storeDir = File(context.cacheDir, "store-contract-${UUID.randomUUID()}")
        storeDir.mkdirs()

        store = AppStateStore(
            context = context,
            dataStore = buildAppStateDataStore(
                context = context,
                file = File(storeDir, "app_state_store.preferences_pb"),
            ),
        )
    }

    @After
    fun tearDown() {
        storeDir.deleteRecursively()
        AppStateStoreCorruption.clear()
    }

    @Test
    fun nothingSavedReadsAsNothingSaved() = runBlocking {
        assertEquals(AppStateLoadResult.Empty, store.load())
    }

    @Test
    fun whatIsSavedIsWhatComesBack() = runBlocking {
        val saved = AppState(
            tasks = listOf(Task(id = 1L, date = monday, description = "dentist")),
            undoneHorizonDays = 90,
            themeMode = AppThemeMode.DARK,
            mainMenuOrder = listOf("calendar", "new_task"),
        )

        store.save(saved)
        val loaded = store.load()

        assertTrue("expected Loaded, got $loaded", loaded is AppStateLoadResult.Loaded)
        assertEquals(saved, (loaded as AppStateLoadResult.Loaded).state)
    }

    /**
     * The contract patch 0059 added, checked where it actually happens.
     *
     * Occurrences a rule can produce again are left out of the file, and
     * generating the range afterwards has to bring back the same days — not
     * merely the same number of them.
     */
    @Test
    fun theDaysLeftOutOfTheFileComeBackTheSame() = runBlocking {
        val today = LocalDate.now()
        val anchor = today.minusDays(1)
        val end = today.plusDays(20)

        val template = Task(
            id = 1L,
            date = anchor,
            description = "water the plants",
            repeatRule = RepeatRule(
                freq = RepeatFreq.DAILY,
                interval = 1,
                weekStart = DayOfWeek.MONDAY,
            ),
        )

        val materialised = generateRecurrencesInRange(
            tasks = listOf(template),
            subtasks = emptyList(),
            suppressedRecurrences = emptySet(),
            start = anchor,
            end = end,
            nextId = 100L,
            defaultWeekStart = DayOfWeek.MONDAY,
        )
        val before = AppState(tasks = materialised.tasks, subtasks = materialised.subtasks)

        store.save(before)
        val loaded = (store.load() as AppStateLoadResult.Loaded).state

        assertTrue(
            "nothing was left out: ${loaded.tasks.size} of ${before.tasks.size}",
            loaded.tasks.size < before.tasks.size,
        )

        val rebuilt = generateRecurrencesInRange(
            tasks = loaded.tasks,
            subtasks = loaded.subtasks,
            suppressedRecurrences = loaded.suppressedRecurrences,
            start = anchor,
            end = end,
            nextId = 10_000L,
            defaultWeekStart = DayOfWeek.MONDAY,
        )

        assertEquals(daysOf(before.tasks), daysOf(rebuilt.tasks))
    }

    /**
     * The reason the tests above hold their own file, said as a test rather
     * than only as a comment.
     *
     * One instance per process is what the app needs — DataStore refuses two
     * over one file — and it is exactly what makes it useless for isolating
     * one test from the next.
     */
    @Test
    fun theAppsOwnStoreIsOneInstanceForTheWholeProcess() {
        assertSame(appStateDataStore(context), appStateDataStore(context))
    }

    /** Every day, in the order the screens draw it, with ids left out. */
    private fun daysOf(tasks: List<Task>): Map<LocalDate, List<String>> =
        tasks
            .filter { it.date != null }
            .groupBy { it.date!! }
            .mapValues { (_, day) ->
                day.sortedWith(compareBy({ it.order }, { it.id }))
                    .map { "${it.order}:${it.description}:${it.isDone}:${it.originTaskId}" }
            }
}
