package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A top bar's Back is TopBarBack and nothing else (0291, 19.10), so that the
 * user's choice — arrow or word — reaches every screen. Half drew an arrow and
 * half the word before; a screen written the old way would quietly ignore the
 * setting.
 *
 * Not caught here, and meant not to be: the full-width Back at the foot of a
 * screen (a button with a modifier), and the metro's retro styles, which draw
 * their own Back in their own ink.
 */
class TopBarBackEverywhereTest {

    private val handMade = listOf(
        Regex("""TextButton\(onClick = onBack\) \{ Text\(stringResource\(R\.string\.back\)\) \}"""),
        Regex("""Icons\.AutoMirrored\.Filled\.ArrowBack,\s*contentDescription = stringResource\(R\.string\.back\)"""),
    )

    @Test
    fun noScreenDrawsItsOwnBack() {
        val files = mainSourceFiles()
        assertTrue("TopBarBack.kt is gone", files.any { it.name == "TopBarBack.kt" })
        val strays = files
            .filter { it.name != "TopBarBack.kt" }
            .filter { file -> val text = file.readText(); handMade.any { it.containsMatchIn(text) } }
            .map { it.name }
        assertTrue("these draw Back themselves; use TopBarBack: $strays", strays.isEmpty())
    }
}
