package com.alphaomegos.annasagenda.support

/*
 * The «Someday» count on the month's title row (30.10, Eduard): a circle with the
 * number in it, a step redder with each task waiting — sixteen shades, from pale to
 * bright. No tasks is the palest; fifteen and more, the brightest.
 *
 * The shades are even steps in OKLCH (lightness down, chroma up, the hue held on
 * red), worked out once and written down, so every phone shows the same ones. The
 * dark theme has its own ramp — from a dim maroon up to the bright red — because a
 * pale pink on a dark screen would shout loudest when nothing is waiting.
 */

/** Light theme: pale pink to bright red. */
val SOMEDAY_SHADES_LIGHT: List<Long> = listOf(
    0xFFFFEBEA, 0xFFFEE0DF, 0xFFFED4D3, 0xFFFEC8C7, 0xFFFEBDBA, 0xFFFEB0AD, 0xFFFFA4A0, 0xFFFE9792,
    0xFFFB8B85, 0xFFF97F78, 0xFFF6726B, 0xFFF3655D, 0xFFEF584F, 0xFFEB4940, 0xFFE7372F, 0xFFE32018,
)

/** Dark theme: dim maroon to bright red. */
val SOMEDAY_SHADES_DARK: List<Long> = listOf(
    0xFF493130, 0xFF543230, 0xFF5F3331, 0xFF6A3432, 0xFF753533, 0xFF7F3634, 0xFF8A3734, 0xFF953835,
    0xFF9F3935, 0xFFAA3935, 0xFFB53A35, 0xFFC03A34, 0xFFCB3A33, 0xFFD63B32, 0xFFE13B30, 0xFFEC3A2E,
)

/** The circle's colour for [count] tasks: one step per task, held at the last. */
fun somedayShade(count: Int, dark: Boolean): Long {
    val ramp = if (dark) SOMEDAY_SHADES_DARK else SOMEDAY_SHADES_LIGHT
    return ramp[count.coerceIn(0, ramp.size - 1)]
}

/** The number's colour on [shade]: black or white, whichever reads better (WCAG contrast). */
fun somedayInk(shade: Long): Long {
    val y = somedayLuminance(shade)
    val onWhite = 1.05 / (y + 0.05)
    val onBlack = (y + 0.05) / 0.05
    return if (onWhite >= onBlack) 0xFFFFFFFF else 0xFF000000
}

/** The contrast of [ink] on [shade], 1–21. */
fun somedayContrast(ink: Long, shade: Long): Double {
    val a = somedayLuminance(ink)
    val b = somedayLuminance(shade)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

/** The number in the circle: as written for the language, «99+» past ninety-nine. */
fun somedayCountLabel(count: Int, format: (Int) -> String): String =
    if (count > 99) format(99) + "+" else format(count.coerceAtLeast(0))

// The relative luminance of an sRGB colour, as WCAG 2 defines it.
internal fun somedayLuminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
        return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}
