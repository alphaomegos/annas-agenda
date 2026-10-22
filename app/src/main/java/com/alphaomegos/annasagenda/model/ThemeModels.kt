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
 * [GREEN] is the whole scheme grown from the app's green the way Material 3
 * does it (a tonal palette, Material Color Utilities' TonalSpot): surfaces,
 * containers, outlines and the third colour all from one family. It is the
 * default, for a phone updating as for a new one (Eduard, 19.10: a new look
 * comes on at once, and the old one stays a tap away).
 *
 * [CLASSIC] is that old one, the app as it was until 0289: its own green on
 * the buttons, and every other colour Material's stock one — the lavender
 * surfaces and containers that go with a purple app, not with this one.
 *
 * [WALLPAPER] takes the colours Android 12 and later draw from the
 * wallpaper. A phone before Android 12 has none to give, and gets [GREEN].
 */
enum class AppPalette {
    CLASSIC,
    GREEN,
    WALLPAPER,
}

/**
 * Reads a stored palette. A name this version does not know (a later
 * version's palette, restored here) is [AppPalette.GREEN], the default —
 * the look of this version, not the one it replaced.
 */
fun parseAppPalette(raw: String): AppPalette =
    runCatching { AppPalette.valueOf(raw) }.getOrElse { AppPalette.GREEN }

/**
 * How Back looks in a screen's top bar (19.10).
 *
 * [ARROW], the default: the arrow Material and Apple both put there, which
 * TalkBack still reads as «Back». Half the app's screens had it already and
 * half had the word, so the same button looked like two different things.
 *
 * [WORD] is the word «Back» as a text button, the way most of the app's
 * screens drew it until now — a tap away for whoever prefers it (Eduard: a
 * new look comes on at once, the old one stays a choice).
 */
enum class BackStyle {
    ARROW,
    WORD,
}

/** Reads a stored style; one this version does not know is the default, the arrow. */
fun parseBackStyle(raw: String): BackStyle =
    runCatching { BackStyle.valueOf(raw) }.getOrElse { BackStyle.ARROW }
