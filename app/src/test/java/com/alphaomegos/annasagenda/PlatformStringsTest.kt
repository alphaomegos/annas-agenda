package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app speaks the language the user chose in the app.
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

    @Test
    fun noScreenBorrowsTheDevicesWordsForItsButtons() {
        val borrowed = mutableListOf<String>()

        mainSourceFiles().forEach { file ->
            file.readText().lines().forEachIndexed { index, line ->
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
