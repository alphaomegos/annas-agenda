package com.alphaomegos.annasagenda

/**
 * Which set of colours the app draws itself in.
 *
 * [SYSTEM] is not "light": it follows whatever the phone is doing, which on a
 * phone with a scheduled night mode means the app changes on its own in the
 * evening. The other two are the user overruling that, and they are worth
 * having — the phone-wide setting is a blunt instrument, and somebody who
 * wants this one app light at night has no other way to say so.
 */
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Reads a stored mode, falling back to following the phone.
 *
 * A payload written by a later version can name a mode this one has never
 * heard of. Following the phone is the safe answer to that: it is what the app
 * did before anyone could choose, so the worst case is the setting appearing
 * not to have been saved, rather than a crash on launch.
 */
fun parseAppThemeMode(raw: String): AppThemeMode =
    runCatching { AppThemeMode.valueOf(raw) }.getOrElse { AppThemeMode.SYSTEM }

/**
 * Whether to draw dark, given the choice and what the phone is currently doing.
 *
 * Split out from the theme composable so the one decision in all of this that
 * can be got wrong is a function with a truth table rather than a conditional
 * inside a @Composable that no terminal will ever run.
 */
fun isDarkTheme(mode: AppThemeMode, systemInDarkTheme: Boolean): Boolean = when (mode) {
    AppThemeMode.SYSTEM -> systemInDarkTheme
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
}
