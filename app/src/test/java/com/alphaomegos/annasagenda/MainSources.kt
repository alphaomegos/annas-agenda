package com.alphaomegos.annasagenda

import java.io.File

/**
 * The app's own Kotlin sources, for the tests that read the code instead of
 * running it.
 *
 * Those tests answer questions no assertion about behaviour can: "is there
 * anywhere in the app that still does X?" A running test can only visit the
 * screens somebody remembered to write a test for, and the whole point of
 * these rules is the place nobody remembered.
 *
 * They belong in src/test and nowhere else. There is no source tree on a
 * phone, so a source-reading test in src/sharedTest would compile into the
 * instrumented run and fail there for a reason that has nothing to do with the
 * app.
 */
internal fun mainSourceFiles(): List<File> =
    mainSourceRoot()
        .walkTopDown()
        .filter { it.isFile && it.name.endsWith(".kt") }
        .toList()

/**
 * Found by looking rather than by being told, because the working directory
 * differs between Gradle, an IDE and a bare JUnit run, and a hard-coded path
 * makes a test that passes by finding nothing.
 */
internal fun mainSourceRoot(): File =
    listOf("src/main/java", "app/src/main/java", "../app/src/main/java")
        .map { File(it) }
        .firstOrNull { it.isDirectory }
        ?: error("Cannot find src/main/java from ${File("").absolutePath}")

/**
 * The app's resource tree, for the tests that read what the app says rather
 * than what it does.
 *
 * Found by looking, for the same reason [mainSourceRoot] is: the working
 * directory differs between Gradle, an IDE and a bare JUnit run, and a
 * hard-coded path makes a test that passes by finding nothing.
 */
internal fun mainResRoot(): File =
    listOf("src/main/res", "app/src/main/res", "../app/src/main/res")
        .map { File(it) }
        .firstOrNull { it.isDirectory }
        ?: error("Cannot find src/main/res from ${File("").absolutePath}")

/**
 * One string resource's text, exactly as the XML holds it.
 *
 * Deliberately shallow: no entity decoding, no unescaping. The tests that use
 * this ask whether a word appears, and every word they look for survives the
 * XML as it is written.
 */
internal fun stringResourceText(dir: String, name: String): String? {
    val pattern = Regex("""<string name="""" + Regex.escape(name) + """">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    return File(mainResRoot(), dir)
        .listFiles { f -> f.isFile && f.name.endsWith(".xml") }
        .orEmpty()
        .firstNotNullOfOrNull { pattern.find(it.readText())?.groupValues?.get(1) }
}

/**
 * True for a line that is only a comment.
 *
 * A rule about what the code does should not be broken by a sentence
 * explaining that rule. The first version of the number-formatting check
 * failed on the KDoc that describes it, which is funny once and misleading
 * afterwards.
 *
 * Deliberately shallow: it does not track whether a block comment is still
 * open, because every comment in this project starts its lines with a marker.
 * A line of real code that only looks like a comment does not exist here, and
 * if it ever does, the cost is one missed finding rather than a false one.
 */
internal fun isCommentLine(line: String): Boolean {
    val t = line.trimStart()
    return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
}
