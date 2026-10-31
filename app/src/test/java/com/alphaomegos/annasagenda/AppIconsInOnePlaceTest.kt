package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/**
 * The calendar marks and the food shelves take their pictures from AppIcons
 * and nowhere else.
 *
 * Eduard will replace the Material icons with his own (02.10). That is one
 * line per icon in AppIcons.kt only while no screen imports one of those
 * icons itself — a second copy would be the one that quietly stays Material.
 */
class AppIconsInOnePlaceTest {

    private val iconImport = Regex("""^import androidx\.compose\.material\.icons\.\w+\.(\w+)$""")

    @Test
    fun noOtherFileImportsAnIconAppIconsHandsOut() {
        val files = mainSourceFiles()
        val home = files.single { it.name == "AppIcons.kt" }
        val owned = home.readText().lines().mapNotNull { iconImport.find(it.trim())?.groupValues?.get(1) }.toSet()

        assertTrue("AppIcons.kt imports no icons; the pattern no longer matches", owned.size >= 10)

        val strays = files.filter { it != home }.flatMap { file ->
            file.readText().lines()
                .mapNotNull { iconImport.find(it.trim())?.groupValues?.get(1) }
                .filter { it in owned }
                .map { "${file.name}: $it" }
        }

        assertTrue("these name an AppIcons picture themselves: $strays", strays.isEmpty())
    }

    /** An import is an arrow into a tray, named on a long press (0366): no folder for it any more. */
    @Test
    fun anImportSaysImport() {
        val files = mainSourceFiles()
        val icons = files.single { it.name == "AppIcons.kt" }.readText()
        assertTrue(icons.hasCode("val importData: ImageVector = Icons.Filled.SystemUpdateAlt") && !icons.contains("FolderOpen"))
        val button = files.single { it.name == "ImportIconButton.kt" }.readText()
        assertTrue(button.hasCode("tooltip = { PlainTooltip { Text(label) } },") && button.hasCode("Icon(AppIcons.importData, contentDescription = label)"))
        val users = files.filter { it.readText().contains("ImportIconButton(stringResource(") }.map { it.name }.toSet()
        assertTrue("$users", users == setOf("RunningPlanScreen.kt", "AnthropometryScreen.kt"))
    }
}
