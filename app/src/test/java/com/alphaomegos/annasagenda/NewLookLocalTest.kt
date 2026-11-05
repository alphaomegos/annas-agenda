package com.alphaomegos.annasagenda

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The look reaches the frames shared across screens (0394, Eduard 05.11):
 * the theme provides it, the activity tells the theme, and the metro's and
 * the music's bar, in today's style, is one row in the new look.
 */
class NewLookLocalTest {
    private val root = File(mainSourceRoot(), "com/alphaomegos/annasagenda")

    @Test
    fun theLookReachesTheSharedBars() {
        val theme = File(root, "app/AppTheme.kt").readText()
        assertTrue(theme.hasCode("val LocalNewLook = staticCompositionLocalOf { true }") && theme.hasCode("LocalNewLook provides newLook,"))
        assertTrue(File(root, "app/MainActivity.kt").readText().hasCode("backStyle = backStyle, newLook = newLook) {"))
        val metro = File(root, "screens/metro/MetroStyle.kt").readText()
        assertTrue(metro.hasCode("if (LocalNewLook.current) {") && metro.hasCode("WayHomeButton()"))
        // The other styles are the section's own and keep their bars.
        assertTrue(metro.hasCode("if (style == MetroStyle.MODERN) {"))
    }

    /** The backups (0395): the shared frame in the new look, asked of the local — the screen has no state of its own to ask. */
    @Test
    fun theBackupsHaveTheNewLook() {
        val screen = File(root, "screens/backups/SidecarBackupsScreen.kt").readText()
        assertTrue(screen.hasCode("if (LocalNewLook.current) {") && screen.hasCode("NewLookScreen(title = stringResource(R.string.sidecar_backups_title), onBack = onBack) {"))
        assertTrue(screen.hasCode("body(PaddingValues(0.dp))") && screen.hasCode("body(inner)") && screen.hasCode("CenterAlignedTopAppBar("))
    }
}
