package com.example.weatherapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.cos
import kotlin.math.sin
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherApp() {
    val viewModel: WeatherViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var cityInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTheme by remember { mutableStateOf(ThemePreference.getTheme(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    var selectedHour by remember { mutableStateOf<HourWeather?>(null) }

    val themeColors = when (currentTheme) {
        WeatherTheme.CUTE -> ThemeConfig.cuteColors()
        WeatherTheme.ROMANTIC -> ThemeConfig.romanticColors()
        WeatherTheme.TECH -> ThemeConfig.techColors()
        WeatherTheme.NATURE -> ThemeConfig.natureColors()
        else -> ThemeConfig.defaultColors()
    }

    val infiniteTransition = rememberInfiniteTransition()
    val floatingOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    fun getGradientColors(temp: Int): List<Color> {
        return if (currentTheme == WeatherTheme.DEFAULT) {
            when {
                temp >= 30 -> listOf(Color(0xFFFF6B6B), Color(0xFFEE5A24))
                temp >= 20 -> listOf(Color(0xFFFF9F43), Color(0xFFF0932B))
                temp >= 10 -> listOf(Color(0xFF54A0FF), Color(0xFF2E86DE))
                temp >= 0 -> listOf(Color(0xFF5F27CD), Color(0xFF341f97))
                else -> listOf(Color(0xFF1B9CFC), Color(0xFF0ABDE3))
            }
        } else {
            listOf(themeColors.gradientStart, themeColors.gradientEnd)
        }
    }

    var gradientColors by remember { mutableStateOf(listOf(themeColors.gradientStart, themeColors.gradientEnd)) }

    LaunchedEffect(uiState, currentTheme) {
        if (uiState is WeatherUiState.Success) {
            val temp = (uiState as WeatherUiState.Success).weather.current.tempC.toInt()
            gradientColors = getGradientColors(temp)
        } else {
            gradientColors = listOf(themeColors.gradientStart, themeColors.gradientEnd)
        }
    }

    fun checkPermissionAndGetLocation() {
        when {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // 权限请求会在运行时处理
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        val lastCity = prefs.getString("last_city", null)

        if (lastCity != null && lastCity.isNotBlank()) {
            cityInput = lastCity
            viewModel.searchWeather(lastCity)
        } else {
            checkPermissionAndGetLocation()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors))
    ) {
        FloatingParticles()
        AnimatedSun(rotationAngle, pulseScale)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showFavorites = !showFavorites }) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = "收藏", tint = Color.White.copy(alpha = 0.9f))
                }

                Text(
                    text = when (currentTheme) {
                        WeatherTheme.CUTE -> "🎀 粉粉天气"
                        WeatherTheme.ROMANTIC -> "🌸 唯美天气"
                        WeatherTheme.TECH -> "💙 科技天气"
                        WeatherTheme.NATURE -> "🌿 自然天气"
                        else -> "🌤️ 天气助手"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 2.sp,
                    modifier = Modifier.shadow(4.dp, RoundedCornerShape(8.dp))
                )

                IconButton(onClick = { showSettings = !showSettings }) {
                    Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color.White.copy(alpha = 0.9f))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { checkPermissionAndGetLocation() },
                        enabled = !isLocating
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("📍", fontSize = 22.sp)
                        }
                    }

                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = { cityInput = it },
                        placeholder = {
                            Text(
                                "输入城市名...",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(50.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                            cursorColor = Color.White
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )

                    IconButton(
                        onClick = {
                            if (cityInput.isNotBlank()) {
                                val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                prefs.edit().putString("last_city", cityInput).apply()
                                viewModel.searchWeather(cityInput)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "搜索", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (showFavorites) {
                FavoriteCitiesBar(viewModel = viewModel)
            }

            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) +
                            slideInVertically(initialOffsetY = { 100 }) togetherWith
                            fadeOut(animationSpec = tween(300)) +
                            slideOutVertically(targetOffsetY = { -100 })
                }
            ) { state ->
                when (state) {
                    is WeatherUiState.Loading -> LoadingAnimation()
                    is WeatherUiState.Success -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            CurrentWeatherCardEnhanced(weather = state.weather, offset = floatingOffset)
                            Spacer(modifier = Modifier.height(16.dp))
                            HourlyForecast(weather = state.weather)
                            Spacer(modifier = Modifier.height(16.dp))
                            ForecastCardEnhanced(weather = state.weather)
                            Spacer(modifier = Modifier.height(16.dp))
                            AirQualityCard(weather = state.weather)
                            Spacer(modifier = Modifier.height(16.dp))
                            LifestyleCardEnhanced(weather = state.weather)
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                    is WeatherUiState.Error -> ErrorCard(message = state.message, onRetry = { checkPermissionAndGetLocation() })
                }
            }
        }

        if (showSettings) {
            SettingsDialog(
                onDismiss = { showSettings = false },
                currentTheme = currentTheme,
                onThemeChange = { newTheme ->
                    currentTheme = newTheme
                    ThemePreference.saveTheme(context, newTheme)
                },
                onShowAbout = { showSettings = false; showAbout = true }
            )
        }

        if (showAbout) {
            AboutDialog(onDismiss = { showAbout = false })
        }

        if (selectedHour != null) {
            HourDetailDialog(hour = selectedHour!!, onDismiss = { selectedHour = null })
        }
    }
}

@Composable
fun FloatingParticles() {
    val particles = remember { List(30) { Particle() } }
    particles.forEach { particle ->
        androidx.compose.runtime.key(particle.id) {
            FloatingParticle(particle)
        }
    }
}

data class Particle(val id: Int = Random.nextInt(), val x: Float = Random.nextFloat(), val delay: Int = Random.nextInt(5000))

@Composable
fun FloatingParticle(particle: Particle) {
    val infiniteTransition = rememberInfiniteTransition()
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000 + particle.delay, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .offset(x = (particle.x * 350).dp, y = offsetY.dp)
    ) {
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = 2f
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.White.copy(alpha = 0.12f),
        shadowElevation = 6.dp
    ) {
        content()
    }
}

@Composable
fun AnimatedSun(rotationAngle: Float, pulseScale: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 100.dp, end = 40.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Canvas(
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2

            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                radius = 50f,
                center = Offset(centerX, centerY)
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = 40f,
                center = Offset(centerX, centerY)
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = 28f,
                center = Offset(centerX, centerY)
            )

            for (i in 0..11) {
                val angle = Math.toRadians((i * 30.0 + rotationAngle).toDouble())
                val startX = centerX + 32f * cos(angle).toFloat()
                val startY = centerY + 32f * sin(angle).toFloat()
                val endX = centerX + 52f * cos(angle).toFloat()
                val endY = centerY + 52f * sin(angle).toFloat()

                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.5f
                )
            }
        }
    }
}

@Composable
fun LoadingAnimation() {
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
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("😵", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text("重试", color = Color(0xFF1A237E), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FavoriteCitiesBar(viewModel: WeatherViewModel) {
    val favoriteCities = listOf("北京", "上海", "广州", "深圳", "成都")

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            items(favoriteCities) { city ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.clickable { viewModel.searchWeather(city) }
                ) {
                    Text(
                        text = city,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CurrentWeatherCardEnhanced(weather: WeatherResponse, offset: Float) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = weather.location.name,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = SimpleDateFormat("MM月dd日 EEEE", Locale.CHINESE).format(Date()),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.offset(y = (-offset).dp)
            ) {
                Text(
                    text = getWeatherIcon(weather.current.condition.text),
                    fontSize = 68.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                AnimatedContent(
                    targetState = weather.current.tempC.toInt(),
                    transitionSpec = {
                        fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                    }
                ) { temp ->
                    Text(
                        text = "$temp°",
                        fontSize = 68.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Text(
                text = weather.current.condition.text,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                TempRangeItem(
                    icon = "🌡️",
                    label = "最高",
                    value = "${weather.forecast.forecastDays[0].day.maxTempC.toInt()}°"
                )
                TempRangeItem(
                    icon = "❄️",
                    label = "最低",
                    value = "${weather.forecast.forecastDays[0].day.minTempC.toInt()}°"
                )
                TempRangeItem(
                    icon = "🔥",
                    label = "体感",
                    value = "${weather.current.feelsLikeC.toInt()}°"
                )
            }
        }
    }
}

@Composable
fun TempRangeItem(icon: String, label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 22.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun HourlyForecast(weather: WeatherResponse) {
    val hours = weather.forecast.forecastDays[0].hour ?: return

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏰", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("24小时预报", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(hours.take(24)) { hour ->
                    HourlyItem(hour = hour)
                }
            }
        }
    }
}

@Composable
fun HourlyItem(hour: HourWeather) {
    val timeStr = hour.time.substringAfter(" ").substringBefore(":")
    val isCurrentHour = timeStr == SimpleDateFormat("HH", Locale.getDefault()).format(Date())

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(60.dp)
            .background(
                if (isCurrentHour) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            text = "$timeStr:00",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = if (isCurrentHour) 1f else 0.7f),
            fontWeight = if (isCurrentHour) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = getWeatherIcon(hour.condition.text),
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${hour.tempC.toInt()}°",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun ForecastCardEnhanced(weather: WeatherResponse) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("3天预报", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(weather.forecast.forecastDays) { forecastDay ->
                    ForecastItemEnhanced(forecastDay = forecastDay)
                }
            }
        }
    }
}

@Composable
fun ForecastItemEnhanced(forecastDay: ForecastDay) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val date = dateFormat.parse(forecastDay.date) ?: Date()
    val dayOfWeek = SimpleDateFormat("EEEE", Locale.CHINESE).format(date)
    val shortDay = when (dayOfWeek) {
        "星期一" -> "周一"
        "星期二" -> "周二"
        "星期三" -> "周三"
        "星期四" -> "周四"
        "星期五" -> "周五"
        "星期六" -> "周六"
        "星期日" -> "周日"
        else -> dayOfWeek.take(2)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = Modifier.width(95.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = shortDay,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = forecastDay.date.substring(5),
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = getWeatherIcon(forecastDay.day.condition.text),
                fontSize = 34.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${forecastDay.day.maxTempC.toInt()}°",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${forecastDay.day.minTempC.toInt()}°",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            if (forecastDay.day.chanceOfRain > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "☔ ${forecastDay.day.chanceOfRain}%",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AirQualityCard(weather: WeatherResponse) {
    val aqi = weather.current.airQuality?.usEpaIndex ?: return

    val aqiInfo = when (aqi) {
        1 -> AirQualityInfo("优", Color(0xFF4CAF50), "空气质量令人满意")
        2 -> AirQualityInfo("良", Color(0xFF8BC34A), "空气质量可接受")
        3 -> AirQualityInfo("轻度污染", Color(0xFFFFC107), "敏感人群减少户外运动")
        4 -> AirQualityInfo("中度污染", Color(0xFFFF9800), "建议佩戴口罩")
        5 -> AirQualityInfo("重度污染", Color(0xFFF44336), "减少户外活动")
        else -> AirQualityInfo("严重污染", Color(0xFF9C27B0), "避免长时间户外活动")
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌫️", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("空气质量", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = aqi.toString(),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = aqiInfo.color
                    )
                    Text(
                        text = aqiInfo.level,
                        fontSize = 14.sp,
                        color = aqiInfo.color,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = aqiInfo.description,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PM2.5: ${weather.current.airQuality?.pm25?.toInt()} μg/m³",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = aqi / 6f,
                color = aqiInfo.color,
                trackColor = Color.White.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

data class AirQualityInfo(
    val level: String,
    val color: Color,
    val description: String
)

@Composable
fun LifestyleCardEnhanced(weather: WeatherResponse) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💡", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("生活指数", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LifestyleItemEnhanced(
                    icon = "🌅",
                    value = weather.forecast.forecastDays[0].astro.sunrise,
                    label = "日出"
                )
                LifestyleItemEnhanced(
                    icon = "🌇",
                    value = weather.forecast.forecastDays[0].astro.sunset,
                    label = "日落"
                )
                LifestyleItemEnhanced(
                    icon = "💧",
                    value = "${weather.current.humidity}%",
                    label = "湿度"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LifestyleItemEnhanced(
                    icon = "💨",
                    value = "${weather.current.windKph.toInt()} km/h",
                    label = "风速"
                )
                LifestyleItemEnhanced(
                    icon = "☀️",
                    value = "${weather.current.uv}",
                    label = "紫外线"
                )
                LifestyleItemEnhanced(
                    icon = "👕",
                    value = getSuggestion(weather.current.tempC.toInt()),
                    label = "穿衣"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = getTip(weather.current.tempC.toInt(), weather.current.condition.text),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun LifestyleItemEnhanced(icon: String, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 22.sp)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    currentTheme: WeatherTheme,
    onThemeChange: (WeatherTheme) -> Unit,
    onShowAbout: () -> Unit
) {
    var showThemeSelector by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF1A237E))
                Spacer(modifier = Modifier.width(8.dp))
                Text("设置", color = Color(0xFF1A237E), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeSelector = !showThemeSelector }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎨", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("主题风格", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("当前：${currentTheme.displayName}", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.Gray)
                    }
                }

                AnimatedVisibility(visible = showThemeSelector) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text("选择主题", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

                        ThemeOption(
                            name = "默认",
                            icon = "🌤️",
                            color = Color(0xFF1A237E),
                            isSelected = currentTheme == WeatherTheme.DEFAULT,
                            onClick = { onThemeChange(WeatherTheme.DEFAULT); showThemeSelector = false }
                        )
                        ThemeOption(
                            name = "可爱粉",
                            icon = "🎀",
                            color = Color(0xFFE91E63),
                            isSelected = currentTheme == WeatherTheme.CUTE,
                            onClick = { onThemeChange(WeatherTheme.CUTE); showThemeSelector = false }
                        )
                        ThemeOption(
                            name = "唯美紫",
                            icon = "🌸",
                            color = Color(0xFF9C27B0),
                            isSelected = currentTheme == WeatherTheme.ROMANTIC,
                            onClick = { onThemeChange(WeatherTheme.ROMANTIC); showThemeSelector = false }
                        )
                        ThemeOption(
                            name = "科技蓝",
                            icon = "💙",
                            color = Color(0xFF00BCD4),
                            isSelected = currentTheme == WeatherTheme.TECH,
                            onClick = { onThemeChange(WeatherTheme.TECH); showThemeSelector = false }
                        )
                        ThemeOption(
                            name = "自然绿",
                            icon = "🌿",
                            color = Color(0xFF4CAF50),
                            isSelected = currentTheme == WeatherTheme.NATURE,
                            onClick = { onThemeChange(WeatherTheme.NATURE); showThemeSelector = false }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SettingItem(
                    icon = "🔔",
                    title = "天气通知",
                    subtitle = "每日天气推送",
                    onClick = {}
                )

                SettingItem(
                    icon = "🌡️",
                    title = "温度单位",
                    subtitle = "摄氏度 / 华氏度",
                    onClick = {}
                )

                SettingItem(
                    icon = "ℹ️",
                    title = "关于",
                    subtitle = "版本 2.0.0",
                    onClick = onShowAbout
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Color(0xFF1A237E))
            }
        },
        containerColor = Color.White
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌤️", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("关于天气助手", color = Color(0xFF1A237E), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = "天气助手",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A237E)
                )
                Text(
                    text = "Weather Assistant",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Divider()

                Spacer(modifier = Modifier.height(12.dp))

                AboutItem(icon = "📱", text = "版本", value = "1.0.0")
                AboutItem(icon = "👨‍💻", text = "开发者", value = "梅梅子")
                AboutItem(icon = "📅", text = "更新日期", value = "2026年月")
                AboutItem(icon = "🌐", text = "数据来源", value = "WeatherAPI.com")
                AboutItem(icon = "🎨", text = "主题", value = "5种主题可选")

                Spacer(modifier = Modifier.height(12.dp))

                Divider()

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "一款简洁美观的天气应用\n为您提供准确的天气信息",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "© 2026 Weather Assistant",
                    fontSize = 10.sp,
                    color = Color.Gray.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Color(0xFF1A237E))
            }
        },
        containerColor = Color.White
    )
}

@Composable
fun AboutItem(icon: String, text: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row {
            Text(icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, fontSize = 14.sp, color = Color.Gray)
        }
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A237E))
    }
}

@Composable
fun ThemeOption(
    name: String,
    icon: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(name, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            Spacer(modifier = Modifier.weight(1f))
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SettingItem(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (title != "关于") {
                Text("开发中", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun HourDetailDialog(hour: HourWeather, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("详细预报", color = Color(0xFF1A237E)) },
        text = {
            Column {
                Text("时间: ${hour.time}", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("温度: ${hour.tempC.toInt()}°C", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("天气: ${hour.condition.text}", fontSize = 14.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        containerColor = Color.White
    )
}

fun getWeatherIcon(condition: String): String {
    return when {
        condition.contains("晴") -> "☀️"
        condition.contains("多云") -> "⛅"
        condition.contains("阴") -> "☁️"
        condition.contains("小雨") -> "🌦️"
        condition.contains("雨") -> "🌧️"
        condition.contains("雪") -> "❄️"
        condition.contains("雾") -> "🌫️"
        condition.contains("雷") -> "⛈️"
        else -> "🌤️"
    }
}

fun getSuggestion(temp: Int): String {
    return when {
        temp >= 28 -> "短袖"
        temp >= 22 -> "薄外套"
        temp >= 15 -> "长袖"
        temp >= 5 -> "厚外套"
        else -> "羽绒服"
    }
}

fun getTip(temp: Int, condition: String): String {
    return when {
        condition.contains("雨") -> "🌧️ 今日有雨，出门记得带伞"
        temp >= 35 -> "🥵 高温预警！注意防暑降温"
        temp <= 0 -> "🥶 天气寒冷，注意保暖"
        condition.contains("雪") -> "❄️ 下雪啦，路面湿滑注意安全"
        else -> "✨ 天气不错，适合户外活动"
    }
}