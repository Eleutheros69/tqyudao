package com.example.weatherapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.weatherapp.ui.theme.WeatherAppTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 定位状态
    var isLocating by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    // 检查权限并获取位置
    fun checkPermissionAndGetLocation() {
        when {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // 已有权限，获取位置
                isLocating = true
                scope.launch {
                    try {
                        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                        val cancellationToken = CancellationTokenSource()
                        val location = fusedClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            cancellationToken.token
                        ).await()

                        if (location != null) {
                            viewModel.searchWeatherByLocation(location)
                        } else {
                            viewModel.searchWeather("Beijing")
                        }
                    } catch (e: Exception) {
                        viewModel.searchWeather("Beijing")
                    } finally {
                        isLocating = false
                    }
                }
            }
            else -> {
                // 请求权限
                permissionDenied = false
                scope.launch {
                    // 这里需要 Activity Result API，简化处理：引导用户去设置
                    permissionDenied = true
                }
            }
        }
    }

    // 启动时自动定位
    LaunchedEffect(Unit) {
        checkPermissionAndGetLocation()
    }

    // 动画渐变背景
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1A237E),
            Color(0xFF283593),
            Color(0xFF0D47A1)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题区域
            AnimatedContent(
                targetState = uiState !is WeatherUiState.Loading,
                transitionSpec = {
                    fadeIn() + slideInVertically() togetherWith fadeOut() + slideOutVertically()
                }
            ) { isLoaded ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🌤️ 天气助手",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Weather Assistant",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 搜索卡片
            Surface(
                shape = RoundedCornerShape(50.dp),
                shadowElevation = 8.dp,
                tonalElevation = 0.dp,
                color = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 定位按钮
                    IconButton(
                        onClick = { checkPermissionAndGetLocation() },
                        enabled = !isLocating
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF1A237E)
                            )
                        } else {
                            Icon(
                                Icons.Default.LocationOn,  // ✅ 使用 LocationOn
                                contentDescription = "定位",
                                tint = Color(0xFF1A237E)
                            )
                        }
                    }

                    // 搜索输入框
                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = { cityInput = it },
                        placeholder = {
                            Text(
                                "输入城市拼音，如：Beijing",
                                color = Color.Gray.copy(alpha = 0.6f)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(50.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color(0xFF1A237E),
                            unfocusedTextColor = Color(0xFF1A237E)
                        )
                    )

                    // 搜索按钮
                    IconButton(
                        onClick = {
                            if (cityInput.isNotBlank()) {
                                viewModel.searchWeather(cityInput)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = Color(0xFF1A237E)
                        )
                    }
                }
            }

            // 权限被拒绝提示
            if (permissionDenied) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x99FF9800)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📍", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "请在设置中开启定位权限",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 天气内容
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                }
            ) { state ->
                when (state) {
                    is WeatherUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(60.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "获取天气中...",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    is WeatherUiState.Success -> {
                        WeatherCard(weather = state.weather)
                    }

                    is WeatherUiState.Error -> {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Text("😵", fontSize = 64.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = state.message,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { checkPermissionAndGetLocation() },
                                    shape = RoundedCornerShape(50.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White
                                    )
                                ) {
                                    Text(
                                        "重试",
                                        color = Color(0xFF1A237E),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherCard(weather: WeatherResponse) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(20.dp, RoundedCornerShape(32.dp))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 城市名称
            Text(
                text = weather.location.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = weather.location.country,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 天气图标和温度
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // 动态天气图标
                Text(
                    text = when {
                        weather.current.condition.text.contains("晴") -> "☀️"
                        weather.current.condition.text.contains("云") -> "⛅"
                        weather.current.condition.text.contains("阴") -> "☁️"
                        weather.current.condition.text.contains("雨") -> "🌧️"
                        weather.current.condition.text.contains("雪") -> "❄️"
                        weather.current.condition.text.contains("雾") -> "🌫️"
                        else -> "🌤️"
                    },
                    fontSize = 72.sp
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${weather.current.tempC.toInt()}°",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = weather.current.condition.text,
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 体感温度
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔥", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "体感温度 ${weather.current.feelsLikeC.toInt()}°C",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 详细信息网格
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeatherDetailItem(
                    icon = "💧",
                    value = "${weather.current.humidity}%",
                    label = "湿度"
                )
                WeatherDetailItem(
                    icon = "💨",
                    value = "${weather.current.windKph.toInt()} km/h",
                    label = "风速"
                )
                WeatherDetailItem(
                    icon = "☀️",
                    value = "${weather.current.uv}",
                    label = "紫外线"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 更新时间
            Text(
                text = "更新于 ${weather.location.localtime.substringAfter(" ")}",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun WeatherDetailItem(icon: String, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 24.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}