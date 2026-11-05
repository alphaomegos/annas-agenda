package com.alphaomegos.annasagenda.app

import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import java.time.ZoneId
import com.alphaomegos.annasagenda.support.stepsByDayOnce
import com.alphaomegos.annasagenda.support.StepChunk
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period
import com.alphaomegos.annasagenda.support.runCatchingCancellable

/** What one read from Health Connect brought: each day's total, and the apps it came from. */
internal data class StepsRead(val days: Map<LocalDate, Int>, val sources: Set<String>)

/** Whether this phone has a Health Connect to read steps from (19.10). */
enum class StepsAvailability {
    /** Android 8 and older, or a work profile: there is none. */
    NONE,

    /** Android 9–13 without the Health Connect app, or with an old one: it can be installed or updated. */
    NEEDS_INSTALL,

    READY,
}

/**
 * Steps from Health Connect (19.10) — read, never written.
 *
 * Samsung Health puts there what the phone and the Galaxy Watch counted (the
 * watch reaching the phone in its own time); from Android 14 the phone also
 * counts its own steps into it once any app may read them.
 *
 * Health Connect's daily total proved to count a walk twice (03.11, Eduard: 18 963
 * against Samsung Health's 11 104): it deduplicates between apps, not between the
 * devices one app wrote for. So the records are read and each minute counted once
 * — the busiest device within an app, the busiest app within a day
 * (support/StepsMerge.kt). The daily total stays as the fallback.
 *
 * Nothing here is touched below Android 9, where Health Connect does not
 * exist: its library wants Android 8 and the app starts at 7 (the manifest
 * overrides the library's minimum on that understanding).
 */
internal object HealthConnectSteps {

    val readPermission: String
        get() = HealthPermission.getReadPermission(StepsRecord::class)

    /**
     * Health Connect's own screen asking the user to let the app read steps;
     * what comes back is the set of permissions granted. Only where
     * [availability] is READY.
     */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /** Health Connect in the store, for Android 9–13 without it or with an old one. */
    const val STORE_LINK = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"

    fun availability(context: Context): StepsAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return StepsAvailability.NONE
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> StepsAvailability.READY
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> StepsAvailability.NEEDS_INSTALL
            else -> StepsAvailability.NONE
        }
    }

    /** Whether the user let the app read steps; false wherever there is no Health Connect. */
    suspend fun isAllowed(context: Context): Boolean =
        availability(context) == StepsAvailability.READY &&
            readPermission in HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    /**
     * The total of each of the [days] days up to [today], by the phone's own
     * calendar — every day of them, a day Health Connect has nothing for as 0
     * — and the apps the steps came from (19.10: shown under the chart, so a
     * read that found nothing says so, and says from where).
     */
    suspend fun readDays(context: Context, today: LocalDate, days: Int): StepsRead {
        val from = today.minusDays(days - 1L)
        val zone = ZoneId.systemDefault()
        val once = runCatchingCancellable { readOnce(context, from, today, zone) }.getOrNull()
        if (once != null) {
            val counts = (0 until days).associate { i ->
                val day = from.plusDays(i.toLong())
                day to (once.first[day] ?: 0)
            }
            return StepsRead(counts, once.second)
        }
        val rows = HealthConnectClient.getOrCreate(context).aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        val byDay = rows.associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
        val counts = (0 until days).associate { i ->
            val day = from.plusDays(i.toLong())
            day to (byDay[day] ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
        val sources = rows.flatMap { row -> row.result.dataOrigins.map { it.packageName } }.toSet()
        return StepsRead(counts, sources)
    }

    // The records from the first day to today, page by page, each step once; and the apps.
    private suspend fun readOnce(context: Context, from: LocalDate, today: LocalDate, zone: ZoneId): Pair<Map<LocalDate, Int>, Set<String>> {
        val client = HealthConnectClient.getOrCreate(context)
        val range = TimeRangeFilter.between(from.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant())
        val chunks = ArrayList<StepChunk>()
        var page: String? = null
        do {
            val answer = client.readRecords(ReadRecordsRequest(StepsRecord::class, range, pageSize = 5000, pageToken = page))
            for (r in answer.records) {
                val device = r.metadata.device?.let { "${it.type}/${it.manufacturer.orEmpty()}/${it.model.orEmpty()}" }.orEmpty()
                chunks += StepChunk(r.metadata.dataOrigin.packageName, device, r.startTime.epochSecond, r.endTime.epochSecond, r.count)
            }
            page = answer.pageToken
        } while (page != null)
        val byDay = stepsByDayOnce(chunks, zone).filterKeys { !it.isBefore(from) && !it.isAfter(today) }
        return byDay to chunks.map { it.app }.toSet()
    }
}
