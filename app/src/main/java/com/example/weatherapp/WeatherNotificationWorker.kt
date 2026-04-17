package com.example.weatherapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WeatherNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
            val lastCity = prefs.getString("last_city", "Beijing") ?: "Beijing"
            val summary = "今日天气：请打开应用查看详细天气"

            // 确保两个参数都是 String
            NotificationHelper.sendWeatherNotification(
                applicationContext,
                "早安！$lastCity 天气简报",
                summary
            )
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}