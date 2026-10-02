package com.alphaomegos.annasagenda

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/*
 * The home-screen widget: today's tasks in day order, ticked from the
 * widget, the header opening today in the app (agreed 02.10). Glance.
 *
 * What it shows and what a tick does are pure (TodayWidgetSupport) and
 * tested there. This file reads the state — from the open app if there is
 * one, from disk if not — and draws it.
 */

/** The extra that asks MainActivity to open a day; the value is an epoch day. */
internal const val EXTRA_OPEN_EPOCH_DAY = "com.alphaomegos.annasagenda.OPEN_EPOCH_DAY"

private val WidgetTaskKey = ActionParameters.Key<String>("task_key")

/** The state as the app has it now: the live view model's, or what is on disk. */
private suspend fun currentStateFor(context: Context): AppState? {
    LiveAppState.viewModel()?.let { vm ->
        return withContext(Dispatchers.Main) { vm.state.value }
    }
    return (AppStateStore(context.applicationContext).load() as? AppStateLoadResult.Loaded)?.state
}

class TodayTasksWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val state = runCatching { currentStateFor(context) }.getOrNull()
        val rows = state?.let { todayWidgetRows(it, today, currentLocaleWeekStart()) }.orEmpty()

        val words = inAppLanguage(context)
        val locale = words.resources.configuration.locales[0]
        val dateText = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        val header = words.getString(R.string.widget_today_title, dateText)
        val empty = words.getString(R.string.widget_nothing_today)

        val openToday = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_EPOCH_DAY, today.toEpochDay())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        provideContent {
            GlanceTheme {
                WidgetContent(header = header, empty = empty, rows = rows, openToday = openToday)
            }
        }
    }
}

@Composable
private fun WidgetContent(header: String, empty: String, rows: List<WidgetTaskRow>, openToday: Intent) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        Text(
            text = header,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            ),
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .clickable(actionStartActivity(openToday)),
        )
        if (rows.isEmpty()) {
            Text(text = empty, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
        } else {
            LazyColumn {
                items(rows, itemId = { it.key.hashCode().toLong() }) { row ->
                    val label = (row.time?.let { formatTaskTime(it) + "  " }.orEmpty()) + row.title
                    CheckBox(
                        checked = row.isDone,
                        onCheckedChange = actionRunCallback<ToggleTaskFromWidget>(
                            actionParametersOf(WidgetTaskKey to row.key)
                        ),
                        text = label,
                        style = TextStyle(color = GlanceTheme.colors.onSurface),
                        modifier = GlanceModifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * A tick from the widget. Through the open app's view model when there is
 * one — a tick written to disk under it would be written over by its next
 * autosave — and straight to disk when there is none.
 */
class ToggleTaskFromWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[WidgetTaskKey] ?: return
        val app = context.applicationContext
        val vm = LiveAppState.viewModel()

        if (vm != null) {
            withContext(Dispatchers.Main) { vm.toggleTaskFromWidget(key) }
        } else {
            val store = AppStateStore(app)
            val loaded = store.load() as? AppStateLoadResult.Loaded ?: return
            val result = stateAfterTogglingTaskFromWidget(
                state = loaded.state,
                key = key,
                today = LocalDate.now(),
                weekStart = currentLocaleWeekStart(),
                nextId = nextIdFor(loaded.state),
            ) ?: return
            store.save(stateWithIdHighWaterAtLeast(result.state, result.nextId))
            // The alarm may have been for a reminder of the task just ticked.
            scheduleNextNotification(app, result.state)
        }
        TodayTasksWidget().update(context, glanceId)
    }
}

class TodayTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget()
}

/** Redraws every widget placed; called by the app when today's tasks may have changed. */
internal suspend fun refreshTodayWidgets(context: Context) {
    runCatching { TodayTasksWidget().updateAll(context) }
}
