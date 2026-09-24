package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Keeps the translated resources honest about the base ones.
 *
 * Everything checked here is something the build only warns about, or does not
 * mention at all, and every one of them was actually present when this test was
 * written: three keys in Serbian and four in Gilbertese that no longer exist in
 * the base locale (aapt printed "removing resource" and dropped the
 * translator's work on the floor), and one of those four spliced into the middle
 * of another string, which is well-formed XML and silently glued two
 * translations together.
 *
 * A format specifier that does not match the base is the one that hurts at run
 * time: getString hands the arguments to String.format, and a translation
 * asking for an argument the code does not pass throws MissingFormatArgument
 * in whatever screen that string belongs to — for a language nobody on the team
 * reads.
 */
class StringResourcesTest {

    private data class Entry(
        val name: String,
        val text: String,
        val childTags: List<String>,
    )

    @Test
    fun everyTranslatedKeyStillExistsInTheBaseLocale() {
        val base = entriesIn(baseDir()).map { it.name }.toSet()

        translationDirs().forEach { dir ->
            val orphans = entriesIn(dir).map { it.name }.filterNot { it in base }

            assertEquals(
                "${dir.name} translates keys the base locale no longer has. " +
                    "They are dropped at build time, so the translation is lost " +
                    "either way — delete them, or restore the key they were renamed from.",
                emptyList<String>(),
                orphans,
            )
        }
    }

    @Test
    fun noLocaleDeclaresTheSameKeyTwice() {
        (listOf(baseDir()) + translationDirs()).forEach { dir ->
            val names = entriesIn(dir).map { it.name }
            val duplicated = names.groupBy { it }.filterValues { it.size > 1 }.keys.sorted()

            // The last one silently wins, and which one that is depends on the
            // order the files happen to be read in.
            assertEquals("${dir.name} declares a key more than once", emptyList<String>(), duplicated)
        }
    }

    @Test
    fun noStringContainsAnotherElement() {
        (listOf(baseDir()) + translationDirs()).forEach { dir ->
            val nested = entriesIn(dir)
                .filter { it.childTags.isNotEmpty() }
                .map { "${it.name} contains ${it.childTags}" }

            // This app uses no inline markup, so a child element inside a string
            // means a line was mangled — a missing </string> swallows whatever
            // follows it, and both strings come out wrong with no warning.
            assertEquals("${dir.name} has a string with markup inside it", emptyList<String>(), nested)
        }
    }

    @Test
    fun everyTranslationAsksForTheSameArgumentsAsTheBase() {
        val base = entriesIn(baseDir()).associateBy { it.name }

        translationDirs().forEach { dir ->
            entriesIn(dir).forEach { entry ->
                val baseEntry = base[entry.name] ?: return@forEach

                assertEquals(
                    "${dir.name}/${entry.name} does not take the arguments the code passes it",
                    formatSpecifiers(baseEntry.text),
                    formatSpecifiers(entry.text),
                )
            }
        }
    }

    @Test
    fun everyPluralHasTheQuantityEveryLanguageNeeds() {
        val baseNames = pluralsIn(baseDir()).keys

        (listOf(baseDir()) + translationDirs()).forEach { dir ->
            pluralsIn(dir).forEach { (name, quantities) ->
                if (dir != baseDir()) {
                    assertTrue("${dir.name} has a plural the base locale does not: $name", name in baseNames)
                }

                // "other" is the fallback for every count in every language;
                // without it the lookup throws at run time.
                assertTrue("${dir.name}/$name has no \"other\" quantity", "other" in quantities)
            }
        }
    }

    // -- reading the files ---------------------------------------------------

    private fun entriesIn(dir: File): List<Entry> =
        resourceFiles(dir).flatMap { file ->
            elementsOf(file).filter { it.tagName == "string" }.map { element ->
                Entry(
                    name = element.getAttribute("name"),
                    text = element.textContent.orEmpty(),
                    childTags = childElements(element).map { it.tagName },
                )
            }
        }

    private fun pluralsIn(dir: File): Map<String, List<String>> =
        resourceFiles(dir)
            .flatMap { file -> elementsOf(file).filter { it.tagName == "plurals" } }
            .associate { plural ->
                plural.getAttribute("name") to childElements(plural).map { it.getAttribute("quantity") }
            }

    private fun resourceFiles(dir: File): List<File> =
        dir.listFiles { file -> file.isFile && file.name.startsWith("strings") && file.name.endsWith(".xml") }
            ?.sortedBy { it.name }
            .orEmpty()

    private fun elementsOf(file: File): List<Element> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return childElements(document.documentElement)
    }

    private fun childElements(element: Element): List<Element> {
        val nodes = element.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    /**
     * Everything a string asks String.format for, sorted: positional arguments,
     * plain ones, and escaped percent signs alike.
     */
    private fun formatSpecifiers(text: String): List<String> =
        Regex("%(\\d+\\\$[a-zA-Z]|[a-zA-Z%])").findAll(text).map { it.value }.toList().sorted()

    private fun baseDir(): File = File(resRoot(), "values")

    private fun translationDirs(): List<File> =
        resRoot().listFiles { file -> file.isDirectory && file.name.startsWith("values-") }
            ?.sortedBy { it.name }
            .orEmpty()

    private fun resRoot(): File =
        listOf("src/main/res", "app/src/main/res", "../app/src/main/res")
            .map { File(it) }
            .firstOrNull { it.isDirectory }
            ?: error("Cannot find src/main/res from ${File("").absolutePath}")
}
