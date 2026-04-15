package com.example.weatherapp

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("v1/current.json")
    suspend fun getCurrentWeather(
        @Query("key") apiKey: String,
        @Query("q") query: String,
        @Query("lang") lang: String = "zh"
    ): WeatherResponse

    // 新增：获取天气预报（3天）
    @GET("v1/forecast.json")
    suspend fun getForecastWeather(
        @Query("key") apiKey: String,
        @Query("q") query: String,
        @Query("days") days: Int = 3,
        @Query("lang") lang: String = "zh"
    ): WeatherResponse
}