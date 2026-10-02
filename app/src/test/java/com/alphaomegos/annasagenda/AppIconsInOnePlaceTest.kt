package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test

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
}
