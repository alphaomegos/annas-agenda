package com.alphaomegos.annasagenda.model

import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

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

/**
 * Which colours the app is drawn in (19.10) — beside light or dark, not
 * instead of it.
 *
 * [CLASSIC] is the app as it has always been: its own green on the buttons,
 * and every other colour Material's stock one — the lavender surfaces and
 * containers that go with a purple app, not with this one. It stays the
 * default, so an update changes nothing until the user asks (Eduard: the user
 * always has the choice; a new look is never pushed on anyone).
 *
 * [GREEN] is the whole scheme grown from the same green the way Material 3
 * does it (a tonal palette, Material Color Utilities' TonalSpot): surfaces,
 * containers, outlines and the third colour all from one family.
 *
 * [WALLPAPER] takes the colours Android 12 and later draw from the
 * wallpaper. A phone before Android 12 has none to give, and gets [GREEN].
 */
enum class AppPalette {
    CLASSIC,
    GREEN,
    WALLPAPER,
}

/** Reads a stored palette; a name this version does not know is [AppPalette.CLASSIC], the look nobody had to choose. */
fun parseAppPalette(raw: String): AppPalette =
    runCatching { AppPalette.valueOf(raw) }.getOrElse { AppPalette.CLASSIC }
