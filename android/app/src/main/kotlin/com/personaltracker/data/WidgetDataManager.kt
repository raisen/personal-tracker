package com.personaltracker.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

object WidgetDataManager {
    private const val PREFS_NAME = "widget_data_prefs"
    private const val KEY_CONFIG = "cached_config"
    private const val KEY_ENTRIES = "cached_entries"

    private val gson: Gson = GsonBuilder().create()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun cacheData(context: Context, config: TrackerConfig, data: TrackerData) {
        prefs(context).edit()
            .putString(KEY_CONFIG, gson.toJson(config))
            .putString(KEY_ENTRIES, gson.toJson(data.entries))
            .apply()
    }

    fun getCachedConfig(context: Context): TrackerConfig? {
        val json = prefs(context).getString(KEY_CONFIG, null) ?: return null
        return try {
            gson.fromJson(json, TrackerConfig::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getCachedEntries(context: Context): List<Entry> {
        val json = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Entry>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getTodayEntry(context: Context): Entry? {
        val config = getCachedConfig(context) ?: return null
        val entries = getCachedEntries(context)
        val today = LocalDate.now().toString()
        val dateField = config.fields.find { it.type == FieldType.DATE }

        return entries.find { entry ->
            if (dateField != null) {
                entry.fields[dateField.id] == today
            } else {
                entry._created.startsWith(today)
            }
        }
    }

    fun getStreak(context: Context): Int {
        val config = getCachedConfig(context) ?: return 0
        val entries = getCachedEntries(context)
        if (entries.isEmpty()) return 0

        val dateField = config.fields.find { it.type == FieldType.DATE }

        val dates = entries.mapNotNull { entry ->
            try {
                val dateStr = if (dateField != null) {
                    entry.fields[dateField.id] as? String
                } else {
                    entry._created.take(10)
                }
                dateStr?.let { LocalDate.parse(it) }
            } catch (e: Exception) {
                null
            }
        }.distinct().sortedDescending()

        if (dates.isEmpty()) return 0

        val today = LocalDate.now()
        // Streak must include today or yesterday to be active
        if (dates.first() != today && dates.first() != today.minusDays(1)) return 0

        var streak = 1
        for (i in 0 until dates.size - 1) {
            if (dates[i].minusDays(1) == dates[i + 1]) {
                streak++
            } else {
                break
            }
        }
        return streak
    }
}
