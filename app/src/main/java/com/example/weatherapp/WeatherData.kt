package com.example.weatherapp

import com.google.gson.annotations.SerializedName

// API 返回的完整天气数据
data class WeatherResponse(
    @SerializedName("location") val location: Location,
    @SerializedName("current") val current: CurrentWeather
)

data class Location(
    @SerializedName("name") val name: String,
    @SerializedName("country") val country: String,
    @SerializedName("localtime") val localtime: String
)

data class CurrentWeather(
    @SerializedName("temp_c") val tempC: Double,
    @SerializedName("feelslike_c") val feelsLikeC: Double,
    @SerializedName("humidity") val humidity: Int,
    @SerializedName("wind_kph") val windKph: Double,
    @SerializedName("uv") val uv: Double,
    @SerializedName("condition") val condition: Condition
)

data class Condition(
    @SerializedName("text") val text: String,
    @SerializedName("icon") val iconUrl: String
)

// UI 状态
sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val weather: WeatherResponse) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}