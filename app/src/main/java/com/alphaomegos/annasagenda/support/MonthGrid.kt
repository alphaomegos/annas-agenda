package com.alphaomegos.annasagenda.support

/**
 * A month cell's height in whole pixels (25.10): what is left of [availablePx]
 * after the gaps between [rows] rows, shared out and rounded down — so the
 * rows and gaps, each laid out in whole pixels, never come to more than the
 * screen has.
 */
fun monthCellHeightPx(availablePx: Int, gapPx: Int, rows: Int): Int =
    ((availablePx - gapPx * (rows - 1)).coerceAtLeast(0)) / rows.coerceAtLeast(1)
