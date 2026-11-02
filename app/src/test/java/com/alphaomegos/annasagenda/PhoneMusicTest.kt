package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.PhoneFolder
import com.alphaomegos.annasagenda.support.PhoneTrack
import com.alphaomegos.annasagenda.support.asTrack
import com.alphaomegos.annasagenda.support.isPhoneTrackId
import com.alphaomegos.annasagenda.support.key
import com.alphaomegos.annasagenda.support.phoneArtist
import com.alphaomegos.annasagenda.support.phoneFolderChosen
import com.alphaomegos.annasagenda.support.phoneFolderOf
import com.alphaomegos.annasagenda.support.phoneFolders
import com.alphaomegos.annasagenda.support.phoneTitleOf
import com.alphaomegos.annasagenda.support.phoneTrackId
import com.alphaomegos.annasagenda.support.phoneTracksHidden
import com.alphaomegos.annasagenda.support.phoneTracksShown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's own music (0371): keys, folders, what the Local tab lists. */
class PhoneMusicTest {

    private fun track(folder: String, name: String, title: String = name, id: Long = name.hashCode().toLong()) =
        PhoneTrack(id, "content://media/external/audio/media/$id", title, "Кино", 240_000, 9_000_000, folder, name, "audio/mpeg", 1_700_000_000)

    private val a = track("Music/", "Группа крови.mp3")
    private val b = track("Music/Кино/", "Кукушка.mp3")
    private val c = track("Download/", "voice.m4a")
    private val all = listOf(a, b, c)

    @Test
    fun aTrackIsKnownByWhereItLiesAndItsName() {
        assertEquals("Music/Кино/Кукушка.mp3", b.key)
        assertEquals("phone:Music/Кино/Кукушка.mp3", phoneTrackId(b))
        assertTrue(isPhoneTrackId(phoneTrackId(b)))
        assertFalse(isPhoneTrackId("4f9c-uuid"))
        // The index's own id may change; the key does not.
        assertEquals(b.key, b.copy(mediaId = 99).key)
    }

    @Test
    fun asATrackOfTheListsItPlaysAndSortsLikeTheSidecars() {
        val t = b.asTrack()
        assertEquals(phoneTrackId(b), t.id)
        assertEquals("Кукушка.mp3", t.title)
        assertTrue(t.hasAudio)
        assertEquals(9_000_000L, t.audioSize)
        assertEquals(1_700_000_000L, t.createdAt)
        assertEquals(t.createdAt, t.approvedAt)
    }

    @Test
    fun theFoldersWithTheirCounts_byName() {
        assertEquals(
            listOf(PhoneFolder("Download/", 1), PhoneFolder("Music/", 1), PhoneFolder("Music/Кино/", 1)),
            phoneFolders(all),
        )
    }

    @Test
    fun aChosenFolderBringsItsSubfolders_andHiddenOnesStayOut() {
        assertTrue(phoneFolderChosen("Music/Кино/", setOf("Music/")))
        assertFalse(phoneFolderChosen("Musical/", setOf("Music/")))
        assertEquals(listOf(a, b), phoneTracksShown(all, setOf("Music/"), emptySet()))
        assertEquals(listOf(a), phoneTracksShown(all, setOf("Music/"), setOf(b.key)))
        assertEquals(emptyList<PhoneTrack>(), phoneTracksShown(all, emptySet(), emptySet()))
        assertEquals(listOf(b), phoneTracksHidden(all, setOf(b.key, "Music/gone.mp3")))
    }

    @Test
    fun oldPhonesFolderFromTheFullPath() {
        assertEquals("Music/Кино/", phoneFolderOf("/storage/emulated/0/Music/Кино/Кукушка.mp3", "/storage/emulated/0"))
        assertEquals("Music/", phoneFolderOf("/storage/emulated/0/Music/a.mp3", "/storage/emulated/0/"))
        assertEquals("storage/1234-ABCD/Music/", phoneFolderOf("/storage/1234-ABCD/Music/a.mp3", "/storage/emulated/0"))
    }

    @Test
    fun missingTagsAreNotInvented() {
        assertNull(phoneArtist("<unknown>"))
        assertNull(phoneArtist("  "))
        assertEquals("Кино", phoneArtist(" Кино "))
        assertEquals("Звезда по имени Солнце", phoneTitleOf(null, "Звезда по имени Солнце.flac"))
        assertEquals("Звезда", phoneTitleOf("Звезда", "x.mp3"))
        assertEquals("README", phoneTitleOf("", "README"))
    }
}
