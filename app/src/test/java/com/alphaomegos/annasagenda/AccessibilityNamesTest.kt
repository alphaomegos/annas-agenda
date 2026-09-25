package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Buttons made of nothing but a picture have to say what they are.
 *
 * A tappable icon with no contentDescription is announced as "button" and
 * nothing else. On a screen with four of them in a row — back, settings, add,
 * the overflow menu — that is four identical buttons, and the only way to find
 * the right one is to press them and see.
 *
 * The rule here is narrow on purpose: it asks only about buttons whose whole
 * content is an icon. A button holding an icon *and* a word is named by the
 * word, and its icon is decoration that should stay unnamed — describing it
 * too would have the name read out twice.
 *
 * Checked by reading the sources rather than by driving the UI, so it costs
 * nothing and runs everywhere. The same sweep found eight of these, which is
 * why it exists.
 */
class AccessibilityNamesTest {

    @Test
    fun everyIconOnlyButtonSaysWhatItIs() {
        val unnamed = mutableListOf<String>()

        mainSourceFiles().forEach { file ->
            val lines = file.readText().lines()

            lines.forEachIndexed { index, line ->
                if (!Regex("""\bIconButton\s*\(""").containsMatchIn(line)) return@forEachIndexed

                val block = blockAt(lines, index) ?: return@forEachIndexed
                val holdsAnIcon = Regex("""\b(Icon|Image)\s*\(""").containsMatchIn(block)
                val holdsAWord = Regex("""\bText\s*\(""").containsMatchIn(block)

                if (!holdsAnIcon || holdsAWord) return@forEachIndexed

                val described = Regex("""contentDescription\s*=\s*(?!null\b)\S""")
                    .containsMatchIn(block)

                if (!described) unnamed += "${file.name}:${index + 1}"
            }
        }

        assertTrue(
            "these buttons are only an icon and announce nothing: $unnamed",
            unnamed.isEmpty(),
        )
    }

    /**
     * The block opened on [start], by counting braces.
     *
     * Null when it does not close within a sensible distance, which means the
     * line was not what this test thinks it was and guessing further would
     * only produce a confident wrong answer.
     */
    private fun blockAt(lines: List<String>, start: Int): String? {
        var depth = 0
        var opened = false
        val body = StringBuilder()

        for (i in start until minOf(lines.size, start + 40)) {
            val line = lines[i]
            depth += line.count { it == '{' } - line.count { it == '}' }
            body.appendLine(line)
            if (line.contains('{')) opened = true
            if (opened && depth <= 0) return body.toString()
        }

        return null
    }
}
