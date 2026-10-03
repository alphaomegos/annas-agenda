package com.alphaomegos.annasagenda

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.cornerRadius
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * Asks every running widget session to read the state again.
 *
 * A Glance session outlives one call: update() on a widget whose session is
 * still alive only recomposes the content — provideGlance does not run
 * again. 0145 read the state once in provideGlance, so after a tick the
 * session kept drawing the box as it was (Eduard, 03.10). Now the content
 * reloads whenever this number moves.
 */
internal object TodayWidgetRefresh {
    val version = MutableStateFlow(0L)
    fun bump() {
        version.value = version.value + 1
    }
}

/** What one drawing of the widget needs, read in one go. */
private data class WidgetContentData(
    val header: String,
    val empty: String,
    val rows: List<WidgetTaskRow>,
    val style: WidgetStyle,
    val today: LocalDate,
)

private suspend fun loadWidgetContent(context: Context): WidgetContentData {
    val today = LocalDate.now()
    val state = runCatching { currentStateFor(context) }.getOrNull()
    val words = inAppLanguage(context)
    val locale = words.resources.configuration.locales[0]
    return WidgetContentData(
        header = words.getString(R.string.widget_today_title, formatWidgetDay(today, locale)),
        empty = words.getString(R.string.widget_nothing_today),
        rows = state?.let { todayWidgetRows(it, today, currentLocaleWeekStart()) }.orEmpty(),
        style = state?.widgetStyle ?: WidgetStyle(),
        today = today,
    )
}

class TodayTasksWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = loadWidgetContent(context)

        provideContent {
            var data by remember { mutableStateOf(first) }
            val version by TodayWidgetRefresh.version.collectAsState()
            LaunchedEffect(version) {
                data = loadWidgetContent(context)
            }

            val openToday = Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_EPOCH_DAY, data.today.toEpochDay())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            GlanceTheme {
                WidgetContent(data = data, openToday = openToday)
            }
        }
    }
}

/** Light words on a dark backing, dark on a light one; the theme's pair for SYSTEM. */
private fun widgetTextColor(style: WidgetStyle): ColorProvider = when (style.textColor) {
    WidgetTextColor.WHITE -> ColorProvider(Color.White)
    WidgetTextColor.BLACK -> ColorProvider(Color.Black)
    WidgetTextColor.SYSTEM -> DayNightColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFE6E1E5))
}

private fun widgetBackground(style: WidgetStyle): ColorProvider {
    val alpha = style.backgroundPercent / 100f
    val dark = Color(0xFF1C1B1F).copy(alpha = alpha)
    val light = Color(0xFFFFFBFE).copy(alpha = alpha)
    return when (style.textColor) {
        WidgetTextColor.WHITE -> ColorProvider(dark)
        WidgetTextColor.BLACK -> ColorProvider(light)
        WidgetTextColor.SYSTEM -> DayNightColorProvider(day = light, night = dark)
    }
}

@Composable
private fun WidgetContent(data: WidgetContentData, openToday: Intent) {
    val text = widgetTextColor(data.style)
    val checked = data.style.checkColorArgb?.let { ColorProvider(Color(it.toInt())) }
        ?: GlanceTheme.colors.primary
    val boxColors = CheckboxDefaults.colors(checkedColor = checked, uncheckedColor = text)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBackground(data.style))
            .cornerRadius(16.dp)
            .padding(12.dp),
    ) {
        Text(
            text = data.header,
            style = TextStyle(color = text, fontWeight = FontWeight.Bold, fontSize = 16.sp),
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .clickable(actionStartActivity(openToday)),
        )
        if (data.rows.isEmpty()) {
            Text(text = data.empty, style = TextStyle(color = text))
        } else {
            LazyColumn {
                items(data.rows, itemId = { it.key.hashCode().toLong() }) { row ->
                    val label = (row.time?.let { formatTaskTime(it) + "  " }.orEmpty()) + row.title
                    CheckBox(
                        checked = row.isDone,
                        onCheckedChange = actionRunCallback<ToggleTaskFromWidget>(
                            actionParametersOf(WidgetTaskKey to row.key)
                        ),
                        text = label,
                        style = TextStyle(color = text),
                        colors = boxColors,
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
        val vm = LiveAppState.viewModelOnceLoaded()

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
        // A running session redraws from the state; a sleeping one is woken.
        TodayWidgetRefresh.bump()
        TodayTasksWidget().update(context, glanceId)
    }
}

class TodayTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget()
}

/** Redraws every widget placed; called by the app when today's tasks may have changed. */
internal suspend fun refreshTodayWidgets(context: Context) {
    TodayWidgetRefresh.bump()
    runCatching { TodayTasksWidget().updateAll(context) }
}
