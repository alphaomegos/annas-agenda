package com.alphaomegos.annasagenda

/**
 * The word that identifies a kind of media in a cover file name.
 *
 * It ends up inside the ref — `internal://media_covers/book_17_9f3a1c02.jpg` —
 * and it was written out as a literal in six places, three for importing a
 * cover and three for migrating an old one. A typo in any of them would have
 * produced a file that nothing ever finds again.
 *
 * These three words are effectively on disk in every user's cover folder.
 * Changing one does not break the app — nothing parses a ref back into a kind
 * and an id — but it does mean covers imported before and after the change
 * live under different names, so there is no reason to change them.
 */
fun coverMediaKind(type: ReadingMediaType): String = when (type) {
    ReadingMediaType.BOOKS -> "book"
    ReadingMediaType.MOVIES -> "movie"
    ReadingMediaType.SERIES -> "series"
}
