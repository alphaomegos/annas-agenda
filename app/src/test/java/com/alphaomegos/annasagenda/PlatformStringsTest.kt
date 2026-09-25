package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the user reads is decided by this app, not by the phone.
 *
 * `android.R.string.ok` and its neighbours are the platform's words, and the
 * platform follows the language of the **device**. This app has its own
 * language setting, so on a phone set to one language and an app set to
 * another — which is the case this app was written for — a dialog would come
 * out half in each: the question in Russian and the button saying "OK" in
 * whatever the phone is.
 *
 * Patch 0037 replaced six of these in the confirmation dialogs. Nine more were
 * left in four files and outlived that patch by a fortnight, which is the
 * argument for a rule rather than another sweep.
 *
 * The app has `R.string.ok` and `R.string.cancel` in all four locales, so
 * there is nothing to weigh up: the platform version is never the right one
 * here.
 */
class PlatformStringsTest {

    /**
     * The other half of the same rule, for numbers.
     *
     * `String.format` and `DecimalFormat` both fall back to
     * `Locale.getDefault()` when nothing is said, which is the machine's
     * answer rather than anyone's decision — and it silently changes what is
     * written into a saved task name. It also makes tests pass for the wrong
     * reason: 0092 had one asserting "10.5" that only held because this
     * machine speaks English.
     *
     * The rule is not "always English". It is "say which, out loud, at every
     * site", so that changing the answer is one visible edit rather than a
     * property of whoever is holding the phone.
     */
    @Test
    fun noNumberTheUserReadsIsFormattedByWhateverTheMachineIsSetTo() {
        val unsaid = mutableListOf<String>()

        mainSourceFiles().forEach { file ->
            file.readText().lines().forEachIndexed { index, line ->
                if (isCommentLine(line)) return@forEachIndexed

                val formats = Regex("""\bString\.format\s*\(""").containsMatchIn(line) &&
                    !Regex("""\bString\.format\s*\(\s*\w*Locale\b""").containsMatchIn(line)

                val decimals = Regex("""\bDecimalFormat\s*\(""").containsMatchIn(line) &&
                    !line.contains("DecimalFormatSymbols")

                if (formats || decimals) unsaid += "${file.name}:${index + 1}"
            }
        }

        assertTrue(
            "these let the machine's locale decide how a number reads, " +
                "name a Locale instead: $unsaid",
            unsaid.isEmpty(),
        )
    }

    @Test
    fun noScreenBorrowsTheDevicesWordsForItsButtons() {
        val borrowed = mutableListOf<String>()

        mainSourceFiles().forEach { file ->
            file.readText().lines().forEachIndexed { index, line ->
                if (isCommentLine(line)) return@forEachIndexed

                if (line.contains("android.R.string")) {
                    borrowed += "${file.name}:${index + 1}"
                }
            }
        }

        assertTrue(
            "these read the device's language instead of the app's, " +
                "use R.string.ok / R.string.cancel instead: $borrowed",
            borrowed.isEmpty(),
        )
    }
}
