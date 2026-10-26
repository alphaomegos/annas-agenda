package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The steps widget (0309): declared, described, and drawn in the tasks
 * widget's style — the one transparency and text colour set in Settings →
 * Widget, not a second set to keep in step.
 */
class StepsWidgetTest {
    private val manifest by lazy { mainResRoot().parentFile.resolve("AndroidManifest.xml").readText() }
    private val widget by lazy { mainSourceFiles().single { it.name == "StepsWidget.kt" }.readText() }

    @Test
    fun theWidgetIsDeclaredAndDescribed() {
        assertTrue(manifest.contains(".StepsWidgetReceiver"))
        assertTrue(manifest.contains("@xml/steps_widget_info"))
        assertTrue(mainResRoot().resolve("xml/steps_widget_info.xml").isFile)
    }

    @Test
    fun itWearsTheTasksWidgetsStyle() {
        assertTrue(widget.contains("widgetBackground(data.style)"))
        assertTrue(widget.contains("widgetTextColor(data.style)"))
        assertTrue(widget.contains("state?.widgetStyle"))
    }

    /**
     * The tasks widget turned into the steps widget (0315): redrawing by class
     * name found the wrong widget once R8 had renamed or merged the classes.
     * Each is redrawn by its receiver's ids, and the classes keep their names.
     */
    @Test
    fun eachWidgetIsRedrawnByItsOwnReceiver() {
        val tasks = mainSourceFiles().single { it.name == "TodayTasksWidget.kt" }.readText()
        assertTrue("updateAll finds widgets by class name", !tasks.contains("updateAll("))
        assertTrue(tasks.contains("redrawWidgetsOf(context, TodayTasksWidgetReceiver::class.java, TodayTasksWidget())"))
        assertTrue(tasks.contains("redrawWidgetsOf(context, StepsWidgetReceiver::class.java, StepsWidget())"))
        val rules = mainResRoot().absoluteFile.parentFile.parentFile.parentFile.resolve("proguard-rules.pro").readText()
        assertTrue(rules.contains("-keep class * extends androidx.glance.appwidget.GlanceAppWidget"))
    }

    /** Both widgets say what failed instead of Glance's bare «Can't show content» (0317). */
    @Test
    fun bothWidgetsShowTheirOwnError() {
        val tasks = mainSourceFiles().single { it.name == "TodayTasksWidget.kt" }.readText()
        assertTrue(tasks.contains("override fun onCompositionError(") && tasks.contains("R.string.widget_error_tasks"))
        assertTrue(widget.contains("override fun onCompositionError(") && widget.contains("R.string.widget_error_steps"))
        assertTrue(mainResRoot().resolve("layout/widget_error.xml").isFile)
    }

    /**
     * A checkbox in a widget takes fixed colours only (0318): GlanceTheme's are
     * the system's dynamic ones from Android 12, resources, and with one of them
     * the tasks widget failed to draw at all on Anna's Pixel 9.
     */
    @Test
    fun theTasksWidgetsTicksAreFixedColours() {
        val tasks = mainSourceFiles().single { it.name == "TodayTasksWidget.kt" }.readText()
        assertTrue("a GlanceTheme colour reaches CheckBoxColors", !tasks.contains("GlanceTheme.colors"))
        assertTrue(tasks.contains("?: WIDGET_DEFAULT_TICK"))
    }
}
