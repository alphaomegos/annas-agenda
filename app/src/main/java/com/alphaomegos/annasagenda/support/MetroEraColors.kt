package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroStyle

/**
 * Line colours from the era of a style (15.10), if the user wants them: each
 * line's own colour brought to the nearest the old machine could show.
 *
 * - Windows 95: the 16 colours of its palette, less white and silver — a
 *   line in either would vanish into the white page or the grey.
 * - DOS: the 16 of CGA, less black and the blue of the panels themselves.
 * - The terminal: one colour, green, in four brightnesses — a line's own
 *   brightness picks its shade.
 *
 * Colours are 0xAARRGGBB, as stored; today's style keeps every one.
 */
fun metroEraColor(style: MetroStyle, argb: Long): Long = when (style) {
    MetroStyle.MODERN -> argb
    MetroStyle.WIN95 -> nearest(argb, WIN95_PALETTE)
    MetroStyle.DOS -> nearest(argb, DOS_PALETTE)
    MetroStyle.TERMINAL -> {
        val l = luminance(argb)
        TERMINAL_SHADES[(l * TERMINAL_SHADES.size).toInt().coerceIn(0, TERMINAL_SHADES.size - 1)]
    }
}

private val WIN95_PALETTE = listOf(
    0xFF000000, 0xFF800000, 0xFF008000, 0xFF808000, 0xFF000080, 0xFF800080, 0xFF008080,
    0xFF808080, 0xFFFF0000, 0xFF00FF00, 0xFFFFFF00, 0xFF0000FF, 0xFFFF00FF, 0xFF00FFFF,
)

private val DOS_PALETTE = listOf(
    0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFAA5500, 0xFFAAAAAA, 0xFF555555,
    0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF,
)

// Darkest to brightest.
private val TERMINAL_SHADES = listOf(0xFF168A3A, 0xFF22C553, 0xFF33FF66, 0xFF9DFFB8)

private fun channels(argb: Long) = Triple((argb shr 16 and 0xFF).toInt(), (argb shr 8 and 0xFF).toInt(), (argb and 0xFF).toInt())

private fun nearest(argb: Long, palette: List<Long>): Long {
    val (r, g, b) = channels(argb)
    return palette.minByOrNull { c ->
        val (pr, pg, pb) = channels(c)
        // Weighted as the eye weighs them.
        2 * (r - pr) * (r - pr) + 4 * (g - pg) * (g - pg) + 3 * (b - pb) * (b - pb)
    } ?: argb
}

private fun luminance(argb: Long): Double {
    val (r, g, b) = channels(argb)
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
}
