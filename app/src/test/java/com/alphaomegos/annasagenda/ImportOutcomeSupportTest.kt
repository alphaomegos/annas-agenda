package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the app says after an import.
 *
 * Four different things can have happened and there used to be three answers,
 * because the one nobody had a word for — the state arrived, some pictures did
 * not — was folded into plain success. The covers were missing afterwards with
 * nothing connecting them to the restore.
 */
class ImportOutcomeSupportTest {

    private fun message(readable: Boolean, outcome: ImportOutcome) =
        importMessageFor(payloadWasReadable = readable, outcome = outcome)

    @Test
    fun everythingArrived() {
        assertEquals(
            ImportMessage.IMPORTED,
            message(readable = true, ImportOutcome(adopted = true)),
        )
    }

    /**
     * The library is still restored: the titles, the shelves, the pages read,
     * the years. Only the pictures are missing, and a picture can be chosen
     * again. Saying nothing would be the mistake.
     */
    @Test
    fun theStateArrivedButSomePicturesDidNot() {
        assertEquals(
            ImportMessage.IMPORTED_WITHOUT_SOME_COVERS,
            message(readable = true, ImportOutcome(adopted = true, coversNotWritten = 3)),
        )
    }

    /**
     * Two different failures, two different things for the user to do: try
     * another file, or accept that this file is not a backup of this app.
     */
    @Test
    fun aFileThatCouldNotBeReadIsNotTheSameAsAFileThatIsNotABackup() {
        assertEquals(
            ImportMessage.COULD_NOT_READ_FILE,
            message(readable = false, ImportOutcome.Failed),
        )
        assertEquals(
            ImportMessage.NOT_A_BACKUP,
            message(readable = true, ImportOutcome.Failed),
        )
    }

    /**
     * Covers written for a state that was then refused are not the user's
     * problem, and mentioning them would only obscure the real answer.
     */
    @Test
    fun coversAreNotMentionedWhenNothingWasImported() {
        assertEquals(
            ImportMessage.NOT_A_BACKUP,
            message(readable = true, ImportOutcome(adopted = false, coversNotWritten = 5)),
        )
        assertEquals(
            ImportMessage.COULD_NOT_READ_FILE,
            message(readable = false, ImportOutcome(adopted = false, coversNotWritten = 5)),
        )
    }

    @Test
    fun noCoversFailingIsPlainSuccess() {
        assertEquals(
            ImportMessage.IMPORTED,
            message(readable = true, ImportOutcome(adopted = true, coversNotWritten = 0)),
        )
    }

    @Test
    fun aFailedImportSaysSoByDefault() {
        assertEquals(false, ImportOutcome.Failed.adopted)
        assertEquals(0, ImportOutcome.Failed.coversNotWritten)
    }
}
