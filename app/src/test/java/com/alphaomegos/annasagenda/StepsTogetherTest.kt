package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.StepFriend
import com.alphaomegos.annasagenda.model.StepInvitation
import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Steps together on the phone (0305): colours that stay, and each invitation told of once. */
class StepsTogetherTest {

    private fun friend(id: String) = StepFriend(id, id, true, emptyMap())

    @Test
    fun aFriendKeepsTheirColour_aNewOneTakesTheFirstFree_aGoneOneFreesIt() {
        val before = mapOf("a" to StepFriendPref(0), "b" to StepFriendPref(2, shown = false))
        val after = stepFriendPrefsFor(listOf(friend("b"), friend("c"), friend("d")), before)
        assertEquals(StepFriendPref(2, shown = false), after["b"])
        assertEquals(0, after["c"]!!.color)
        assertEquals(1, after["d"]!!.color)
        assertEquals(setOf("b", "c", "d"), after.keys)
    }

    @Test
    fun eachIncomingInvitationIsToldOfOnce() {
        val waiting = listOf(StepInvitation("i1", "Аня", true, ""), StepInvitation("i2", "Ира", true, ""), StepInvitation("o1", "Оля", false, ""))
        assertEquals(listOf("i2"), stepInvitationsToNotify(waiting, setOf("i1")).map { it.id })
        // Answered or gone invitations are forgotten, so the set does not grow.
        assertEquals(setOf("i2"), stepInvitationsNotifiedAfter(waiting.drop(1), setOf("i1", "i2", "old")))
    }
}
