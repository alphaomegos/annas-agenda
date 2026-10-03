package com.alphaomegos.annasagenda

/** The colour of the widget's words. Stored by name. */
enum class WidgetTextColor {
    /** Dark on a light phone, light on a dark one: what the widget did at first. */
    SYSTEM,
    WHITE,
    BLACK,
}

/**
 * How the home-screen widget looks (03.10).
 *
 * [backgroundPercent] is how opaque the backing is, 0 fully transparent to
 * 100 solid. The backing is the opposite of the words — dark under white
 * words, light under black ones — so that half-transparent still reads.
 * [checkColorArgb] is the ticked box's colour; null keeps the theme's.
 *
 * The defaults are what the widget looked like in 0145, so a phone that
 * never opens the settings sees no change. A setting with a default rides
 * on the stored state without a schema step, like the other display
 * switches did before the diet made schema 6 necessary.
 */
data class WidgetStyle(
    val backgroundPercent: Int = 100,
    val textColor: WidgetTextColor = WidgetTextColor.SYSTEM,
    val checkColorArgb: Long? = null,
)

/** Anything outside 0..100 is clamped: a percentage is a percentage. */
fun normalizedWidgetStyle(style: WidgetStyle): WidgetStyle =
    style.copy(backgroundPercent = style.backgroundPercent.coerceIn(0, 100))
