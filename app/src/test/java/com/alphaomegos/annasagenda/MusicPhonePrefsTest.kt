package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.app.MusicPhoneChoices
import com.alphaomegos.annasagenda.app.MusicPhonePrefs
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The phone's music choices (0371): kept across a restart, this phone's own file. */
class MusicPhonePrefsTest {

    private val dir: File = Files.createTempDirectory("phone-prefs").toFile()
    private val file = File(File(dir, "music"), "phone.json")

    @Test
    fun foldersAndHiddenTracksSurviveARestart() {
        val prefs = MusicPhonePrefs(file)
        prefs.setFolders(setOf("Music/", "Download/"))
        prefs.hide("Music/a.mp3")
        prefs.hide("Music/b.mp3")
        prefs.unhide("Music/a.mp3")
        val again = MusicPhonePrefs(file)
        assertEquals(MusicPhoneChoices(setOf("Music/", "Download/"), setOf("Music/b.mp3")), again.choices.value)
    }

    @Test
    fun aBrokenFileIsNoChoicesAtAll() {
        file.parentFile!!.mkdirs()
        file.writeText("{ not json")
        assertEquals(MusicPhoneChoices(), MusicPhonePrefs(file).choices.value)
    }
}
