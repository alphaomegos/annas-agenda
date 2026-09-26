package com.alphaomegos.annasagenda.screens.media

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.ReadingMediaType
import com.alphaomegos.annasagenda.ReadingShelf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One details screen for three kinds of media.
 *
 * There were three of these, 702 of whose 764 lines were the same screen
 * written out again, and not one of them had a test. What is asserted here is
 * what the three used to have to agree on by hand: which words belong to which
 * kind, when the year field is there at all, and what Save and Done do when the
 * screen says the item is not valid.
 *
 * The questions are asked as existence, not as visibility. Everything below
 * the screen title lives in a scrolling column, so whether a field happens to
 * be above the fold depends on how tall the screen is — and these used to say
 * `assertIsDisplayed` only because the emulator's screen was tall enough to
 * hide the difference. "Is this the word this kind of media uses" is a
 * question about the form, not about the phone; the negative half of each pair
 * was already asking it that way. Done is clicked the way a person would reach
 * it, by scrolling to it first.
 */
@RunWith(AndroidJUnit4::class)
class MediaDetailsFormTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun eachKindOfMediaBringsItsOwnWords() {
        composeRule.setContent {
            MaterialTheme {
                Form(type = ReadingMediaType.MOVIES, shelf = ReadingShelf.PLANS)
            }
        }

        val activity = composeRule.activity

        composeRule.onNodeWithText(activity.getString(R.string.reading_movie_title))
            .assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.reading_movie_done))
            .assertExists()
    }

    @Test
    fun theYearFieldIsOnlyThereOnAShelfThatHasAYear() {
        composeRule.setContent {
            MaterialTheme {
                Form(type = ReadingMediaType.BOOKS, shelf = ReadingShelf.NOW)
            }
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.reading_book_field_year_read))
            .assertDoesNotExist()
    }

    @Test
    fun aFinishedItemIsAskedWhenItWasFinished() {
        composeRule.setContent {
            MaterialTheme {
                Form(type = ReadingMediaType.BOOKS, shelf = ReadingShelf.DONE)
            }
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.reading_book_field_year_read))
            .assertExists()
    }

    @Test
    fun anAbandonedItemIsAskedWhenItWasAbandoned() {
        composeRule.setContent {
            MaterialTheme {
                Form(type = ReadingMediaType.SERIES, shelf = ReadingShelf.ABANDONED)
            }
        }

        val activity = composeRule.activity

        composeRule
            .onNodeWithText(activity.getString(R.string.reading_media_field_year_abandoned))
            .assertExists()
        composeRule
            .onNodeWithText(activity.getString(R.string.reading_series_field_year_watched))
            .assertDoesNotExist()
    }

    @Test
    fun doneClosesTheScreenOnlyWhenTheItemIsValid() {
        var saves = 0
        var backs = 0

        composeRule.setContent {
            MaterialTheme {
                Form(
                    type = ReadingMediaType.BOOKS,
                    shelf = ReadingShelf.PLANS,
                    onSave = { saves++; false },
                    onBack = { backs++ },
                )
            }
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.reading_book_done))
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, saves)
            assertEquals("an invalid item must not leave the screen", 0, backs)
        }
    }

    @Test
    fun savingFromTheBarDoesNotCloseTheScreenEvenWhenItWorks() {
        var backs = 0

        composeRule.setContent {
            MaterialTheme {
                Form(
                    type = ReadingMediaType.BOOKS,
                    shelf = ReadingShelf.PLANS,
                    onSave = { true },
                    onBack = { backs++ },
                )
            }
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.save))
            .performClick()

        composeRule.runOnIdle {
            // Save is for carrying on editing; Done is the one that leaves.
            assertEquals(0, backs)
        }
    }

    @Composable
    private fun Form(
        type: ReadingMediaType,
        shelf: ReadingShelf,
        onSave: () -> Boolean = { true },
        onBack: () -> Unit = {},
    ) {
        MediaDetailsForm(
            type = type,
            coverUri = null,
            onPickCover = {},
            onRemoveCover = {},
            onDelete = {},
            onSave = onSave,
            onBack = onBack,
            title = "Dune",
            onTitleChange = {},
            shelf = shelf,
            onShelfChange = {},
            yearText = "2019",
            onYearTextChange = {},
            extraFields = {},
        )
    }
}
