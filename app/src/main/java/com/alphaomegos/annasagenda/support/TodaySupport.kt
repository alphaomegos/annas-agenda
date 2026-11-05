package com.alphaomegos.annasagenda.support

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * How long, at [now], until [today] is over — and a second more, so the new
 * day has surely begun when the wait ends (05.11, the internals review).
 * Nought and the second when it is over already.
 */
fun millisUntilTomorrow(now: LocalDateTime, today: LocalDate): Long =
    Duration.between(now, today.plusDays(1).atStartOfDay()).toMillis().coerceAtLeast(0L) + 1_000L
