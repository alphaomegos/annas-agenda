package com.alphaomegos.annasagenda

/**
 * Which of the three lamps the Undone indicator is showing.
 *
 * The rule lived in two screens, written out twice, and the two had to agree
 * by hand. They did — but the pair was about to become a quartet, because each
 * of the three now has a light and a dark drawing, and "the same when clause
 * in two files" is how the lamp and the Undone screen disagreed in the first
 * place, which is what 0006 was about.
 *
 * Said here once, as a question about state rather than about pictures, so it
 * can be asked from a terminal.
 */
enum class UndoneLamp {
    /** Nothing is owed. */
    GREEN,

    /** The user turned the indicator off; the app does not nag. */
    GRAY,

    /** Something in the past is still undone. */
    RED,
}

/**
 * Muted wins over owing, deliberately.
 *
 * Turning the lamp off is the user saying "I know, stop telling me". An app
 * that went on showing red anyway would be answering a question nobody asked;
 * the debts are still there, and the Undone screen still lists them.
 */
fun undoneLampFor(muted: Boolean, hasDebt: Boolean): UndoneLamp = when {
    muted -> UndoneLamp.GRAY
    hasDebt -> UndoneLamp.RED
    else -> UndoneLamp.GREEN
}
