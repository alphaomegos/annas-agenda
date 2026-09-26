package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which lamp, and why that one.
 */
class UndoneLampTest {

    @Test
    fun nothingOwedIsGreen() {
        assertEquals(
            UndoneLamp.GREEN,
            undoneLampFor(muted = false, hasDebt = false),
        )
    }

    @Test
    fun somethingOwedIsRed() {
        assertEquals(
            UndoneLamp.RED,
            undoneLampFor(muted = false, hasDebt = true),
        )
    }

    /**
     * The one worth stating. Turning the lamp off is the user saying "I know".
     * Showing red anyway would be answering a question nobody asked — and the
     * debts are still listed on the Undone screen either way.
     */
    @Test
    fun mutedStaysGreyEvenWhenSomethingIsOwed() {
        assertEquals(
            UndoneLamp.GRAY,
            undoneLampFor(muted = true, hasDebt = true),
        )
        assertEquals(
            UndoneLamp.GRAY,
            undoneLampFor(muted = true, hasDebt = false),
        )
    }

    /**
     * Every state has a lamp, and no state has two. Written as a sweep rather
     * than three assertions so that adding a fourth lamp fails here rather
     * than on somebody's screen.
     */
    @Test
    fun everyCombinationAnswersExactlyOnce() {
        val answers = listOf(false, true).flatMap { muted ->
            listOf(false, true).map { debt -> undoneLampFor(muted, debt) }
        }

        assertEquals(4, answers.size)
        assertEquals(
            setOf(UndoneLamp.GREEN, UndoneLamp.GRAY, UndoneLamp.RED),
            answers.toSet(),
        )
    }
}
