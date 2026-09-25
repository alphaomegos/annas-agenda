package com.alphaomegos.annasagenda

/**
 * How an import went, in more detail than "yes" or "no".
 *
 * The state and the pictures are not worth the same. A library whose covers
 * failed to write is still a restored library — the titles, the shelves, the
 * pages read, the years — and refusing the whole import over an image would
 * throw away the part that cannot be replaced in order to protect the part
 * that can.
 *
 * But the user has to be told, because the alternative is what this replaces:
 * the import reported success, and the missing pictures were discovered later,
 * with nothing to connect them to the restore.
 */
data class ImportOutcome(
    val adopted: Boolean,
    /** Cover files the archive carried and the disk would not take. */
    val coversNotWritten: Int = 0,
) {
    companion object {
        val Failed = ImportOutcome(adopted = false)
    }
}

/** What to say to the user after an import, as a string resource id. */
enum class ImportMessage {
    /** Everything arrived. */
    IMPORTED,

    /** The state arrived; some pictures did not. */
    IMPORTED_WITHOUT_SOME_COVERS,

    /** Nothing arrived, because the file could not be read at all. */
    COULD_NOT_READ_FILE,

    /** The file was read and was not a backup this app understands. */
    NOT_A_BACKUP,
}

/**
 * Which of the four things to say.
 *
 * [payloadWasReadable] is false when the file could not be opened or unpacked
 * — a wrong file, a truncated download — and true when it was read but its
 * contents were refused. The two are different problems and the user can act
 * on the difference: one means "try another file", the other means "this file
 * is not a backup of this app".
 */
fun importMessageFor(
    payloadWasReadable: Boolean,
    outcome: ImportOutcome,
): ImportMessage = when {
    !payloadWasReadable -> ImportMessage.COULD_NOT_READ_FILE
    !outcome.adopted -> ImportMessage.NOT_A_BACKUP
    outcome.coversNotWritten > 0 -> ImportMessage.IMPORTED_WITHOUT_SOME_COVERS
    else -> ImportMessage.IMPORTED
}
