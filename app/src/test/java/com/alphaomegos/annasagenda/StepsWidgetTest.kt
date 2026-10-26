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
}
