package com.personaltracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import android.graphics.Color
import com.personaltracker.MainActivity
import com.personaltracker.data.WidgetDataManager

class QuickEntryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = WidgetDataManager.getCachedConfig(context)
        val title = config?.title ?: "Personal Tracker"

        provideContent {
            GlanceTheme {
                QuickEntryContent(title)
            }
        }
    }

    @Composable
    private fun QuickEntryContent(title: String) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color.WHITE, Color.parseColor("#1E1E2E")))
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "+",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color.parseColor("#6C5CE7"), Color.parseColor("#A29BFE"))
                    )
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(Color.parseColor("#333333"), Color.parseColor("#E0E0E0"))
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

class QuickEntryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = QuickEntryWidget()
}
