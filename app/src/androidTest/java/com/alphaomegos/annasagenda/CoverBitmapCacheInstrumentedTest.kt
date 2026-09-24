package com.alphaomegos.annasagenda

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alphaomegos.annasagenda.util.buildInternalCoverRef
import com.alphaomegos.annasagenda.util.deleteInternalCoverIfAny
import com.alphaomegos.annasagenda.util.loadCoverBitmapForUi
import com.alphaomegos.annasagenda.util.writeInternalCoverBytes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * A cover is decoded once and kept.
 *
 * Every row in the media list decodes its own cover, and a LazyColumn starts
 * that afresh each time a row comes back on screen — so scrolling up a list
 * the user had already looked at flashed the placeholder again while the file
 * was read and decoded a second time.
 */
@RunWith(AndroidJUnit4::class)
class CoverBitmapCacheInstrumentedTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
    }

    @Test
    fun theSameCoverAtTheSameSizeIsDecodedOnlyOnce() = runBlocking {
        val ref = storedCover(Color.RED)

        val first = loadCoverBitmapForUi(app, ref, targetMaxSidePx = 120)
        val second = loadCoverBitmapForUi(app, ref, targetMaxSidePx = 120)

        assertNotNull(first)
        assertSame("the second load must come from memory", first, second)

        deleteInternalCoverIfAny(app, ref)
    }

    /**
     * A list thumbnail and a details screen ask for very different sizes of
     * the same picture, and handing one the other's would either blur it or
     * waste the memory.
     */
    @Test
    fun theSameCoverAtADifferentSizeIsItsOwnEntry() = runBlocking {
        val ref = storedCover(Color.GREEN)

        val small = loadCoverBitmapForUi(app, ref, targetMaxSidePx = 120)
        val large = loadCoverBitmapForUi(app, ref, targetMaxSidePx = 480)

        assertNotNull(small)
        assertNotNull(large)
        assertNotSame(small, large)

        deleteInternalCoverIfAny(app, ref)
    }

    @Test
    fun aDeletedCoverIsForgotten() = runBlocking {
        val ref = storedCover(Color.BLUE)

        val before = loadCoverBitmapForUi(app, ref, targetMaxSidePx = 120)
        assertNotNull(before)

        deleteInternalCoverIfAny(app, ref)

        // The file is gone, so there is nothing to decode — and nothing must
        // be handed back from memory either.
        assertNull(loadCoverBitmapForUi(app, ref, targetMaxSidePx = 120))
    }

    private suspend fun storedCover(color: Int): String {
        val ref = buildInternalCoverRef(mediaKind = "book", itemId = System.nanoTime())

        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)

        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        bitmap.recycle()

        writeInternalCoverBytes(app, ref, bytes)
        return ref
    }
}
