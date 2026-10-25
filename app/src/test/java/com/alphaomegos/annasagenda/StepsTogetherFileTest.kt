package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.model.StepFriend
import com.alphaomegos.annasagenda.model.StepInvitation
import com.alphaomegos.annasagenda.model.StepSharing
import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Steps together's own file (0305): kept as it was, and never part of the app's state. */
class StepsTogetherFileTest {

    @Test
    fun theFileComesBackAsKept() {
        val state = StepsTogetherState(
            sharing = StepSharing(true, "Эдик", "Эдуард", true),
            friends = listOf(StepFriend("u1", "Аня", true, mapOf(LocalDate.of(2026, 10, 24) to 8012))),
            invitations = listOf(StepInvitation("i1", "Ира", true, "2026-10-25T09:14:03.000Z"), StepInvitation("o1", "Оля", false, "x")),
            prefs = mapOf("u1" to StepFriendPref(1, shown = false)),
            selfShown = false,
            notified = setOf("i1"),
            syncedAt = "2026-10-25T10:00:00Z",
        )
        val text = sidecarJson.encodeToString(StepsTogetherFileDto.serializer(), state.toFileDto())
        assertEquals(state, sidecarJson.decodeFromString(StepsTogetherFileDto.serializer(), text).toState())
    }

    /** The friends' days are theirs: the app's state — and so its backups — has no place for them. */
    @Test
    fun theAppsStateHasNoFriends() {
        val json = appStateStoreJson.encodeToString(AppStateDto.serializer(), com.alphaomegos.annasagenda.model.AppState().toDto())
        listOf("friend", "invitation", "sharing").forEach { assertTrue("$it in the app's state", !json.contains(it, ignoreCase = true)) }
    }
}
