package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Nothing the view model's construction touches may be declared below it.
 *
 * Property initialisers run in declaration order, and the `init` block runs in
 * that same order with them. What is easy to miss — and what cost a release
 * build and three wrong diagnoses — is that the coroutine `init` starts runs
 * there too: `viewModelScope` is `Dispatchers.Main.immediate`, a view model is
 * built on the main thread, and an immediate dispatcher does not post. It runs
 * the body on the spot, inside the constructor.
 *
 * So a field declared a thousand lines below `init` does not exist yet when
 * that body first reaches it. `_state` was exactly that, and the crash was
 * `MutableStateFlow.setValue because ... _state is null` — rare, random, and
 * invisible, because the body normally suspends on the file read before it
 * gets there and the constructor normally finishes first. Normally.
 *
 * This reads the source rather than running anything, because the failure it
 * guards against is a race: a test that constructs a view model and hopes to
 * lose the race is a test that passes for the wrong reason almost every time.
 *
 * It follows the calls out of `init` as well, one function into the next, so
 * that moving the problem into a helper does not move it out of sight.
 */
class ViewModelInitOrderTest {

    @Test
    fun everythingTheInitBlockReachesIsDeclaredAboveIt() {
        val lines = viewModelSource().readLines()

        val initStart = lines.indexOfFirst { it.trimEnd() == "    init {" }
        assertTrue("AppViewModel has no init block any more", initStart >= 0)

        val properties = declaredProperties(lines)
        val functions = memberFunctions(lines)

        val reached = mutableSetOf<String>()
        val offenders = mutableListOf<String>()

        fun walk(body: List<String>, via: String) {
            val words = body.flatMap { Regex("""\w+""").findAll(it).map { m -> m.value }.toList() }

            words.forEach { word ->
                properties[word]?.let { declaredAt ->
                    if (declaredAt > initStart) {
                        offenders += "$word is declared at line ${declaredAt + 1}, " +
                            "below the init block at line ${initStart + 1} (reached $via)"
                    }
                }

                if (word in functions && reached.add(word)) {
                    walk(blockAt(lines, functions.getValue(word)), "$via -> $word")
                }
            }
        }

        walk(blockAt(lines, initStart), "init")

        assertTrue(
            "Construction touches something that does not exist yet. Move the " +
                "declaration above the init block:\n" + offenders.distinct().joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /** Property name to the line it is declared on. */
    private fun declaredProperties(lines: List<String>): Map<String, Int> =
        lines.withIndex()
            .mapNotNull { (index, line) ->
                Regex("""^    (?:private |internal )?va[lr] (\w+)""")
                    .find(line)
                    ?.groupValues
                    ?.get(1)
                    ?.let { it to index }
            }
            .toMap()

    /** Member function name to the line it is declared on. */
    private fun memberFunctions(lines: List<String>): Map<String, Int> =
        lines.withIndex()
            .mapNotNull { (index, line) ->
                Regex("""^    (?:private |internal )?(?:suspend )?fun (\w+)""")
                    .find(line)
                    ?.groupValues
                    ?.get(1)
                    ?.let { it to index }
            }
            .toMap()

    /**
     * The braced block starting on [start], by depth.
     *
     * A single-expression function has no block; it comes back as its own
     * line, which is the right answer for a body that is one expression.
     */
    private fun blockAt(lines: List<String>, start: Int): List<String> {
        if (!lines[start].contains("{")) return listOf(lines[start])

        var depth = 0
        for (i in start until lines.size) {
            depth += lines[i].count { it == '{' } - lines[i].count { it == '}' }
            if (depth == 0) return lines.subList(start, i + 1)
        }

        return lines.subList(start, lines.size)
    }

    private fun viewModelSource(): File =
        File(mainSourceRoot(), "com/alphaomegos/annasagenda/app/AppViewModel.kt")
            .also { assertTrue("AppViewModel.kt is not where it was", it.isFile) }
}
