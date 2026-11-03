package com.alphaomegos.annasagenda.support

/**
 * How far a sending has gone, in whole percent (03.11, Eduard: the backup's and a
 * track's going up shown in percent): 0 before anything is known, never past 100.
 */
fun progressPercent(done: Long, of: Long): Int =
    if (of <= 0L) 0 else ((done.coerceAtLeast(0L) * 100L) / of).coerceIn(0L, 100L).toInt()

/** Whether [percent] is worth telling again after [shown]: every five percent, and the end. */
fun progressWorthTelling(percent: Int, shown: Int): Boolean = percent >= shown + 5 || (percent == 100 && shown < 100)
