package com.alphaomegos.annasagenda.screens

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alphaomegos.annasagenda.AppThemeMode
import com.alphaomegos.annasagenda.R
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainMenuContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Any day that is not the 29th of July. The menu must look normal on it. */
    private val anOrdinaryDay: LocalDate = LocalDate.of(2026, 7, 28)

    private val annaDay: LocalDate = LocalDate.of(2026, 7, 29)

    @Test
    fun mainMenuContent_showsVisibleItems_hidesHiddenItems_and_routesBasicClicks() {
        var languageClicks = 0
        var calendarClicks = 0

        composeRule.setContent {
            MaterialTheme {
                MainMenuContent(
                    today = anOrdinaryDay,
                    langIconRes = R.drawable.ic_langflag_en,
                    undoneLampIconRes = R.drawable.ic_undone_lamp_green,
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
                    themeMode = AppThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    onLanguage = { languageClicks++ },
                    onUndone = {},
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
                    today = anOrdinaryDay,
                    langIconRes = R.drawable.ic_langflag_en,
                    undoneLampIconRes = R.drawable.ic_undone_lamp_green,
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
                    themeMode = AppThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    onLanguage = {},
                    onUndone = {},
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

    /**
     * The one card that is only there for one day.
     *
     * Both halves are asked, because an easter egg that is always on is not an
     * easter egg and one that is never on is an easter egg nobody will ever
     * see — and neither failure shows up for a year.
     */
    @Test
    fun mainMenuContent_showsTheAnnaDayCard_onTheTwentyNinthOfJuly() {
        setMenu(today = annaDay)

        val activity = composeRule.activity
        composeRule.onNodeWithText(activity.getString(R.string.anna_day_title))
            .assertIsDisplayed()
    }

    @Test
    fun mainMenuContent_hasNoAnnaDayCard_onAnyOtherDay() {
        setMenu(today = anOrdinaryDay)

        val activity = composeRule.activity
        composeRule.onAllNodesWithText(activity.getString(R.string.anna_day_title))
            .assertCountEquals(0)
    }

    /** The menu at its plainest: one item, nothing hidden, no callbacks wanted. */
    private fun setMenu(today: LocalDate) {
        composeRule.setContent {
            MaterialTheme {
                MainMenuContent(
                    today = today,
                    langIconRes = R.drawable.ic_langflag_en,
                    undoneLampIconRes = R.drawable.ic_undone_lamp_green,
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
                    themeMode = AppThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    onLanguage = {},
                    onUndone = {},
                    onExport = {},
                    onImport = {},
                    onResetConfirmed = {},
                )
            }
        }
    }
}
