package com.alphaomegos.annasagenda

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Both guards exist because the automatic backup overwrites itself: writing at
 * the wrong moment does not add a bad archive, it replaces the good one.
 *
 * They used to live as two bare `if (...) return` lines in MainActivity.onStop,
 * where nothing could reach them.
 */
class BackupPolicyTest {

    @Test
    fun aLoadedHealthyStateIsBackedUp() {
        assertTrue(shouldWriteAutoBackup(isLoaded = true, hasStorageFailure = false))
    }

    @Test
    fun anUnloadedStateIsNotBackedUp() {
        assertFalse(
            "the state is still empty before loading finishes",
            shouldWriteAutoBackup(isLoaded = false, hasStorageFailure = false),
        )
    }

    @Test
    fun aStorageFailureIsNotBackedUp() {
        assertFalse(
            "an unreadable startup must not overwrite the archive it would be restored from",
            shouldWriteAutoBackup(isLoaded = true, hasStorageFailure = true),
        )
    }

    @Test
    fun aStorageFailureWinsEvenIfLoadingNeverFinished() {
        assertFalse(shouldWriteAutoBackup(isLoaded = false, hasStorageFailure = true))
    }
}
