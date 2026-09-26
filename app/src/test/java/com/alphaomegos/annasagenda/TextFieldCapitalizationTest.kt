package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text field says what kind of keyboard it wants.
 *
 * Android's default is a keyboard that starts in lower case, which is right
 * for almost nothing this app asks for. A task is a sentence, a book has a
 * title, a person has a name — all of them start with a capital, and typing
 * one meant reaching for shift every single time. It is a small thing that
 * happened on every input in the app.
 *
 * The rule is therefore not "these fields were fixed" but **every field
 * declares an intent**: a keyboard type for the numeric ones, a capitalization
 * for the free-text ones, or a place on the short list below saying why it
 * wants neither. A field added next year without one is the same paper cut
 * again, and nothing about the screen would show it.
 *
 * Read from the source rather than from a running screen on purpose. A UI test
 * can only visit the dialogs somebody remembered to write a test for, and the
 * whole point here is the field nobody remembered.
 */
class TextFieldCapitalizationTest {

    /**
     * Fields that genuinely want the plain keyboard, with the reason.
     *
     * Named by their label's string resource, because that is what identifies
     * a field to a reader — a line number stops being true the next time
     * somebody adds a line above it.
     */
    private val fieldsThatWantThePlainKeyboard = mapOf(
        "reading_search_label" to
            "a search box: the query is matched case-insensitively, and a forced " +
            "capital is just a letter to delete",
        "start_date_iso" to "an ISO date: digits and dashes, no words",
        "end_date_iso" to "an ISO date: digits and dashes, no words",
    )

    @Test
    fun everyTextFieldSaysWhatKeyboardItWants() {
        val offenders = mutableListOf<String>()

        mainSourceFiles().forEach { file ->
            val text = file.readText()
            fieldBlocks(text).forEach { (line, block) ->
                if ("keyboardOptions" in block) return@forEach

                val label = labelResourceIn(block)
                if (label != null && label in fieldsThatWantThePlainKeyboard) return@forEach

                offenders += "${file.name}:$line (label: ${label ?: "unknown"})"
            }
        }

        assertTrue(
            "These text fields declare no keyboardOptions. Add a capitalization " +
                "(Sentences for anything a person writes), or a keyboardType for a " +
                "number, or name the field in fieldsThatWantThePlainKeyboard with the " +
                "reason:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /**
     * The free-text fields ask for sentence capitalization specifically.
     *
     * Checked separately from the rule above because "declares something" and
     * "declares the right thing" are different questions, and a field that
     * quietly asked for [androidx.compose.ui.text.input.KeyboardCapitalization.None]
     * would satisfy the first one.
     */
    @Test
    fun theFieldsAPersonWritesIntoAskForACapitalFirstLetter() {
        val withSentences = mainSourceFiles().sumOf { file ->
            fieldBlocks(file.readText())
                .count { (_, block) -> "KeyboardCapitalization.Sentences" in block }
        }

        assertTrue(
            "expected the free-text fields to ask for a capital first letter, found $withSentences",
            withSentences >= 13,
        )
    }

    /**
     * Each `OutlinedTextField(` in the file, as (1-based line, argument list).
     *
     * String literals are blanked before the parentheses are counted: a label
     * built as `stringResource(x) + " (${stringResource(y)})"` carries a
     * bracket inside quotes, and counting it would end the block in the wrong
     * place — silently, by finding no keyboardOptions in a field that has one.
     */
    private fun fieldBlocks(text: String): List<Pair<Int, String>> {
        val blanked = blankStringLiterals(text)
        val out = mutableListOf<Pair<Int, String>>()

        Regex("""\bOutlinedTextField\(""").findAll(blanked).forEach { m ->
            val start = m.range.last
            var depth = 0
            var end = start

            loop@ for (i in start until blanked.length) {
                when (blanked[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            end = i
                            break@loop
                        }
                    }
                }
            }

            val line = blanked.take(m.range.first).count { it == '\n' } + 1
            out += line to blanked.substring(start, end + 1)
        }

        return out
    }

    /** Keeps the quotes so offsets do not move, drops whatever was between them. */
    private fun blankStringLiterals(text: String): String {
        val sb = StringBuilder(text.length)
        var inString = false
        var escaped = false

        text.forEach { c ->
            when {
                escaped -> {
                    sb.append(' '); escaped = false
                }
                inString && c == '\\' -> {
                    sb.append(' '); escaped = true
                }
                c == '"' -> {
                    sb.append(c); inString = !inString
                }
                inString && c == '\n' -> sb.append(c)   // keep line numbers honest
                inString -> sb.append(' ')
                else -> sb.append(c)
            }
        }

        return sb.toString()
    }

    private fun labelResourceIn(block: String): String? =
        Regex("""label\s*=\s*\{[^}]*?R\.string\.(\w+)""", RegexOption.DOT_MATCHES_ALL)
            .find(block)
            ?.groupValues
            ?.get(1)
}
