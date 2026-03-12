package com.personaltracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.personaltracker.data.AuthManager
import com.personaltracker.data.GistApi
import com.personaltracker.data.WidgetDataManager
import java.util.concurrent.TimeUnit

class WidgetRefreshWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val token = AuthManager.getToken() ?: return Result.failure()
        val gistId = AuthManager.getGistId() ?: return Result.failure()

        return try {
            val (config, data) = GistApi.loadGist(token, gistId)
            WidgetDataManager.cacheData(context, config, data)

            QuickEntryWidget().updateAll(context)
            TodaySummaryWidget().updateAll(context)
            StreakWidget().updateAll(context)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "widget_refresh"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        suspend fun refreshNow(context: Context) {
            val token = AuthManager.getToken() ?: return
            val gistId = AuthManager.getGistId() ?: return

            try {
                val (config, data) = GistApi.loadGist(token, gistId)
                WidgetDataManager.cacheData(context, config, data)

                QuickEntryWidget().updateAll(context)
                TodaySummaryWidget().updateAll(context)
                StreakWidget().updateAll(context)
            } catch (_: Exception) {
                // Silently fail — widgets will show cached data
            }
        }

        suspend fun updateWidgets(context: Context) {
            QuickEntryWidget().updateAll(context)
            TodaySummaryWidget().updateAll(context)
            StreakWidget().updateAll(context)
        }
    }
}
