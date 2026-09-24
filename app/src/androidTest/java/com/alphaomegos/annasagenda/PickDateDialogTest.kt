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
 * Nothing here asserts which date comes back, on purpose. The picker speaks
 * milliseconds and reads them as UTC, while the callers hand it midnight in
 * the device's own zone — so what the dialog opens at, and what it returns
 * untouched, is exactly the question still open in the findings. Pinning
 * today's answer here would make it harder to fix rather than easier.
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
}
