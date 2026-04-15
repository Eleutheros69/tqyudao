package com.example.weatherapp

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WeatherViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    fun searchWeather(cityName: String) {
        if (cityName.isBlank()) return

        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                val response = RetrofitInstance.apiService.getCurrentWeather(
                    apiKey = RetrofitInstance.getApiKey(),
                    query = cityName.trim()
                )
                _uiState.value = WeatherUiState.Success(response)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error(
                    when {
                        e.message?.contains("401") == true -> "API密钥无效"
                        e.message?.contains("404") == true -> "找不到城市，请输入正确的城市拼音"
                        e.message?.contains("timeout") == true -> "网络超时，请重试"
                        else -> "网络错误：${e.message}"
                    }
                )
            }
        }
    }

    fun searchWeatherByLocation(location: Location) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                val query = "${location.latitude},${location.longitude}"
                val response = RetrofitInstance.apiService.getCurrentWeather(
                    apiKey = RetrofitInstance.getApiKey(),
                    query = query
                )
                _uiState.value = WeatherUiState.Success(response)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error("获取位置天气失败：${e.message}")
            }
        }
    }
}