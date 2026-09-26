package com.alphaomegos.annasagenda

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alphaomegos.annasagenda.util.buildInternalCoverRef
import com.alphaomegos.annasagenda.util.resolveStoredCoverFiles
import com.alphaomegos.annasagenda.util.writeInternalCoverBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Restoring from a state-only archive must not destroy cover images.
 *
 * importBackupPackage() used to delete every internal cover the archive did not
 * contain. The automatic backup carries no covers at all, so restoring from it
 * wiped the images for media the restored state still pointed at — the state
 * came back, the pictures did not.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportCoversInstrumentedTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        clearAppStateStoreFile()
    }

    @After
    fun tearDown() {
        clearAppStateStoreFile()
    }

    @Test
    fun importingAStateOnlyArchiveKeepsCoversTheRestoredStateStillReferences() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val bookId = 4242L
        val coverRef = buildInternalCoverRef(mediaKind = "book", itemId = bookId)
        val coverBytes = "book-cover-bytes".toByteArray(StandardCharsets.UTF_8)

        writeInternalCoverBytes(context = app, coverRef = coverRef, bytes = coverBytes)

        val stateWithCover = AppState(
            readingBooks = listOf(
                ReadingBook(
                    id = bookId,
                    title = "Книга с обложкой",
                    totalPages = 300,
                    coverUri = coverRef,
                )
            )
        )
        val json = AppStateStore(app).encodeToJson(stateWithCover)

        // Exactly what the automatic backup produces: state, no covers.
        val restored = vm.importBackupPackage(
            appStateJson = json,
            coverEntries = emptyMap(),
        )

        assertTrue("import should succeed", restored.adopted)
        assertEquals("no covers were offered, so none could fail", 0, restored.coversNotWritten)
        assertEquals(
            listOf(coverRef),
            vm.state.value.readingBooks.mapNotNull { it.coverUri },
        )

        val stillOnDisk = resolveStoredCoverFiles(app, vm.state.value).map { it.ref }
        assertEquals(
            "the cover file must survive a state-only restore",
            listOf(coverRef),
            stillOnDisk,
        )
    }

    /**
     * The orphan sweep is still expected to run: a cover the restored state no
     * longer mentions should go.
     */
    @Test
    fun importingDropsCoversTheRestoredStateNoLongerReferences() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val bookId = 5150L
        val coverRef = buildInternalCoverRef(mediaKind = "book", itemId = bookId)

        writeInternalCoverBytes(
            context = app,
            coverRef = coverRef,
            bytes = "gone-soon".toByteArray(StandardCharsets.UTF_8),
        )

        val withCover = AppState(
            readingBooks = listOf(
                ReadingBook(
                    id = bookId,
                    title = "Книга",
                    totalPages = 100,
                    coverUri = coverRef,
                )
            )
        )
        vm.importBackupPackage(
            appStateJson = AppStateStore(app).encodeToJson(withCover),
            coverEntries = emptyMap(),
        )

        // Now restore a state that knows nothing about that book.
        vm.importBackupPackage(
            appStateJson = AppStateStore(app).encodeToJson(AppState()),
            coverEntries = emptyMap(),
        )

        // The sweep runs in its own coroutine, so poll rather than guess.
        var swept = false
        for (attempt in 1..50) {
            if (resolveStoredCoverFiles(app, withCover).isEmpty()) {
                swept = true
                break
            }
            delay(20)
        }

        assertTrue("a cover nothing references any more should be swept", swept)
    }

    private fun clearAppStateStoreFile() {
        val file = File(app.filesDir, "datastore/app_state_store.preferences_pb")
        if (file.exists()) {
            file.delete()
        }
    }
}
