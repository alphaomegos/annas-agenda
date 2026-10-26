package com.alphaomegos.annasagenda

import androidx.glance.unit.ColorProvider
import androidx.glance.layout.fillMaxWidth
import androidx.glance.appwidget.LinearProgressIndicator
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import java.text.NumberFormat
import java.time.LocalDate

/** What the steps widget shows: today's steps, yesterday's, the record, in the tasks widget's style. */
private data class StepsWidgetData(
    val today: String,
    // The second line as chosen: yesterday's or the week's average as text,
    // or a bar towards the goal, or nothing.
    val line: String?,
    val progress: Float?,
    val record: String?,
    val style: WidgetStyle,
)

private suspend fun loadStepsWidget(context: Context): StepsWidgetData {
    val state = runCatching { currentStateFor(context) }.getOrNull()
    val words = inAppLanguage(context)
    val locale = words.resources.configuration.locales[0]
    val count = NumberFormat.getIntegerInstance(locale)
    val day = LocalDate.now()
    val days = state?.stepDays.orEmpty().associate { it.date to it.steps }
    val best = stepRecords(state?.stepDays.orEmpty()).firstOrNull()
    val todaySteps = days[day] ?: 0
    val line = state?.stepsWidgetLine ?: StepsWidgetLine.YESTERDAY
    return StepsWidgetData(
        today = count.format(todaySteps),
        line = when (line) {
            StepsWidgetLine.YESTERDAY -> words.getString(R.string.steps_widget_yesterday, count.format(days[day.minusDays(1)] ?: 0))
            StepsWidgetLine.WEEK_AVERAGE -> words.getString(R.string.steps_widget_week_average, count.format(stepsWeekAverage(state?.stepDays.orEmpty(), day)))
            StepsWidgetLine.GOAL_BAR, StepsWidgetLine.NONE -> null
        },
        progress = if (line == StepsWidgetLine.GOAL_BAR) stepsGoalProgress(todaySteps, state?.stepsGoal ?: 10_000) else null,
        record = if (state?.stepsWidgetRecord != false) best?.let { words.getString(R.string.steps_widget_record, count.format(it.steps)) } else null,
        style = state?.widgetStyle ?: WidgetStyle(),
    )
}

/**
 * Today's steps on the home screen (25.10): the count large, yesterday's and
 * the record under it, in the same style as the tasks widget — its
 * background's transparency and its text colour, set in Settings → Widget.
 * A tap opens the app. Redrawn with the tasks widget and every half hour.
 */
class StepsWidget : GlanceAppWidget() {
    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) =
        showWidgetError(context, appWidgetId, throwable, R.string.widget_error_steps)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = loadStepsWidget(context)
        provideContent {
            var data by remember { mutableStateOf(first) }
            val version by TodayWidgetRefresh.version.collectAsState()
            LaunchedEffect(version) { data = loadStepsWidget(context) }
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val text = widgetTextColor(data.style)
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(widgetBackground(data.style))
                        .cornerRadius(16.dp)
                        .padding(12.dp)
                        .clickable(actionStartActivity(open)),
                ) {
                    // No title and no figure (25.10, Eduard): the number alone, large.
                    Text(text = data.today, style = TextStyle(color = text, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                    data.line?.let { Text(text = it, style = TextStyle(color = text, fontSize = 12.sp)) }
                    data.progress?.let { p ->
                        LinearProgressIndicator(
                            progress = p,
                            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
                            color = ColorProvider(GOAL_GREEN),
                            backgroundColor = ColorProvider(GOAL_GREEN.copy(alpha = 0.25f)),
                        )
                    }
                    data.record?.let { Text(text = "🥇 $it", style = TextStyle(color = text, fontSize = 12.sp)) }
                }
            }
        }
    }
}

/** The bar's green: the app's own, as on its buttons. */
private val GOAL_GREEN = androidx.compose.ui.graphics.Color(0xFF43A047)

class StepsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepsWidget()
}
