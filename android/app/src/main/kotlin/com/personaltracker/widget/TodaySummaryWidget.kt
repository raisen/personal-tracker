package com.personaltracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
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
import com.personaltracker.MainActivity
import com.personaltracker.data.Entry
import com.personaltracker.data.FieldConfig
import com.personaltracker.data.FieldType
import com.personaltracker.data.WidgetDataManager

class TodaySummaryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = WidgetDataManager.getCachedConfig(context)
        val todayEntry = WidgetDataManager.getTodayEntry(context)
        val title = config?.title ?: "Personal Tracker"
        val fields = config?.fields ?: emptyList()

        provideContent {
            GlanceTheme {
                TodaySummaryContent(title, todayEntry, fields)
            }
        }
    }

    @Composable
    private fun TodaySummaryContent(title: String, entry: Entry?, fields: List<FieldConfig>) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .clickable(actionStartActivity<MainActivity>(
                    actionParametersOf(QuickEntryWidget.ACTION_KEY to QuickEntryWidget.ACTION_NEW_ENTRY)
                ))
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color(0xFF6C5CE7))
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                if (entry == null) {
                    // No entry yet
                    Box(
                        modifier = GlanceModifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No entry today",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    color = GlanceTheme.colors.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = "Tap to add",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(Color(0xFF6C5CE7))
                                )
                            )
                        }
                    }
                } else {
                    // Show key fields
                    val displayFields = fields.filter {
                        it.type != FieldType.DATE && entry.fields.containsKey(it.id)
                    }.take(4) // Show at most 4 fields

                    displayFields.forEach { field ->
                        val value = entry.fields[field.id]
                        val displayValue = when (value) {
                            is Boolean -> if (value) "Yes" else "No"
                            is Number -> if (value.toDouble() == value.toLong().toDouble()) {
                                value.toLong().toString()
                            } else {
                                value.toString()
                            }
                            else -> value?.toString() ?: ""
                        }

                        if (displayValue.isNotBlank()) {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${field.icon} ${field.label}:",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = GlanceTheme.colors.onSurfaceVariant
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = GlanceModifier.width(4.dp))
                                Text(
                                    text = displayValue,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GlanceTheme.colors.onSurface
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class TodaySummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TodaySummaryWidget()
}
