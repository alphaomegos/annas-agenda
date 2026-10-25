package com.alphaomegos.annasagenda

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
    val title: String,
    val today: String,
    val yesterday: String,
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
    return StepsWidgetData(
        title = words.getString(R.string.steps_widget_title),
        today = count.format(days[day] ?: 0),
        yesterday = words.getString(R.string.steps_widget_yesterday, count.format(days[day.minusDays(1)] ?: 0)),
        record = best?.let { words.getString(R.string.steps_widget_record, count.format(it.steps)) },
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
                    Text(text = "🚶 " + data.title, style = TextStyle(color = text, fontSize = 13.sp))
                    Text(text = data.today, style = TextStyle(color = text, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                    Text(text = data.yesterday, style = TextStyle(color = text, fontSize = 12.sp))
                    data.record?.let { Text(text = "🥇 $it", style = TextStyle(color = text, fontSize = 12.sp)) }
                }
            }
        }
    }
}

class StepsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepsWidget()
}
