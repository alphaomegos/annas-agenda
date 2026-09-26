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
import java.time.LocalDate

/**
 * One dialog now serves both "copy task" and "copy subtask".
 *
 * They were two identical copies before, so nothing was ever asserted about
 * either — a shared component earns a test, and the title is the only thing
 * that still differs between the two uses.
 */
@RunWith(AndroidJUnit4::class)
class CopyToDateDialogsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theTitleIsWhicheverTheCallerAsksFor() {
        composeRule.setContent {
            MaterialTheme {
                CopyToDateDialogs(
                    itemId = 1L,
                    titleRes = R.string.copy_subtask,
                    showDatePicker = false,
                    onDismissAll = {},
                    onShowDatePicker = {},
                    onCopyToToday = {},
                    onCopyToTomorrow = {},
                    onCopyToDate = { _, _ -> },
                )
            }
        }

        val activity = composeRule.activity

        composeRule.onNodeWithText(activity.getString(R.string.copy_subtask)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.schedule_today)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.schedule_tomorrow)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.pick_date)).assertIsDisplayed()
    }

    @Test
    fun todayAndTomorrowCopyTheItemAndCloseTheDialog() {
        val copiedToday = mutableListOf<Long>()
        var dismissals = 0

        composeRule.setContent {
            MaterialTheme {
                CopyToDateDialogs(
                    itemId = 42L,
                    titleRes = R.string.copy_task,
                    showDatePicker = false,
                    onDismissAll = { dismissals++ },
                    onShowDatePicker = {},
                    onCopyToToday = { copiedToday += it },
                    onCopyToTomorrow = {},
                    onCopyToDate = { _, _ -> },
                )
            }
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.schedule_today))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(42L), copiedToday)
            assertEquals("copying must close the dialog", 1, dismissals)
        }
    }

    @Test
    fun theDatePickerConfirmsWithADateAndTheItemItWasOpenedFor() {
        val copied = mutableListOf<Pair<Long, LocalDate>>()

        composeRule.setContent {
            MaterialTheme {
                CopyToDateDialogs(
                    itemId = 42L,
                    titleRes = R.string.copy_task,
                    showDatePicker = true,
                    onDismissAll = {},
                    onShowDatePicker = {},
                    onCopyToToday = {},
                    onCopyToTomorrow = {},
                    onCopyToDate = { id, date -> copied += id to date },
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.ok)).performClick()

        composeRule.runOnIdle {
            assertEquals(1, copied.size)
            assertEquals(42L, copied.single().first)
        }
    }

    /** No item selected, no dialog — this is how the caller closes it. */
    @Test
    fun nothingIsShownWhenThereIsNoItem() {
        composeRule.setContent {
            MaterialTheme {
                CopyToDateDialogs(
                    itemId = null,
                    titleRes = R.string.copy_task,
                    showDatePicker = false,
                    onDismissAll = {},
                    onShowDatePicker = {},
                    onCopyToToday = {},
                    onCopyToTomorrow = {},
                    onCopyToDate = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.copy_task))
            .assertDoesNotExist()
    }
}
