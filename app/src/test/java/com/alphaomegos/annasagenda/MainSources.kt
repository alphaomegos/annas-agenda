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
