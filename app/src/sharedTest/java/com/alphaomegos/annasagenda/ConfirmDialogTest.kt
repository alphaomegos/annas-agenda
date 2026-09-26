package com.alphaomegos.annasagenda.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
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

/**
 * The one "are you sure?" dialog, standing in for six hand-written copies.
 *
 * Five of the six guard something irreversible — wiping all data, deleting a
 * repeating series, deleting a book — so the thing worth asserting is that
 * confirming and cancelling do not get crossed, and that cancelling does
 * nothing but close.
 */
@RunWith(AndroidJUnit4::class)
class ConfirmDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun itShowsTheTitleTheTextAndBothButtons() {
        composeRule.setContent {
            MaterialTheme {
                ConfirmDialog(
                    titleRes = R.string.reset_title,
                    textRes = R.string.reset_text,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        val activity = composeRule.activity

        composeRule.onNodeWithText(activity.getString(R.string.reset_title)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.reset_text)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.ok)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.cancel)).assertIsDisplayed()
    }

    @Test
    fun confirmingCallsOnlyOnConfirm() {
        var confirms = 0
        var dismissals = 0

        composeRule.setContent {
            MaterialTheme {
                ConfirmDialog(
                    titleRes = R.string.reset_title,
                    textRes = R.string.reset_text,
                    onConfirm = { confirms++ },
                    onDismiss = { dismissals++ },
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.ok)).performClick()

        composeRule.runOnIdle {
            assertEquals(1, confirms)
            // The caller closes the dialog itself, usually in the same handler
            // that does the work — this must not also fire the cancel path.
            assertEquals(0, dismissals)
        }
    }

    @Test
    fun cancellingCallsOnlyOnDismiss() {
        var confirms = 0
        var dismissals = 0

        composeRule.setContent {
            MaterialTheme {
                ConfirmDialog(
                    titleRes = R.string.reset_title,
                    textRes = R.string.reset_text,
                    onConfirm = { confirms++ },
                    onDismiss = { dismissals++ },
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.cancel)).performClick()

        composeRule.runOnIdle {
            assertEquals("cancelling must never do the thing", 0, confirms)
            assertEquals(1, dismissals)
        }
    }

    @Test
    fun theConfirmLabelIsWhicheverTheCallerAsksFor() {
        composeRule.setContent {
            MaterialTheme {
                ConfirmDialog(
                    titleRes = R.string.delete_repeating_task_title,
                    textRes = R.string.delete_repeating_task_text,
                    confirmLabelRes = R.string.remove,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        val activity = composeRule.activity

        composeRule.onNodeWithText(activity.getString(R.string.remove)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.ok)).assertDoesNotExist()
    }
}
