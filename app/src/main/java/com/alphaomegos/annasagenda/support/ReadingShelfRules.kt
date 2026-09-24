package com.alphaomegos.annasagenda

/**
 * The two years a media item can carry, and which of them a shelf allows.
 *
 * Books call the first one "read", films and series call it "watched", and
 * that naming difference is the only reason the rule was written out six
 * times — three times for creating an item, three for moving it, and three
 * more inside the edit functions, each time with the same shape and its own
 * chance of being wrong.
 *
 * The rule itself: an item finished has a year finished and no year
 * abandoned, an item abandoned has the opposite, and an item still planned or
 * in progress has neither. There is no state where both are set, and none
 * where one is set on a shelf that has no meaning for it.
 */
data class ShelfYears(
    val finished: Int?,
    val abandoned: Int?,
)

/** The years a newly created or newly moved item gets on [shelf]. */
fun yearsForShelf(shelf: ReadingShelf, currentYear: Int): ShelfYears = when (shelf) {
    ReadingShelf.DONE -> ShelfYears(finished = currentYear, abandoned = null)
    ReadingShelf.ABANDONED -> ShelfYears(finished = null, abandoned = currentYear)
    ReadingShelf.PLANS,
    ReadingShelf.NOW -> ShelfYears(finished = null, abandoned = null)
}

/**
 * The years an item keeps after an edit.
 *
 * Unlike [yearsForShelf] this does not restamp: a book finished in 2019 that
 * is edited today is still a book finished in 2019. A year is taken from the
 * edit if it gives one, otherwise from what the item already had, and only
 * when it has neither does it fall back to [currentYear]. Leaving a shelf that
 * had a year drops it, because the item is no longer a thing that happened.
 */
fun resolvedShelfYears(
    shelf: ReadingShelf,
    requested: ShelfYears,
    existing: ShelfYears,
    currentYear: Int,
): ShelfYears = when (shelf) {
    ReadingShelf.DONE -> ShelfYears(
        finished = requested.finished ?: existing.finished ?: currentYear,
        abandoned = null,
    )

    ReadingShelf.ABANDONED -> ShelfYears(
        finished = null,
        abandoned = requested.abandoned ?: existing.abandoned ?: currentYear,
    )

    ReadingShelf.PLANS,
    ReadingShelf.NOW -> ShelfYears(finished = null, abandoned = null)
}

/**
 * A title that cannot be emptied.
 *
 * An edit sending nothing, or only spaces, keeps the title the item has. The
 * alternative would be refusing the save, and the screens have never done
 * that — a media item without a title cannot be found again by any of the
 * three sort orders, so there is no useful state to move to.
 */
fun titleAfterEdit(requested: String?, existing: String): String =
    requested?.trim()?.takeIf { it.isNotEmpty() } ?: existing

/**
 * Which cover an edit leaves behind.
 *
 * Clearing wins over replacing, because the two arrive together whenever a
 * caller passes both and only one of them can be meant.
 */
fun coverAfterEdit(
    requested: String?,
    existing: String?,
    clearCover: Boolean,
): String? = when {
    clearCover -> null
    requested != null -> requested
    else -> existing
}

/** No year on either side: a newly chosen shelf, before anything is typed. */
val NoShelfYears = ShelfYears(finished = null, abandoned = null)

/**
 * What the single year field on a details screen shows for [shelf].
 *
 * The screens keep one text field for both years, because only one of them can
 * be set at a time — which shelf is chosen decides which year it means. On a
 * shelf with no year it shows nothing, and the field is hidden.
 *
 * An item on a finished shelf with no year recorded shows [currentYear] rather
 * than an empty field: it is the answer the user almost always wants, and an
 * empty field there would save as this year anyway.
 */
fun shelfYearText(shelf: ReadingShelf, years: ShelfYears, currentYear: Int): String =
    when (shelf) {
        ReadingShelf.DONE -> (years.finished ?: currentYear).toString()
        ReadingShelf.ABANDONED -> (years.abandoned ?: currentYear).toString()
        ReadingShelf.PLANS,
        ReadingShelf.NOW -> ""
    }

/**
 * The other direction: what the year field means once it is filled in.
 *
 * Unreadable text falls back to [currentYear] rather than refusing the save.
 * That is what the screens have always done, and it is defensible — the field
 * only appears on a shelf where a year is required, and the user has already
 * said the thing is finished.
 */
fun shelfYearsFromText(shelf: ReadingShelf, yearText: String, currentYear: Int): ShelfYears =
    when (shelf) {
        ReadingShelf.DONE -> ShelfYears(
            finished = yearText.toIntOrNull() ?: currentYear,
            abandoned = null,
        )

        ReadingShelf.ABANDONED -> ShelfYears(
            finished = null,
            abandoned = yearText.toIntOrNull() ?: currentYear,
        )

        ReadingShelf.PLANS,
        ReadingShelf.NOW -> NoShelfYears
    }
