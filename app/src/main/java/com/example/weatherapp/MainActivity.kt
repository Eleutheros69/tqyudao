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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var isLocating by remember { mutableStateOf(false) }
    var selectedHour by remember { mutableStateOf<HourWeather?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .shadow(8.dp),
                containerColor = Color.White.copy(alpha = 0.15f),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "天气") },
                    label = { Text("天气") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                        unselectedTextColor = Color.White.copy(alpha = 0.6f),
                        indicatorColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "收藏") },
                    label = { Text("收藏") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                        unselectedTextColor = Color.White.copy(alpha = 0.6f),
                        indicatorColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                        unselectedTextColor = Color.White.copy(alpha = 0.6f),
                        indicatorColor = Color.White.copy(alpha = 0.3f)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(gradientColors))
                .padding(paddingValues)
        ) {
            FloatingParticles()
            AnimatedSun(rotationAngle, pulseScale)

            when (selectedTab) {
                0 -> WeatherTab(
                    uiState = uiState,
                    cityInput = cityInput,
                    onCityInputChange = { cityInput = it },
                    onSearch = {
                        if (cityInput.isNotBlank()) {
                            val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("last_city", cityInput).apply()
                            viewModel.searchWeather(cityInput)
                        }
                    },
                    onLocationClick = { checkPermissionAndGetLocation() },
                    isLocating = isLocating,
                    floatingOffset = floatingOffset,
                    onHourClick = { selectedHour = it },
                    onRefresh = {
                        if (cityInput.isNotBlank()) {
                            viewModel.searchWeather(cityInput)
                        } else {
                            checkPermissionAndGetLocation()
                        }
                    }
                )
                1 -> FavoritesTab(viewModel = viewModel)
                2 -> SettingsTab(
                    currentTheme = currentTheme,
                    onThemeChange = { newTheme ->
                        currentTheme = newTheme
                        ThemePreference.saveTheme(context, newTheme)
                    },
                    onShowAbout = { showAbout = true }
                )
            }

            if (selectedHour != null) {
                HourDetailDialog(hour = selectedHour!!, onDismiss = { selectedHour = null })
            }

            if (showAbout) {
                AboutDialog(onDismiss = { showAbout = false })
            }
        }
    }
}

@Composable
fun WeatherTab(
    uiState: WeatherUiState,
    cityInput: String,
    onCityInputChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLocationClick: () -> Unit,
    isLocating: Boolean,
    floatingOffset: Float,
    onHourClick: (HourWeather) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onLocationClick, enabled = !isLocating) {
                        if (isLocating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("📍", fontSize = 24.sp)
                        }
                    }
                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = onCityInputChange,
                        placeholder = { Text("搜索城市...", color = Color.White.copy(alpha = 0.6f)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(30.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White
                        )
                    )
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "搜索", tint = Color.White)
                    }
                }
            }
        }

        when (uiState) {
            is WeatherUiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("加载天气中...", color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
            is WeatherUiState.Success -> {
                val weather = uiState.weather
                item { CurrentWeatherCard(weather = weather, offset = floatingOffset) }
                item { HourlyForecastCard(weather = weather, onHourClick = onHourClick) }
                item { DailyForecastCard(weather = weather) }
                item { AirQualityCardCompact(weather = weather) }
                item { LifestyleGrid(weather = weather) }
                item {
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("刷新天气")
                    }
                }
            }
            is WeatherUiState.Error -> {
                item { ErrorCard(message = uiState.message, onRetry = onRefresh) }
            }
        }
    }
}

@Composable
fun CurrentWeatherCard(weather: WeatherResponse, offset: Float) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = weather.location.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = SimpleDateFormat("MM月dd日 EEEE", Locale.CHINESE).format(Date()),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset(y = (-offset).dp)
            ) {
                Text(
                    text = getWeatherIcon(weather.current.condition.text),
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                AnimatedContent(
                    targetState = weather.current.tempC.toInt(),
                    transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() }
                ) { temp ->
                    Text(
                        text = "$temp°",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Text(
                text = weather.current.condition.text,
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TempInfo(icon = "🌡️", label = "最高", value = "${weather.forecast.forecastDays[0].day.maxTempC.toInt()}°")
                TempInfo(icon = "❄️", label = "最低", value = "${weather.forecast.forecastDays[0].day.minTempC.toInt()}°")
                TempInfo(icon = "🔥", label = "体感", value = "${weather.current.feelsLikeC.toInt()}°")
            }
        }
    }
}

@Composable
fun TempInfo(icon: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 24.sp)
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun HourlyForecastCard(weather: WeatherResponse, onHourClick: (HourWeather) -> Unit) {
    val hours = weather.forecast.forecastDays[0].hour ?: return
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("24小时预报", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(hours.take(24)) { hour ->
                    HourlyCard(hour = hour, onClick = { onHourClick(hour) })
                }
            }
        }
    }
}

@Composable
fun HourlyCard(hour: HourWeather, onClick: () -> Unit) {
    val timeStr = hour.time.substringAfter(" ").substringBefore(":")
    val isCurrentHour = timeStr == SimpleDateFormat("HH", Locale.getDefault()).format(Date())
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentHour) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier
            .width(70.dp)
            .clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Text("$timeStr:00", fontSize = 12.sp, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(getWeatherIcon(hour.condition.text), fontSize = 28.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${hour.tempC.toInt()}°", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun DailyForecastCard(weather: WeatherResponse) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("3天预报", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            weather.forecast.forecastDays.forEach { day ->
                DailyItem(day = day)
                if (day != weather.forecast.forecastDays.last()) {
                    Divider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
fun DailyItem(day: ForecastDay) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val date = dateFormat.parse(day.date) ?: Date()
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(shortDay, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.width(50.dp))
        Text(getWeatherIcon(day.day.condition.text), fontSize = 28.sp)
        Text("${day.day.minTempC.toInt()}° / ${day.day.maxTempC.toInt()}°", fontSize = 14.sp, color = Color.White)
        if (day.day.chanceOfRain > 0) {
            Text("☔ ${day.day.chanceOfRain}%", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun AirQualityCardCompact(weather: WeatherResponse) {
    val aqi = weather.current.airQuality?.usEpaIndex ?: return

    val (level, color, description) = when (aqi) {
        1 -> Triple("优", Color(0xFF4CAF50), "空气很棒")
        2 -> Triple("良", Color(0xFF8BC34A), "空气良好")
        3 -> Triple("轻度污染", Color(0xFFFFC107), "敏感人群注意")
        4 -> Triple("中度污染", Color(0xFFFF9800), "减少户外活动")
        5 -> Triple("重度污染", Color(0xFFF44336), "佩戴口罩")
        else -> Triple("严重污染", Color(0xFF9C27B0), "避免外出")
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("空气质量", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                Text(aqi.toString(), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = color)
                Text(level, fontSize = 14.sp, color = color)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(description, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                Text("PM2.5: ${weather.current.airQuality?.pm25?.toInt()} μg/m³", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun LifestyleGrid(weather: WeatherResponse) {
    // 获取更多指标数据（添加空值安全）
    val pressure = weather.current.pressure?.toIntOrNull() ?: 0
    val visibility = weather.current.vis?.toIntOrNull() ?: 0
    val rainChance = weather.forecast.forecastDays[0].day.chanceOfRain

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("生活指数", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))

            // 第一行：日出、日落、湿度
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LifestyleChip(icon = "🌅", label = "日出", value = weather.forecast.forecastDays[0].astro.sunrise)
                LifestyleChip(icon = "🌇", label = "日落", value = weather.forecast.forecastDays[0].astro.sunset)
                LifestyleChip(icon = "💧", label = "湿度", value = "${weather.current.humidity}%")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 第二行：风速、紫外线、穿衣
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LifestyleChip(icon = "💨", label = "风速", value = "${weather.current.windKph.toInt()} km/h")
                LifestyleChip(icon = "☀️", label = "紫外线", value = "${weather.current.uv}")
                LifestyleChip(icon = "👕", label = "穿衣", value = getSuggestion(weather.current.tempC.toInt()))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 第三行：气压、能见度、降雨概率（新增）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LifestyleChip(icon = "⏲️", label = "气压", value = "$pressure hPa")
                LifestyleChip(icon = "👁️", label = "能见度", value = "$visibility km")
                LifestyleChip(icon = "🌧️", label = "降雨概率", value = "$rainChance%")
            }
        }
    }
}

@Composable
fun LifestyleChip(icon: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 24.sp)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
fun FavoritesTab(viewModel: WeatherViewModel) {
    val favoriteCities = listOf("Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Chengdu", "Hangzhou", "Wuhan", "Xi'an")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(favoriteCities) { city ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.searchWeather(city) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(city, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    currentTheme: WeatherTheme,
    onThemeChange: (WeatherTheme) -> Unit,
    onShowAbout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("主题风格", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeChip(name = "默认", icon = "🌤️", isSelected = currentTheme == WeatherTheme.DEFAULT) {
                            onThemeChange(WeatherTheme.DEFAULT)
                        }
                        ThemeChip(name = "可爱粉", icon = "🎀", isSelected = currentTheme == WeatherTheme.CUTE) {
                            onThemeChange(WeatherTheme.CUTE)
                        }
                        ThemeChip(name = "唯美紫", icon = "🌸", isSelected = currentTheme == WeatherTheme.ROMANTIC) {
                            onThemeChange(WeatherTheme.ROMANTIC)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeChip(name = "科技蓝", icon = "💙", isSelected = currentTheme == WeatherTheme.TECH) {
                            onThemeChange(WeatherTheme.TECH)
                        }
                        ThemeChip(name = "自然绿", icon = "🌿", isSelected = currentTheme == WeatherTheme.NATURE) {
                            onThemeChange(WeatherTheme.NATURE)
                        }
                    }
                }
            }
        }
        item {
            SettingCard(icon = "🔔", title = "天气通知", subtitle = "每日天气推送", onClick = {})
        }
        item {
            SettingCard(icon = "🌡️", title = "温度单位", subtitle = "摄氏度 / 华氏度", onClick = {})
        }
        item {
            SettingCard(icon = "ℹ️", title = "关于", subtitle = "版本 2.0.0", onClick = onShowAbout)
        }
    }
}

@Composable
fun ThemeChip(name: String, icon: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(name, fontSize = 12.sp, color = if (isSelected) Color(0xFF1A237E) else Color.White)
        }
    }
}

@Composable
fun SettingCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    Text(subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
            if (title != "关于") {
                Text("开发中", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            } else {
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("😵", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry, shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                Text("重试", color = Color(0xFF1A237E))
            }
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
        drawCircle(color = Color.White.copy(alpha = 0.08f), radius = 2f)
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
            drawCircle(color = Color.White.copy(alpha = 0.1f), radius = 50f, center = Offset(centerX, centerY))
            drawCircle(color = Color.White.copy(alpha = 0.2f), radius = 40f, center = Offset(centerX, centerY))
            drawCircle(color = Color.White.copy(alpha = 0.35f), radius = 28f, center = Offset(centerX, centerY))
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
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
                AboutItem(icon = "📱", text = "版本", value = "2.0.0")
                AboutItem(icon = "👨‍💻", text = "开发者", value = "梅梅子")
                AboutItem(icon = "📅", text = "更新日期", value = "2026年4月")
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