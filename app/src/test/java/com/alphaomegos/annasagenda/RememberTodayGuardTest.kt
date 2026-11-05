package com.alphaomegos.annasagenda

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Screens know today through rememberToday (0398): it turns at midnight and on coming back. */
class RememberTodayGuardTest {
    private val root = File(mainSourceRoot(), "com/alphaomegos/annasagenda")

    @Test
    fun noScreenKeepsTheDayItOpenedOn() {
        val once = Regex("""val today = (remember \{ )?LocalDate\.now\(\)""")
        File(root, "screens").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            assertTrue("${file.name} keeps the day it opened on", !once.containsMatchIn(file.readText()))
        }
        val helper = File(root, "components/RememberToday.kt").readText()
        assertTrue(helper.hasCode("delay(millisUntilTomorrow(LocalDateTime.now(), today))"))
        assertTrue(helper.hasCode("if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()"))
    }
}
