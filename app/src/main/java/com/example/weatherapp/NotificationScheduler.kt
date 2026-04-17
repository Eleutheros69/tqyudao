package com.example.weatherapp

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val WORK_NAME = "weather_notification_work"

    fun scheduleDailyWeatherNotification(context: Context, enabled: Boolean) {
        if (enabled) {
            val workRequest = PeriodicWorkRequestBuilder<WeatherNotificationWorker>(
                1, TimeUnit.DAYS
            ).setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    private fun calculateInitialDelay(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        if (now.after(target)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}