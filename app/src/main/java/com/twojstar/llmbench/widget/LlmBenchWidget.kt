package com.twojstar.llmbench.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.action.actionStartActivity

class LlmBenchWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            QuickActions(context)
        }
    }

    @Composable
    private fun QuickActions(context: Context) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("LlmBench")
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WidgetButton(context, "Web AI", LlmBenchWidgetNavigation.DESTINATION_WEB_AI)
                WidgetButton(context, "Compare", LlmBenchWidgetNavigation.DESTINATION_COMPARE)
                WidgetButton(context, "Studio", LlmBenchWidgetNavigation.DESTINATION_STUDIO)
            }
        }
    }

    @Composable
    private fun WidgetButton(context: Context, label: String, destination: String) {
        Button(
            text = label,
            onClick = actionStartActivity(LlmBenchWidgetNavigation.launchIntent(context, destination)),
        )
    }
}

class LlmBenchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LlmBenchWidget()
}
