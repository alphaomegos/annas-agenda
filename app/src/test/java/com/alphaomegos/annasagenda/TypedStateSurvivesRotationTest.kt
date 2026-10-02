package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * State that holds what the user did lives in `rememberSaveable`.
 *
 * Turning the phone throws the composition away and builds it again. Whatever
 * sat in a plain `remember` is gone; whatever sat in `rememberSaveable` comes
 * back. This project has lost typed input to that six times — 0043, 0044,
 * 0055, 0089, 0090, and the custom range dates in 0116, fixed in 0129 — and
 * every time it was found by somebody turning a phone, weeks later.
 *
 * So the rule is not "these screens were fixed" but **every mutable state in a
 * plain `remember` is named here, with the reason it may be lost**: a dropdown
 * that is open, a gesture in flight, a clock tick, a cache. Anything else
 * belongs in `rememberSaveable`. A new one that is neither is the next
 * rotation bug, and this test is where it shows up — on the day it is written
 * rather than on a phone.
 *
 * Read from the source on purpose, like [TextFieldCapitalizationTest]: a UI
 * test can only turn the phone on the screens somebody remembered to test.
 */
class TypedStateSurvivesRotationTest {

    /**
     * Plain-`remember` state that may be lost on rotation, with the reason.
     *
     * Keyed by file and variable name rather than by line, because a line
     * number stops being true the next time somebody adds a line above it.
     */
    private val mayBeLostOnRotation = mapOf(
        "MainMenuScreen.kt:dataMenuExpanded" to
            "a dropdown being open; turning the phone closes it, nothing typed is in it",
        "MainMenuScreen.kt:items" to
            "a working copy of the order while rearranging. Every move is written " +
            "through to the saved order as it happens — drag end, arrow tap, hide — so " +
            "a rotation rebuilds it from there",
        "MainMenuScreen.kt:draggingIndex" to "a drag in flight; a rotation ends the drag",
        "MainMenuScreen.kt:draggingOffsetY" to "a drag in flight; a rotation ends the drag",
        "MainMenuScreen.kt:draggingOffsetX" to "a drag in flight; a rotation ends the drag",
        "MainMenuScreen.kt:swipingItemId" to "a swipe in flight; a rotation ends the swipe",
        "MainMenuScreen.kt:swipeOffsetX" to "a swipe in flight; a rotation ends the swipe",
        "NewTaskScreen.kt:draftLoaded" to
            "gates this composition's own effects: the autosave of the draft must start " +
            "only after this composition has read the draft, so it has to begin false " +
            "every time. The fields themselves are saveable",
        "UndoneTasksScreen.kt:debt" to
            "a snapshot computed by an effect on entry, so that ticking a task off does " +
            "not pull it from under the finger; it is recomputed after a rotation",
        "UndoneTasksScreen.kt:horizonMenuOpen" to "a dropdown being open",
        "MediaLibraryControls.kt:flashed" to "a highlight that fades by itself after a tap",
        "MediaLibraryList.kt:offsetX" to
            "where the wall of covers is panned to — a view position, like a scroll " +
            "offset; the wall starts again from the middle",
        "MediaLibraryList.kt:offsetY" to "as offsetX",
        "MediaLibraryList.kt:viewportWidthPx" to "measured on layout, and the layout changes on rotation",
        "MediaLibraryList.kt:viewportHeightPx" to "measured on layout, and the layout changes on rotation",
        "MediaLibraryList.kt:coverCache" to "decoded bitmaps, a cache; loaded again on demand",
        "MediaLibraryList.kt:expanded" to "a dropdown being open",
        "MediaLibraryScreen.kt:pendingDelete" to
            "a delete confirmation for one item. Turning the phone closes it and loses " +
            "nothing: the item is still there and one tap asks again. The item itself " +
            "does not go into a Bundle",
        "ReadingSessionScreen.kt:nowMs" to "a clock tick, refreshed every second",
        "ReadingSessionScreen.kt:errorText" to
            "a message about the last tap on Save, recomputed by the next one; the three " +
            "numbers typed in that dialog are saveable",
    )

    @Test
    fun everyPlainRememberedStateIsNamedWithAReason() {
        val offenders = plainRememberedStates()
            .filterNot { it.key in mayBeLostOnRotation }
            .map { "${it.key} (line ${it.line})" }

        assertTrue(
            "These hold mutable state in a plain remember, which turning the phone " +
                "throws away. If it is something the user did — typed, ticked, picked, a " +
                "dialog they opened — use rememberSaveable (a value that does not go into " +
                "a Bundle as it is gets a Saver; see fieldIdSetSaver). If losing it is " +
                "genuinely fine, name it in mayBeLostOnRotation with the reason:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /**
     * The list above cannot keep excusing something that is gone. A stale entry
     * is how an exception outlives its reason and quietly covers the next
     * variable that happens to get the same name.
     */
    @Test
    fun everyNamedExceptionStillExists() {
        val found = plainRememberedStates().map { it.key }.toSet()
        val stale = mayBeLostOnRotation.keys - found

        assertTrue(
            "These are excused in mayBeLostOnRotation but no longer exist; " +
                "remove them:\n" + stale.joinToString("\n"),
            stale.isEmpty(),
        )
    }

    /**
     * Every `remember { mutable…StateOf` the crude search finds has to be one
     * the named search understood. Otherwise a shape this file does not know —
     * a state passed straight into a call, keys with brackets inside them —
     * would simply not be looked at, and the rule above would pass by finding
     * nothing.
     */
    @Test
    fun noRememberedStateEscapesTheSearch() {
        mainSourceFiles().forEach { file ->
            val text = file.readText()
            assertEquals(
                "${file.name}: a remembered mutable state this test cannot name",
                rawSites(text).size,
                namedSites(text).size,
            )
        }
    }

    /** The searches themselves, on the shapes they have to handle. */
    @Test
    fun theSearchSeesEveryShapeAndNothingElse() {
        val source = """
            var a by remember { mutableStateOf(false) }
            val b = remember { mutableIntStateOf(0) }
            val c = remember(key) {
                mutableStateOf(setOf("x"))
            }
            val d = remember(items) {
                mutableStateMapOf<String, Int>()
            }
            val e = remember { mutableStateListOf<Int>() }
            var saved by rememberSaveable { mutableStateOf("") }
            val plain = remember { LocalDate.now() }
            // var commented by remember { mutableStateOf(1) }
        """.trimIndent()

        assertEquals(listOf("a", "b", "c", "d", "e"), namedSites(source).map { it.second })
        assertEquals(5, rawSites(source).size)
    }

    /* ---------------- the search ---------------- */

    private data class Site(val key: String, val line: Int)

    private fun plainRememberedStates(): List<Site> =
        mainSourceFiles().flatMap { file ->
            namedSites(file.readText()).map { (line, name) -> Site("${file.name}:$name", line) }
        }

    /** `remember` (with or without keys) whose lambda starts with a mutable state. */
    private val raw = Regex(
        """\bremember\s*(\([^()]*\))?\s*\{\s*(mutable\w*StateOf|mutableStateListOf|mutableStateMapOf)\b"""
    )

    /** The same, as the right-hand side of a `val` or `var`, capturing the name. */
    private val named = Regex(
        """\b(?:val|var)\s+(\w+)\s*(?:by|=)\s*remember\s*(\([^()]*\))?\s*\{\s*""" +
            """(mutable\w*StateOf|mutableStateListOf|mutableStateMapOf)\b"""
    )

    private fun rawSites(text: String): List<Int> =
        raw.findAll(text).map { lineOf(text, it.range.first) }
            .filterNot { isCommentLine(text.lines()[it - 1]) }
            .toList()

    private fun namedSites(text: String): List<Pair<Int, String>> =
        named.findAll(text).map { lineOf(text, it.range.first) to it.groupValues[1] }
            .filterNot { (line, _) -> isCommentLine(text.lines()[line - 1]) }
            .toList()

    private fun lineOf(text: String, offset: Int): Int = text.take(offset).count { it == '\n' } + 1
}
