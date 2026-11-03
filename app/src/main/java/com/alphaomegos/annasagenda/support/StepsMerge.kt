package com.alphaomegos.annasagenda.support

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/*
 * Steps counted once (03.11, Eduard: 18 963 in the app for a day Samsung Health
 * showed 11 104). Health Connect's daily total adds up what one app wrote for
 * several devices: Samsung Health writes the phone's and the watch's steps side by
 * side, so a walk with both counted twice. Its own deduplication is only between
 * apps. So the app reads the records themselves and counts each minute once:
 *
 * - within one app, each minute takes the device that counted most in it (the phone
 *   in the pocket and the watch on the wrist counted the same steps);
 * - between apps, each day takes the app that counted most that day (two apps
 *   counting the same walk are two opinions, not two walks).
 *
 * A record is spread over the minutes it covers, in proportion to the time.
 */

/** A Health Connect steps record: who wrote it (app), on what (device), from when to when, how many. */
data class StepChunk(val app: String, val device: String, val startSec: Long, val endSec: Long, val count: Long)

/** Each day's steps, every step counted once, by the phone's [zone]. */
fun stepsByDayOnce(chunks: List<StepChunk>, zone: ZoneId): Map<LocalDate, Int> {
    // app → minute → device → steps
    val minutes = HashMap<String, HashMap<Long, HashMap<String, Double>>>()
    for (c in chunks) {
        if (c.count <= 0) continue
        val perMinute = minutes.getOrPut(c.app) { HashMap() }
        if (c.endSec <= c.startSec) {
            val m = Math.floorDiv(c.startSec, 60L)
            perMinute.getOrPut(m) { HashMap() }.merge(c.device, c.count.toDouble(), Double::plus)
            continue
        }
        val span = (c.endSec - c.startSec).toDouble()
        var m = Math.floorDiv(c.startSec, 60L)
        val last = Math.floorDiv(c.endSec - 1, 60L)
        while (m <= last) {
            val from = maxOf(c.startSec, m * 60)
            val to = minOf(c.endSec, (m + 1) * 60)
            if (to > from) perMinute.getOrPut(m) { HashMap() }.merge(c.device, c.count * (to - from) / span, Double::plus)
            m++
        }
    }
    // Per app: each minute's busiest device, summed by day.
    val byApp = minutes.mapValues { (_, perMinute) ->
        val days = HashMap<LocalDate, Double>()
        for ((m, devices) in perMinute) {
            val day = Instant.ofEpochSecond(m * 60).atZone(zone).toLocalDate()
            days.merge(day, devices.values.max(), Double::plus)
        }
        days
    }
    // Per day: the app that counted most.
    val out = HashMap<LocalDate, Int>()
    for (days in byApp.values) for ((day, steps) in days) out[day] = maxOf(out[day] ?: 0, steps.roundToInt())
    return out
}
