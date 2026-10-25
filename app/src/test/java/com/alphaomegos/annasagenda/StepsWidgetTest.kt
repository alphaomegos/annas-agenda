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
}
