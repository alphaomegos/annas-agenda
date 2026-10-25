package com.alphaomegos.annasagenda.app

import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period

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
 * counts its own steps into it once any app may read them. Health Connect
 * adds them all up into one total a day, by the order of sources the user set
 * there — so the app asks for that total, day by day, and never sums records
 * itself: steps counted by both the phone and the watch would count twice.
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
     * calendar: every day of them, a day Health Connect has nothing for as 0.
     */
    suspend fun readDays(context: Context, today: LocalDate, days: Int): Map<LocalDate, Int> {
        val from = today.minusDays(days - 1L)
        val rows = HealthConnectClient.getOrCreate(context).aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        val byDay = rows.associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
        return (0 until days).associate { i ->
            val day = from.plusDays(i.toLong())
            day to (byDay[day] ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
    }
}
