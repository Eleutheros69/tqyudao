package com.example.weatherapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.weatherapp.ui.theme.WeatherAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherAppTheme {
                WeatherApp()
            }
        }
    }
}

@Composable
fun WeatherApp() {
    val viewModel: WeatherViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var cityInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.searchWeather("Beijing")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A237E), Color(0xFF0D47A1))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🌤️ 天气助手",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 40.dp, bottom = 24.dp)
            )

            // 搜索卡片
            Surface(
                shape = RoundedCornerShape(50.dp),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = { cityInput = it },
                        placeholder = { Text("输入城市拼音，如：Beijing") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(50.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    IconButton(
                        onClick = {
                            if (cityInput.isNotBlank()) {
                                viewModel.searchWeather(cityInput)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (val state = uiState) {
                is WeatherUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                is WeatherUiState.Success -> {
                    WeatherContent(weather = state.weather)
                }

                is WeatherUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0x99FF5252),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.message,
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherContent(weather: WeatherResponse) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = when (weather.current.condition.text) {
                "晴" -> "☀️"
                "多云" -> "⛅"
                "阴" -> "☁️"
                "雨" -> "🌧️"
                "雪" -> "❄️"
                else -> "🌤️"
            },
            fontSize = 80.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${weather.current.tempC.toInt()}°",
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = weather.current.condition.text,
            fontSize = 24.sp,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "${weather.location.name}, ${weather.location.country}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "更新时间：${weather.location.localtime}",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.3f))

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WeatherMetric(
                        icon = "🔥",
                        value = "${weather.current.feelsLikeC.toInt()}°",
                        label = "体感温度"
                    )
                    WeatherMetric(
                        icon = "💧",
                        value = "${weather.current.humidity}%",
                        label = "湿度"
                    )
                    WeatherMetric(
                        icon = "💨",
                        value = "${weather.current.windKph.toInt()} km/h",
                        label = "风速"
                    )
                    WeatherMetric(
                        icon = "☀️",
                        value = "${weather.current.uv}",
                        label = "紫外线"
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherMetric(icon: String, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = icon, fontSize = 28.sp)
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text = label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
    }
}