package com.alphaomegos.annasagenda

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alphaomegos.annasagenda.util.appBackgroundScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/*
 * The Android half of the notifications. Everything about *what* and *when*
 * is in NotificationRules.kt and tested there; this file only sets one alarm
 * for the next minute something is due, and when it fires posts what is due
 * and sets the next one. AlarmManager, no libraries.
 *
 * The alarm is exact where the phone allows it (USE_EXACT_ALARM from Android
 * 13, granted on install; SCHEDULE_EXACT_ALARM before that) and inexact
 * otherwise — a summary a few minutes late beats none.
 */

internal const val CHANNEL_SUMMARY = "daily_summary"
internal const val CHANNEL_REMINDERS = "task_reminders"

private const val EXTRA_AT_MINUTE = "com.alphaomegos.annasagenda.AT_MINUTE"
private const val ALARM_REQUEST_CODE = 4100
private const val SUMMARY_NOTIFICATION_ID = 4101

/** How many of today's tasks a summary spells out before "and N more". */
private const val SUMMARY_MAX_LINES = 6

/** A local minute as a number, independent of the zone: the alarm extra. */
private fun LocalDateTime.toLocalEpochMinute(): Long = toEpochSecond(ZoneOffset.UTC) / 60

private fun localDateTimeOfEpochMinute(minute: Long): LocalDateTime =
    LocalDateTime.ofEpochSecond(minute * 60, 0, ZoneOffset.UTC)

internal fun ensureNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val nm = context.getSystemService(NotificationManager::class.java) ?: return
    nm.createNotificationChannel(
        NotificationChannel(
            CHANNEL_SUMMARY,
            context.getString(R.string.notif_channel_summary),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
    )
    nm.createNotificationChannel(
        NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.notif_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        )
    )
}

/** Android 13 asks the user before an app may post anything; older phones do not. */
internal fun canPostNotifications(context: Context): Boolean =
    (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun alarmIntent(context: Context, at: LocalDateTime?): PendingIntent {
    val intent = Intent(context, NotificationAlarmReceiver::class.java)
    if (at != null) intent.putExtra(EXTRA_AT_MINUTE, at.toLocalEpochMinute())
    return PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * The one alarm, set for the next minute something is due after [after], or
 * cancelled when the settings ask for nothing.
 *
 * With something switched on but nothing due in the days looked at — the
 * next timed task is a week away — the alarm is set a day ahead anyway, with
 * no minute in it: firing, it only looks again.
 */
internal fun scheduleNextNotification(
    context: Context,
    state: AppState,
    after: LocalDateTime = LocalDateTime.now(),
) {
    val am = context.getSystemService(AlarmManager::class.java) ?: return
    val settings = state.notifications
    val anythingOn = settings.summaryMinutes.isNotEmpty() || settings.reminderLeadMinutes != null
    if (!anythingOn) {
        am.cancel(alarmIntent(context, null))
        return
    }

    val next = nextNotificationAt(state, after, currentLocaleWeekStart())
    val fireAt = next ?: after.plusDays(1)
    val millis = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val pending = alarmIntent(context, next)

    val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
    try {
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    } catch (e: SecurityException) {
        // The exact-alarm permission was taken away between the check and the call.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
    }
}

/** Reads the saved state and sets the alarm from it: after a reboot, an update, a clock change. */
internal fun rescheduleNotificationsFromDisk(context: Context, onDone: () -> Unit = {}) {
    val app = context.applicationContext
    appBackgroundScope.launch {
        try {
            val loaded = AppStateStore(app).load() as? AppStateLoadResult.Loaded
            if (loaded != null) scheduleNextNotification(app, loaded.state)
        } catch (e: Throwable) {
            // Nothing to show for it, and a crash in a boot receiver is worse.
        } finally {
            onDone()
        }
    }
}

/**
 * The context to read the notification's words from: in the app's own
 * language, not the phone's. From Android 13 the system already gives the
 * application that language; before it, AppCompat keeps it and the
 * application context does not know.
 */
internal fun inAppLanguage(context: Context): Context {
    if (Build.VERSION.SDK_INT >= 33) return context
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return context
    val config = android.content.res.Configuration(context.resources.configuration)
    config.setLocale(locales[0])
    return context.createConfigurationContext(config)
}

private fun openAppIntent(context: Context): PendingIntent? {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
    launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

@SuppressLint("MissingPermission") // checked by canPostNotifications
private fun postSummary(context: Context, summary: DailySummary) {
    if (!canPostNotifications(context)) return

    val lines = summary.today.map { line ->
        val time = line.time?.let { formatTaskTime(it) + " " }.orEmpty()
        (if (line.isDone) "✓ " else "") + time + line.title
    }
    val inbox = NotificationCompat.InboxStyle()
    lines.take(SUMMARY_MAX_LINES).forEach { inbox.addLine(it) }
    if (lines.size > SUMMARY_MAX_LINES) {
        inbox.addLine(context.getString(R.string.notif_summary_more, lines.size - SUMMARY_MAX_LINES))
    }
    if (summary.debts > 0) inbox.addLine(context.getString(R.string.notif_summary_debts, summary.debts))

    val title = if (summary.today.isEmpty()) {
        context.getString(R.string.notif_summary_title_debts_only)
    } else {
        context.getString(R.string.notif_summary_title, summary.today.size)
    }
    val text = lines.firstOrNull() ?: context.getString(R.string.notif_summary_debts, summary.debts)

    val n = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(inbox)
        .setContentIntent(openAppIntent(context))
        .setAutoCancel(true)
        .build()
    // One id: a newer summary replaces the one before it rather than piling up.
    NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, n)
}

@SuppressLint("MissingPermission") // checked by canPostNotifications
private fun postReminder(context: Context, event: ReminderEvent) {
    if (!canPostNotifications(context)) return
    val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(event.title)
        .setContentText(context.getString(R.string.notif_reminder_at, formatTaskTime(event.time)))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(openAppIntent(context))
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(event.taskKey.hashCode(), n)
}

/**
 * The alarm went off: post what falls between the minute it was set for and
 * now — a phone in deep sleep may deliver it a few minutes late — and set
 * the next one.
 */
class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        val atMinute = intent.getLongExtra(EXTRA_AT_MINUTE, -1L)

        appBackgroundScope.launch {
            try {
                val state = (AppStateStore(app).load() as? AppStateLoadResult.Loaded)?.state ?: return@launch
                val now = LocalDateTime.now().withSecond(0).withNano(0)
                val weekStart = currentLocaleWeekStart()
                var after = now

                if (atMinute >= 0) {
                    val at = localDateTimeOfEpochMinute(atMinute)
                    val due = upcomingNotificationEvents(state, at.minusMinutes(1), weekStart)
                        .filter { !it.at.isAfter(maxOf(now, at)) }
                    val words = inAppLanguage(app)
                    ensureNotificationChannels(words)
                    // At most one summary, however many were slept through.
                    if (due.any { it is SummaryEvent }) {
                        dailySummaryOf(state, now.toLocalDate(), weekStart)?.let { postSummary(words, it) }
                    }
                    due.filterIsInstance<ReminderEvent>()
                        .filter { reminderStillDue(state, it, weekStart) }
                        .forEach { postReminder(words, it) }
                    after = maxOf(now, at)
                }
                scheduleNextNotification(app, state, after)
            } catch (e: Throwable) {
                // A receiver that throws is killed with the process; nothing to tell anybody.
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms do not survive a reboot, and a clock or zone change moves every one of them. */
class NotificationRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        rescheduleNotificationsFromDisk(context) { pending.finish() }
    }
}
