package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import java.util.Locale

/** Signing in to oursidecar and what its server says (16.10). */
class SidecarSessionTest {

    private val now = 1_000_000L

    @Test
    fun theSessionIsTheCookieTheServerSetsForAsLongAsItSays() {
        val session = sidecarSessionFromCookies(
            listOf("oursidecar_session=ab12cd; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000; Secure"),
            now,
        )

        assertEquals("ab12cd", session?.token)
        assertEquals(now + 2_592_000_000L, session?.expiresAtMillis)
    }

    @Test
    fun otherCookiesAreNotTheSessionAndWithoutAnAgeItIsTheServersThirtyDays() {
        val session = sidecarSessionFromCookies(listOf("theme=dark; Max-Age=5", "oursidecar_session=ff00; Path=/"), now)

        assertEquals("ff00", session?.token)
        assertEquals(now + SIDECAR_SESSION_DEFAULT_SECONDS * 1000, session?.expiresAtMillis)
    }

    @Test
    fun aClearedCookieIsNoSessionAndTheLastToNameItWins() {
        assertNull(sidecarSessionFromCookies(listOf("oursidecar_session=; Path=/; Max-Age=0"), now))
        assertNull(sidecarSessionFromCookies(listOf("oursidecar_session=aa; Max-Age=60", "oursidecar_session=; Max-Age=0"), now))
        assertEquals("bb", sidecarSessionFromCookies(listOf("oursidecar_session=; Max-Age=0", "oursidecar_session=bb; Max-Age=60"), now)?.token)
        assertNull(sidecarSessionFromCookies(emptyList(), now))
    }

    @Test
    fun aTokenThatCouldBreakTheHeaderIsNotBelieved() {
        assertNull(sidecarSessionFromCookies(listOf("oursidecar_session=a b; Max-Age=60"), now))
        assertNull(sidecarSessionFromCookies(listOf("oursidecar_session=\"ab\"; Max-Age=60"), now))
        assertFalse(sidecarTokenUsable("ab\r\nX-Evil: 1"))
        assertTrue(sidecarTokenUsable("0f9e8d"))
        assertEquals("oursidecar_session=0f9e8d", sidecarCookieHeader("0f9e8d"))
    }

    @Test
    fun theIconIsOutWithNobodyInWhileTheSessionRunsAndAsksOtherwise() {
        val live = now + 10

        assertEquals(SidecarSignInState.SIGNED_OUT, sidecarSignInState(false, live, false, now))
        assertEquals(SidecarSignInState.SIGNED_IN, sidecarSignInState(true, live, false, now))
        assertEquals(SidecarSignInState.ATTENTION, sidecarSignInState(true, live, true, now))
        assertEquals(SidecarSignInState.ATTENTION, sidecarSignInState(true, live, false, now + 10))
        assertEquals(SidecarSignInState.ATTENTION, sidecarSignInState(true, null, false, now))
    }

    @Test
    fun theAddressIsABareHttpsHost() {
        assertEquals("https://oursidecar.com", sidecarServerUrl(" oursidecar.com "))
        assertEquals("https://oursidecar.com", sidecarServerUrl("https://OurSidecar.com/"))
        assertEquals("https://test.example.org:8443", sidecarServerUrl("test.example.org:8443"))

        assertNull(sidecarServerUrl(""))
        assertNull(sidecarServerUrl("http://oursidecar.com"))
        assertNull(sidecarServerUrl("https://oursidecar.com/api"))
        assertNull(sidecarServerUrl("https://me@oursidecar.com"))
        assertNull(sidecarServerUrl("https://oursidecar.com:0"))
        assertNull(sidecarServerUrl("https://oursidecar.com:http"))
        assertNull(sidecarServerUrl("ftp://oursidecar.com"))
        assertNull(sidecarServerUrl("our sidecar.com"))
    }

    @Test
    fun anIdIsEscapedIntoItsSegment() {
        assertEquals("/api/tracks/3f2a-9b/audio", sidecarAudioPath("3f2a-9b"))
        assertEquals("/api/sidecars/a%2Fb%20c/tracks", sidecarTracksPath("a/b c"))
        assertEquals("/api/sidecars/%D1%8F", sidecarSpacePath("я"))
    }

    @Test
    fun anAnswerSaysWhatWentWrong() {
        assertNull(sidecarFailureOf(200))
        assertNull(sidecarFailureOf(201))
        assertEquals(SidecarFailure.WRONG_PASSWORD, sidecarFailureOf(401, signingIn = true))
        assertEquals(SidecarFailure.SIGNED_OUT, sidecarFailureOf(401))
        assertEquals(SidecarFailure.TOO_MANY_ATTEMPTS, sidecarFailureOf(429, signingIn = true))
        assertEquals(SidecarFailure.NOT_FOUND, sidecarFailureOf(404))
        assertEquals(SidecarFailure.SERVER, sidecarFailureOf(503))
        assertEquals(SidecarFailure.UNEXPECTED, sidecarFailureOf(400))
    }

    @Test
    fun aWaitIsSaidInWholeMinutes() {
        assertEquals(1L, sidecarRetryMinutes("0"))
        assertEquals(1L, sidecarRetryMinutes("30"))
        assertEquals(1L, sidecarRetryMinutes("60"))
        assertEquals(2L, sidecarRetryMinutes(" 61 "))
        assertNull(sidecarRetryMinutes(null))
        assertNull(sidecarRetryMinutes("soon"))
        assertNull(sidecarRetryMinutes("-5"))
    }
}

/** The lists, the queue and the kept copies of oursidecar's music (16.10). */
class SidecarMusicTest {

    private val eduard = SidecarMember("u1", "eduard@example.org", nickname = "Эдик")
    private val anna = SidecarMember("u2", "anna@example.org")

    private fun track(
        id: String,
        title: String = id,
        artist: String? = null,
        by: String = "u1",
        at: Long = 0,
        approved: Boolean = true,
        audio: Boolean = true,
        size: Long? = 100,
        etag: String? = "e$id",
    ) = SidecarTrack(
        id = id,
        title = title,
        artist = artist,
        createdByUserId = by,
        createdAt = at,
        approvedAt = if (approved) at + 1 else null,
        hasAudio = audio,
        audioSize = if (audio) size else null,
        audioEtag = if (audio) etag else null,
        pendingApproval = !approved,
    )

    @Test
    fun aPersonIsTheirNicknameOrTheirAddressUpToTheAt() {
        assertEquals("Эдик", musicMemberName(eduard))
        assertEquals("anna", musicMemberName(anna))
        assertEquals("@odd", musicMemberName(SidecarMember("u3", "@odd", nickname = "  ")))
    }

    @Test
    fun theListsAreAllByArtistThenEachPersonInTheOrderTheyJoined() {
        assertEquals(
            listOf(MusicTab.All, MusicTab.ByArtist, MusicTab.Member("u1"), MusicTab.Member("u2")),
            musicTabs(listOf(eduard, anna)),
        )
        assertEquals(listOf(MusicTab.All, MusicTab.Member("u2")), musicTabs(listOf(anna), byArtist = false))
    }

    @Test
    fun aListIsRememberedAndReadBackAndOneThatIsGoneIsAllSongs() {
        val tabs = musicTabs(listOf(eduard, anna))
        tabs.forEach { assertEquals(it, parseMusicTab(musicTabKey(it))) }
        assertEquals(MusicTab.All, parseMusicTab(null))
        assertEquals(MusicTab.All, parseMusicTab("member:"))
        assertEquals(MusicTab.All, parseMusicTab("playlist"))

        assertEquals(MusicTab.Member("u2"), musicTabShown(MusicTab.Member("u2"), tabs))
        assertEquals(MusicTab.All, musicTabShown(MusicTab.Member("u9"), tabs))
        assertEquals(MusicTab.All, musicTabShown(MusicTab.ByArtist, musicTabs(listOf(anna), byArtist = false)))
        assertEquals(MusicSort.ZA, parseMusicSort("ZA"))
        assertEquals(MusicSort.NEWEST, parseMusicSort("random"))
    }

    @Test
    fun allSongsAreTheApprovedAndAPersonsListHasTheirWaitingOnesToo() {
        val tracks = listOf(track("1", by = "u1"), track("2", by = "u2", approved = false), track("3", by = "u2"))

        assertEquals(listOf("1", "3"), musicTracksOf(tracks, MusicTab.All, null).map { it.id })
        assertEquals(listOf("2", "3"), musicTracksOf(tracks, MusicTab.Member("u2"), null).map { it.id })
    }

    @Test
    fun theArtistsAreOfTheApprovedEachOnceInOrderAndTheChosenOneStaysWhileThere() {
        val tracks = listOf(
            track("1", artist = "moby"),
            track("2", artist = " ABBA "),
            track("3", artist = "moby"),
            track("4", artist = "Zz Top", approved = false),
            track("5", artist = null),
            track("6", artist = "Zemfira"),
        )
        val artists = musicArtists(tracks, Locale.ENGLISH)

        assertEquals(listOf("ABBA", "moby", "Zemfira"), artists)
        assertEquals("moby", musicArtistShown("moby", artists))
        assertEquals("ABBA", musicArtistShown("Zz Top", artists))
        assertNull(musicArtistShown(null, emptyList()))
        assertEquals(listOf("1", "3"), musicTracksOf(tracks, MusicTab.ByArtist, "moby").map { it.id })
        assertEquals(listOf("2"), musicTracksOf(tracks, MusicTab.ByArtist, "ABBA").map { it.id })
        assertTrue(musicTracksOf(tracks, MusicTab.ByArtist, null).isEmpty())
    }

    @Test
    fun theOrdersAreByWhenAddedAndByArtistAndTitleWithoutCase() {
        val tracks = listOf(
            track("a", title = "Lift Me Up", artist = "Moby", at = 20),
            track("b", title = "Dancing Queen", artist = "abba", at = 30),
            track("c", title = "Porcelain", artist = "moby", at = 10),
        )

        assertEquals(listOf("b", "a", "c"), musicSorted(tracks, MusicSort.NEWEST, Locale.ENGLISH).map { it.id })
        assertEquals(listOf("c", "a", "b"), musicSorted(tracks, MusicSort.OLDEST, Locale.ENGLISH).map { it.id })
        assertEquals(listOf("b", "a", "c"), musicSorted(tracks, MusicSort.AZ, Locale.ENGLISH).map { it.id })
        assertEquals(listOf("c", "a", "b"), musicSorted(tracks, MusicSort.ZA, Locale.ENGLISH).map { it.id })
    }

    @Test
    fun equalNamesKeepTheOrderTheyCameIn() {
        val tracks = listOf(track("1", title = "Same", artist = "X"), track("2", title = "same", artist = "x"))

        assertEquals(listOf("1", "2"), musicSorted(tracks, MusicSort.AZ, Locale.ENGLISH).map { it.id })
        assertEquals(listOf("1", "2"), musicSorted(tracks, MusicSort.ZA, Locale.ENGLISH).map { it.id })
    }

    @Test
    fun theQueueIsTheListsPlayableBeginningAtTheOnePressed() {
        val shown = listOf(track("1"), track("2", audio = false), track("3"), track("4"))
        val playable: (SidecarTrack) -> Boolean = { it.hasAudio }

        val fromThird = musicQueue(shown, shown[2], playable)
        assertEquals(listOf("1", "3", "4"), fromThird?.tracks?.map { it.id })
        assertEquals(1, fromThird?.startIndex)

        assertEquals(0, musicQueue(shown, null, playable)?.startIndex)
        assertEquals(listOf("9"), musicQueue(shown, track("9"), playable)?.tracks?.map { it.id })
        assertNull(musicQueue(shown, shown[1], playable))
        assertNull(musicQueue(listOf(track("5", audio = false)), null, playable))
    }

    @Test
    fun aFileOfThePhonesMakesATrackWithoutOneOnTheServerPlayable() {
        val shown = listOf(track("1", audio = false), track("2"))
        val mine = setOf("1")

        val queue = musicQueue(shown, shown[0]) { it.hasAudio || it.id in mine }

        assertEquals(listOf("1", "2"), queue?.tracks?.map { it.id })
        assertEquals(0, queue?.startIndex)
    }

    @Test
    fun aPositionIsMinutesAndSeconds() {
        assertEquals("0:00", musicTimeLabel(0))
        assertEquals("0:00", musicTimeLabel(-5))
        assertEquals("3:07", musicTimeLabel(187_900))
        assertEquals("62:05", musicTimeLabel(3_725_000))
    }

    @Test
    fun aKeptCopyIsNamedByItsTrackAndItsFile() {
        assertEquals("track:7:e7", musicCacheKey(track("7")))
        assertNull(musicCacheKey(track("8", audio = false)))
        assertNull(musicCacheKey(track("9", etag = "")))
        assertEquals("7", musicCacheKeyTrackId("track:7:e7"))
        assertNull(musicCacheKeyTrackId("cover:7"))
        assertNull(musicCacheKeyTrackId("track::x"))
    }

    @Test
    fun aCopyIsStaleWhenItsTrackIsGoneItsFileReplacedOrItIsNotToBeKept() {
        val tracks = listOf(track("1"), track("2", etag = "new"), track("3"))
        val kept = setOf("track:1:e1", "track:2:e2", "track:3:e3", "track:4:e4")

        assertEquals(setOf("track:2:e2", "track:3:e3", "track:4:e4"), musicCacheKeysStale(kept, tracks, notKept = setOf("3")))
        assertTrue(musicCacheKeysStale(emptySet(), tracks, emptySet()).isEmpty())
    }

    @Test
    fun saveAllFetchesWhatIsNotKeptYetAndSaysHowMuch() {
        val tracks = listOf(
            track("1", size = 5_000_000),
            track("2", size = 7_000_000),
            track("3", audio = false),
            track("4", size = 1_000_000),
            track("1", size = 5_000_000),
            track("5", size = null),
        )

        val plan = musicDownloadPlan(tracks, kept = setOf("track:2:e2"), notKept = setOf("4"))

        assertEquals(listOf("1", "5"), plan.tracks.map { it.id })
        assertEquals(5_000_000L, plan.bytes)
    }
}
