package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroScheme

/**
 * Undo and redo for a metro scheme (08.10): every edit keeps the scheme as
 * it was before, so the editor can step back and forward again. Kept in
 * memory only — a drawing session's safety net, not part of the data — and
 * at most [METRO_HISTORY_LIMIT] steps back. A new edit after stepping back
 * forgets what could be redone, as everywhere else.
 */
data class MetroHistory(
    val undo: List<MetroScheme> = emptyList(),
    val redo: List<MetroScheme> = emptyList(),
) {
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** An edit made: [before] is what undo brings back. */
    fun recorded(before: MetroScheme): MetroHistory =
        MetroHistory((undo + before).takeLast(METRO_HISTORY_LIMIT), emptyList())

    /** One step back from [current]: the scheme to show, and the history after; null with nothing to undo. */
    fun undone(current: MetroScheme): Pair<MetroScheme, MetroHistory>? {
        val previous = undo.lastOrNull() ?: return null
        return previous to MetroHistory(undo.dropLast(1), redo + current)
    }

    /** One step forward again from [current]; null with nothing to redo. */
    fun redone(current: MetroScheme): Pair<MetroScheme, MetroHistory>? {
        val next = redo.lastOrNull() ?: return null
        return next to MetroHistory(undo + current, redo.dropLast(1))
    }
}

const val METRO_HISTORY_LIMIT = 100
