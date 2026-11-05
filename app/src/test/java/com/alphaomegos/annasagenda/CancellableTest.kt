package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.runCatchingCancellable
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** runCatching for coroutine code (0397): failures caught as before, a cancellation let through. */
class CancellableTest {
    @Test
    fun aFailureIsCaughtAndAValueKept() {
        assertEquals(7, runCatchingCancellable { 7 }.getOrNull())
        val failed = runCatchingCancellable { error("broken") }
        assertTrue(failed.isFailure && failed.exceptionOrNull()?.message == "broken")
    }

    @Test
    fun aCancellationGoesOnUp() {
        try {
            runCatchingCancellable { throw CancellationException("called off") }
            fail("the cancellation was caught")
        } catch (e: CancellationException) {
            assertEquals("called off", e.message)
        }
    }
}
