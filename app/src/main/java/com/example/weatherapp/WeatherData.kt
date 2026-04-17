package com.example.weatherapp

import com.google.gson.annotations.SerializedName

// API 返回的完整天气数据
data class WeatherResponse(
    @SerializedName("location") val location: Location,
    @SerializedName("current") val current: CurrentWeather,
    @SerializedName("forecast") val forecast: Forecast
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
    @SerializedName("condition") val condition: Condition,
    @SerializedName("air_quality") val airQuality: AirQuality? = null,
    @SerializedName("pressure_mb") val pressure: String? = null,   // 气压 (hPa)
    @SerializedName("vis_km") val vis: String? = null              // 能见度 (km)
)

data class Forecast(
    @SerializedName("forecastday") val forecastDays: List<ForecastDay>
)

data class ForecastDay(
    @SerializedName("date") val date: String,
    @SerializedName("day") val day: DayWeather,
    @SerializedName("astro") val astro: Astro,
    @SerializedName("hour") val hour: List<HourWeather>? = null
)

data class DayWeather(
    @SerializedName("maxtemp_c") val maxTempC: Double,
    @SerializedName("mintemp_c") val minTempC: Double,
    @SerializedName("avgtemp_c") val avgTempC: Double,
    @SerializedName("condition") val condition: Condition,
    @SerializedName("daily_chance_of_rain") val chanceOfRain: Int
)

data class HourWeather(
    @SerializedName("time") val time: String,
    @SerializedName("temp_c") val tempC: Double,
    @SerializedName("condition") val condition: Condition
)

data class Astro(
    @SerializedName("sunrise") val sunrise: String,
    @SerializedName("sunset") val sunset: String
)

data class Condition(
    @SerializedName("text") val text: String,
    @SerializedName("icon") val iconUrl: String
)

data class AirQuality(
    @SerializedName("pm2_5") val pm25: Double,
    @SerializedName("pm10") val pm10: Double,
    @SerializedName("us_epa_index") val usEpaIndex: Int
)

// UI 状态
sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val weather: WeatherResponse) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}