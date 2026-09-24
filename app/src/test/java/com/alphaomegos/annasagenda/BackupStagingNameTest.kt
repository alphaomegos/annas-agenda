package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.util.AUTO_BACKUP_FILE_NAME
import com.alphaomegos.annasagenda.util.MANUAL_BACKUP_FILE_NAME
import com.alphaomegos.annasagenda.util.stagingFileNameFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The archive is built under a staging name and only takes the real one once
 * it is whole, so the two must never collide — and the staging name has to
 * keep the .zip extension, which MediaStore checks against the media type it
 * was given.
 */
class BackupStagingNameTest {

    @Test
    fun theStagingNameKeepsTheZipExtension() {
        assertTrue(stagingFileNameFor(MANUAL_BACKUP_FILE_NAME).endsWith(".zip"))
        assertTrue(stagingFileNameFor(AUTO_BACKUP_FILE_NAME).endsWith(".zip"))
    }

    @Test
    fun theStagingNameIsNeverTheRealOne() {
        assertNotEquals(MANUAL_BACKUP_FILE_NAME, stagingFileNameFor(MANUAL_BACKUP_FILE_NAME))
        assertNotEquals(AUTO_BACKUP_FILE_NAME, stagingFileNameFor(AUTO_BACKUP_FILE_NAME))
    }

    @Test
    fun theTwoArchivesDoNotShareAStagingName() {
        assertNotEquals(
            stagingFileNameFor(MANUAL_BACKUP_FILE_NAME),
            stagingFileNameFor(AUTO_BACKUP_FILE_NAME),
        )
    }

    @Test
    fun aNameWithoutTheExtensionStillGetsOne() {
        assertEquals("plain.part.zip", stagingFileNameFor("plain"))
    }
}
