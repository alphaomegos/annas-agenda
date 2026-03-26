package com.alphaomegos.annasagenda.screens

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alphaomegos.annasagenda.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainMenuContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mainMenuContent_showsVisibleItems_hidesHiddenItems_and_routesBasicClicks() {
        var languageClicks = 0
        var calendarClicks = 0

        composeRule.setContent {
            MaterialTheme {
                MainMenuContent(
                    langIconRes = R.drawable.ic_langflag_en,
                    menuEntries = listOf(
                        MenuEntry(
                            id = "calendar",
                            iconRes = R.drawable.ic_menu_calendar,
                            titleRes = R.string.calendar,
                            onClick = { calendarClicks++ },
                        ),
                        MenuEntry(
                            id = "new_task",
                            iconRes = R.drawable.ic_menu_new_task,
                            titleRes = R.string.create_task,
                            onClick = {},
                        ),
                        MenuEntry(
                            id = "reading",
                            iconRes = R.drawable.ic_menu_reading,
                            titleRes = R.string.menu_reading,
                            onClick = {},
                        ),
                    ),
                    menuOrderIds = listOf("reading", "calendar", "new_task"),
                    menuHiddenIds = setOf("new_task"),
                    onMenuOrderChange = {},
                    onHideMenuItem = {},
                    onShowAllMenuItems = {},
                    onLanguage = { languageClicks++ },
                    onExport = {},
                    onImport = {},
                    onResetConfirmed = {},
                )
            }
        }

        val activity = composeRule.activity

        composeRule.onNodeWithText(activity.getString(R.string.menu_reading))
            .assertIsDisplayed()

        composeRule.onNodeWithText(activity.getString(R.string.calendar))
            .assertIsDisplayed()

        composeRule.onNodeWithText(activity.getString(R.string.create_task))
            .assertDoesNotExist()

        composeRule.onNodeWithContentDescription(
            activity.getString(R.string.choose_language)
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(1, languageClicks)
        }

        composeRule.onNodeWithText(activity.getString(R.string.calendar))
            .performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(1, calendarClicks)
        }
    }

    @Test
    fun mainMenuContent_dataMenu_routesCallbacks_and_reset_requiresConfirmation() {
        var exportClicks = 0
        var importClicks = 0
        var resetConfirmed = 0

        composeRule.setContent {
            MaterialTheme {
                MainMenuContent(
                    langIconRes = R.drawable.ic_langflag_en,
                    menuEntries = listOf(
                        MenuEntry(
                            id = "calendar",
                            iconRes = R.drawable.ic_menu_calendar,
                            titleRes = R.string.calendar,
                            onClick = {},
                        ),
                    ),
                    menuOrderIds = emptyList(),
                    menuHiddenIds = emptySet(),
                    onMenuOrderChange = {},
                    onHideMenuItem = {},
                    onShowAllMenuItems = {},
                    onLanguage = {},
                    onExport = { exportClicks++ },
                    onImport = { importClicks++ },
                    onResetConfirmed = { resetConfirmed++ },
                )
            }
        }

        val activity = composeRule.activity
        val dataMenuLabel = activity.getString(R.string.data_menu)

        composeRule.onNodeWithContentDescription(dataMenuLabel)
            .performClick()

        composeRule.onNodeWithText(activity.getString(R.string.export_backup_json))
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, exportClicks)
        }

        composeRule.onNodeWithContentDescription(dataMenuLabel)
            .performClick()

        composeRule.onNodeWithText(activity.getString(R.string.import_backup_json))
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, importClicks)
        }

        composeRule.onNodeWithContentDescription(dataMenuLabel)
            .performClick()

        composeRule.onNodeWithText(activity.getString(R.string.reset_data_menu))
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithText(activity.getString(R.string.reset_title))
            .assertIsDisplayed()

        composeRule.onNodeWithText(activity.getString(R.string.ok))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, resetConfirmed)
        }
    }
}