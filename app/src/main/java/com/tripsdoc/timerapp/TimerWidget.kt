package com.tripsdoc.timerapp

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class TimerWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (remaining, state) = TimerStore.read(context)
        provideContent {
            WidgetContent(
                remainingFormatted = formatMillis(remaining),
                state = state,
                onOpen = actionStartActivity(Intent(context, MainActivity::class.java)),
                onRefresh = actionRunCallback<RefreshAction>()
            )
        }
    }
}

@Composable
private fun WidgetContent(
    remainingFormatted: String,
    state: TimerState,
    onOpen: Action,
    onRefresh: Action
) {
    Column(
        modifier = GlanceModifier
            .background(ColorProvider(0xFF121212.toInt()))
            .padding(12.dp)
    ) {
        Text(
            text = "Simple Timer",
            style = TextStyle(color = ColorProvider(0xFFFFFFFF.toInt()), fontWeight = FontWeight.Medium)
        )
        Text(
            text = remainingFormatted,
            style = TextStyle(color = ColorProvider(0xFFFFFFFF.toInt())),
            modifier = GlanceModifier.padding(top = 4.dp, bottom = 8.dp)
        )
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "Open",
                modifier = GlanceModifier
                    .wrapContentWidth()
                    .padding(end = 16.dp)
                    .clickable(onOpen),
                style = TextStyle(color = ColorProvider(0xFF64B5F6.toInt()))
            )
            Text(
                text = "Refresh",
                modifier = GlanceModifier.clickable(onRefresh),
                style = TextStyle(color = ColorProvider(0xFF81C784.toInt()))
            )
        }
        Text(
            text = "State: ${state.name}",
            style = TextStyle(color = ColorProvider(0xFFB0BEC5.toInt())),
            modifier = GlanceModifier.padding(top = 6.dp)
        )
    }
}

class RefreshAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { it }
        TimerWidget().update(context, glanceId)
    }
}

class TimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimerWidget()
}
