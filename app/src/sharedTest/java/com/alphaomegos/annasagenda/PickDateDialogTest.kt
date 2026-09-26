package com.alphaomegos.annasagenda.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alphaomegos.annasagenda.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The one date picker, used by four screens.
 *
 * The date tests below are the ones that matter: the picker speaks UTC
 * milliseconds, and handing it midnight in the device's own zone used to move
 * the answer by a day. They pass in any time zone only because the conversion
 * is now UTC on both sides — run the suite on an emulator set to Moscow or to
 * Los Angeles and they say the same thing.
 */
@RunWith(AndroidJUnit4::class)
class PickDateDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cancelClosesWithoutPicking() {
        var picks = 0
        var dismissals = 0

        composeRule.setContent {
            MaterialTheme {
                PickDateDialog(
                    initialDate = LocalDate.of(2026, 3, 23),
                    onDismiss = { dismissals++ },
                    onPicked = { picks++ },
                )
            }
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.cancel))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(0, picks)
            assertEquals(1, dismissals)
        }
    }

    @Test
    fun confirmingPicksAndThenCloses() {
        val order = mutableListOf<String>()

        composeRule.setContent {
            MaterialTheme {
                PickDateDialog(
                    initialDate = LocalDate.of(2026, 3, 23),
                    onDismiss = { order += "dismiss" },
                    onPicked = { order += "pick" },
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.ok)).performClick()

        composeRule.runOnIdle {
            // Callers clear their own "show the picker" flag in onDismiss, so
            // it has to arrive after the date, not instead of it.
            assertEquals(listOf("pick", "dismiss"), order)
        }
    }

    @Test
    fun theExtraActionIsShownOnlyWhenTheCallerGivesOne() {
        composeRule.setContent {
            MaterialTheme {
                PickDateDialog(
                    initialDate = null,
                    onDismiss = {},
                    onPicked = {},
                )
            }
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.create_no_date))
            .assertDoesNotExist()
    }

    @Test
    fun theExtraActionSitsBesideCancelAndDoesItsOwnThing() {
        var noDateClicks = 0

        composeRule.setContent {
            MaterialTheme {
                PickDateDialog(
                    initialDate = null,
                    onDismiss = {},
                    onPicked = {},
                    extraDismissAction = {
                        TextButton(onClick = { noDateClicks++ }) {
                            Text(stringResource(R.string.create_no_date))
                        }
                    },
                )
            }
        }

        val activity = composeRule.activity

        composeRule.onNodeWithText(activity.getString(R.string.cancel)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.create_no_date)).performClick()

        composeRule.runOnIdle {
            assertEquals(1, noDateClicks)
        }
    }

    @Test
    fun theDialogOpensOnTheDateItWasGivenAndHandsItBackUntouched() {
        val asked = LocalDate.of(2026, 3, 23)
        var picked: LocalDate? = null

        composeRule.setContent {
            MaterialTheme {
                PickDateDialog(
                    initialDate = asked,
                    onDismiss = {},
                    onPicked = { picked = it },
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.ok)).performClick()

        composeRule.runOnIdle {
            // Confirming without touching the calendar has to return exactly
            // what the caller asked for. Before the conversion moved to UTC it
            // did not: midnight in the device's zone is a different day to the
            // picker, which reads its milliseconds as UTC.
            assertEquals(asked, picked)
        }
    }

    @Test
    fun aDateOnTheTurnOfTheYearIsStillItself() {
        // A day where being off by one hour is also off by one month, and by
        // one year.
        val asked = LocalDate.of(2026, 1, 1)
        var picked: LocalDate? = null

        composeRule.setContent {
            MaterialTheme {
                PickDateDialog(
                    initialDate = asked,
                    onDismiss = {},
                    onPicked = { picked = it },
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.ok)).performClick()

        composeRule.runOnIdle {
            assertEquals(asked, picked)
        }
    }
}
