package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings in three groups, by what a setting is about (0292, 19.10): how the
 * app looks, how tasks behave, each section's own — and an on/off setting is
 * a switch. A row added later in the wrong group is the list going back to a
 * heap, which is what Eduard asked to have tidied.
 */
class SettingsGroupsTest {

    private val screen by lazy { mainSourceFiles().single { it.name == "SettingsScreen.kt" }.readText() }

    private fun at(what: String): Int = screen.indexOf(what).also { assertTrue("$what is not in Settings", it >= 0) }

    @Test
    fun eachSettingIsInItsGroup() {
        val appearance = at("R.string.settings_section_appearance")
        val tasks = at("R.string.settings_section_tasks")
        val sections = at("R.string.settings_section_screens")
        assertTrue("the groups are out of order", appearance < tasks && tasks < sections)

        listOf("R.string.choose_language", "R.string.theme_mode_menu", "R.string.palette_menu", "R.string.back_style_menu", "R.string.widget_style_menu")
            .forEach { assertTrue("$it belongs to Appearance", at(it) in appearance..tasks) }
        listOf("R.string.settings_done_tasks_last", "R.string.settings_done_subtasks_last", "R.string.settings_ask_before_deleting", "R.string.undone_title", "R.string.notif_settings_menu")
            .forEach { assertTrue("$it belongs to Tasks", at(it) in tasks..sections) }
        listOf("R.string.calendar_title", "R.string.calorimeter_title", "R.string.anthropometry_title", "R.string.metro_settings_scheme", "R.string.steps_together_menu")
            .forEach { assertTrue("$it belongs to the sections", at(it) > sections) }
    }

    @Test
    fun anOnOffSettingIsASwitch() {
        assertTrue(Regex("""SettingsSwitchRow\(\s*stringResource\(R\.string\.settings_done_tasks_last\)""").containsMatchIn(screen))
        assertTrue(Regex("""SettingsSwitchRow\(\s*stringResource\(R\.string\.settings_done_subtasks_last\)""").containsMatchIn(screen))
        assertTrue(Regex("""SettingsSwitchRow\(\s*stringResource\(R\.string\.settings_ask_before_deleting\)""").containsMatchIn(screen))
        assertTrue("a switch row must be a switch to TalkBack", screen.contains("role = Role.Switch"))
    }
}
