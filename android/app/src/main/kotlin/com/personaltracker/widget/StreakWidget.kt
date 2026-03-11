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

class StreakWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val streak = WidgetDataManager.getStreak(context)

        provideContent {
            GlanceTheme {
                StreakContent(streak)
            }
        }
    }

    @Composable
    private fun StreakContent(streak: Int) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color.WHITE, Color.parseColor("#1E1E2E")))
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (streak > 0) "\uD83D\uDD25" else "\u2744\uFE0F",
                    style = TextStyle(fontSize = 24.sp)
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = streak.toString(),
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (streak > 0) {
                            ColorProvider(Color.parseColor("#E17055"), Color.parseColor("#FAB1A0"))
                        } else {
                            ColorProvider(Color.GRAY, Color.LTGRAY)
                        }
                    )
                )
                Text(
                    text = if (streak == 1) "day" else "days",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = ColorProvider(Color.GRAY, Color.LTGRAY)
                    )
                )
            }
        }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = StreakWidget()
}
