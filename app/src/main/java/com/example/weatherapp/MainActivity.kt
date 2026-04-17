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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.work.*
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
import java.util.concurrent.TimeUnit
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
    var showPrivacy by remember { mutableStateOf(false) }

    // 温度单位
    var isCelsius by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        isCelsius = prefs.getBoolean("is_celsius", true)
    }
    fun saveUnitPreference(celsius: Boolean) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_celsius", celsius).apply()
        isCelsius = celsius
    }

    // 通知开关
    var isNotificationEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        isNotificationEnabled = prefs.getBoolean("notification_enabled", false)
    }
    fun scheduleDailyNotification(enabled: Boolean) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notification_enabled", enabled).apply()
        isNotificationEnabled = enabled
        if (enabled) {
            val initialDelay = run {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 8)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                if (calendar.timeInMillis <= System.currentTimeMillis())
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                calendar.timeInMillis - System.currentTimeMillis()
            }
            val workRequest = PeriodicWorkRequestBuilder<WeatherNotificationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_weather_notification",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        } else {
            WorkManager.getInstance(context).cancelUniqueWork("daily_weather_notification")
        }
    }

    fun convertTemp(celsius: Double): Int {
        return if (isCelsius) celsius.toInt() else ((celsius * 9 / 5) + 32).toInt()
    }

    val themeColors = when (currentTheme) {
        WeatherTheme.CUTE -> ThemeConfig.cuteColors()
        WeatherTheme.ROMANTIC -> ThemeConfig.romanticColors()
        WeatherTheme.TECH -> ThemeConfig.techColors()
        WeatherTheme.NATURE -> ThemeConfig.natureColors()
        else -> ThemeConfig.defaultColors()
    }

    val infiniteTransition = rememberInfiniteTransition()
    val floatingOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart)
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
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
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                isLocating = true
                scope.launch {
                    try {
                        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                        val tokenSource = CancellationTokenSource()
                        val location = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token).await()
                        if (location != null) viewModel.searchWeatherByLocation(location)
                        else viewModel.searchWeather("Beijing")
                    } catch (e: Exception) { viewModel.searchWeather("Beijing") }
                    finally { isLocating = false }
                }
            }
            else -> { /* 权限请求 */ }
        }
    }

    LaunchedEffect(Unit) { NotificationHelper.createNotificationChannel(context) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        val lastCity = prefs.getString("last_city", null)
        if (!lastCity.isNullOrBlank()) {
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
                modifier = Modifier.padding(16.dp).clip(RoundedCornerShape(30.dp)).shadow(8.dp),
                containerColor = Color.White.copy(alpha = 0.15f),
                tonalElevation = 0.dp
            ) {
                listOf("天气", "收藏", "设置").forEachIndexed { idx, label ->
                    NavigationBarItem(
                        icon = { Icon(if (idx == 0) Icons.Default.Home else if (idx == 1) Icons.Default.Favorite else Icons.Default.Settings, contentDescription = label) },
                        label = { Text(label) },
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
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
                    uiState, cityInput, { cityInput = it },
                    onSearch = {
                        if (cityInput.isNotBlank()) {
                            context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                .edit().putString("last_city", cityInput).apply()
                            viewModel.searchWeather(cityInput)
                        }
                    },
                    onLocationClick = { checkPermissionAndGetLocation() },
                    isLocating = isLocating,
                    floatingOffset = floatingOffset,
                    onHourClick = { selectedHour = it },
                    onRefresh = {
                        if (cityInput.isNotBlank()) viewModel.searchWeather(cityInput)
                        else checkPermissionAndGetLocation()
                    },
                    convertTemp = { convertTemp(it) },
                    context = context
                )
                1 -> FavoritesTab(viewModel)
                2 -> SettingsTab(
                    currentTheme = currentTheme,
                    onThemeChange = { currentTheme = it; ThemePreference.saveTheme(context, it) },
                    onShowAbout = { showAbout = true },
                    onShowPrivacy = { showPrivacy = true },
                    isCelsius = isCelsius,
                    onUnitChange = { saveUnitPreference(it) },
                    isNotificationEnabled = isNotificationEnabled,
                    onNotificationToggle = { scheduleDailyNotification(it) },
                    context = context  // 新增 context
                )
            }

            if (selectedHour != null) HourDetailDialog(selectedHour!!) { selectedHour = null }
            if (showAbout) AboutDialog { showAbout = false }
            if (showPrivacy) PrivacyDialog { showPrivacy = false }
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
    onRefresh: () -> Unit,
    convertTemp: (Double) -> Int,
    context: Context
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
                        if (isLocating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("📍", fontSize = 24.sp)
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
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "搜索", tint = Color.White) }
                }
            }
        }

        when (uiState) {
            is WeatherUiState.Loading -> {
                item {
                    Box(Modifier.fillParentMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("加载天气中...", color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
            is WeatherUiState.Success -> {
                val weather = uiState.weather
                item { CurrentWeatherCard(weather, floatingOffset, convertTemp) }
                item { HourlyForecastCard(weather, onHourClick, convertTemp) }
                item { DailyForecastCard(weather, convertTemp) }
                item { AirQualityCardCompact(weather) }
                item { LifestyleGrid(weather) }
                item {
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("刷新天气")
                    }
                }
                item {
                    Button(
                        onClick = { NotificationHelper.sendTestNotification(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("发送测试通知")
                    }
                }
                item {
                    Button(
                        onClick = {
                            val workRequest = OneTimeWorkRequestBuilder<WeatherNotificationWorker>().build()
                            WorkManager.getInstance(context).enqueue(workRequest)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Text("⏰", fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("测试定时通知")
                    }
                }
            }
            is WeatherUiState.Error -> {
                item { ErrorCard(uiState.message, onRefresh) }
            }
        }
    }
}

@Composable
fun CurrentWeatherCard(weather: WeatherResponse, offset: Float, convertTemp: (Double) -> Int) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(weather.location.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(SimpleDateFormat("MM月dd日 EEEE", Locale.CHINESE).format(Date()), fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(y = (-offset).dp)) {
                Text(getWeatherIcon(weather.current.condition.text), fontSize = 64.sp)
                Spacer(Modifier.width(16.dp))
                AnimatedContent(targetState = convertTemp(weather.current.tempC), transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() }) { temp ->
                    Text("$temp°", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Text(weather.current.condition.text, fontSize = 18.sp, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TempInfo("🌡️", "最高", "${convertTemp(weather.forecast.forecastDays[0].day.maxTempC)}°")
                TempInfo("❄️", "最低", "${convertTemp(weather.forecast.forecastDays[0].day.minTempC)}°")
                TempInfo("🔥", "体感", "${convertTemp(weather.current.feelsLikeC)}°")
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
fun HourlyForecastCard(weather: WeatherResponse, onHourClick: (HourWeather) -> Unit, convertTemp: (Double) -> Int) {
    val hours = weather.forecast.forecastDays[0].hour ?: return
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("24小时预报", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(hours.take(24)) { hour ->
                    HourlyCard(hour, { onHourClick(hour) }, convertTemp)
                }
            }
        }
    }
}

@Composable
fun HourlyCard(hour: HourWeather, onClick: () -> Unit, convertTemp: (Double) -> Int) {
    val timeStr = hour.time.substringAfter(" ").substringBefore(":")
    val isCurrentHour = timeStr == SimpleDateFormat("HH", Locale.getDefault()).format(Date())
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isCurrentHour) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.width(70.dp).clickable { onClick() }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
            Text("$timeStr:00", fontSize = 12.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(getWeatherIcon(hour.condition.text), fontSize = 28.sp)
            Spacer(Modifier.height(4.dp))
            Text("${convertTemp(hour.tempC)}°", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun DailyForecastCard(weather: WeatherResponse, convertTemp: (Double) -> Int) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("3天预报", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(12.dp))
            weather.forecast.forecastDays.forEach { day ->
                DailyItem(day, convertTemp)
                if (day != weather.forecast.forecastDays.last()) Divider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
fun DailyItem(day: ForecastDay, convertTemp: (Double) -> Int) {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(day.date) ?: Date()
    val shortDay = when (SimpleDateFormat("EEEE", Locale.CHINESE).format(date)) {
        "星期一" -> "周一"
        "星期二" -> "周二"
        "星期三" -> "周三"
        "星期四" -> "周四"
        "星期五" -> "周五"
        "星期六" -> "周六"
        "星期日" -> "周日"
        else -> SimpleDateFormat("EEEE", Locale.CHINESE).format(date).take(2)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(shortDay, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.width(50.dp))
        Text(getWeatherIcon(day.day.condition.text), fontSize = 28.sp)
        Text("${convertTemp(day.day.minTempC)}° / ${convertTemp(day.day.maxTempC)}°", fontSize = 14.sp, color = Color.White)
        if (day.day.chanceOfRain > 0) Text("☔ ${day.day.chanceOfRain}%", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
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
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
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
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LifestyleChip("🌅", "日出", weather.forecast.forecastDays[0].astro.sunrise)
                LifestyleChip("🌇", "日落", weather.forecast.forecastDays[0].astro.sunset)
                LifestyleChip("💧", "湿度", "${weather.current.humidity}%")
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LifestyleChip("💨", "风速", "${weather.current.windKph.toInt()} km/h")
                LifestyleChip("☀️", "紫外线", "${weather.current.uv}")
                LifestyleChip("👕", "穿衣", getSuggestion(weather.current.tempC.toInt()))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LifestyleChip("⏲️", "气压", "$pressure hPa")
                LifestyleChip("👁️", "能见度", "$visibility km")
                LifestyleChip("🌧️", "降雨概率", "$rainChance%")
            }
        }
    }
}

@Composable
fun LifestyleChip(icon: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(icon, fontSize = 24.sp) }
        }
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
fun FavoritesTab(viewModel: WeatherViewModel) {
    val cities = listOf("Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Chengdu", "Hangzhou", "Wuhan", "Xi'an")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cities) { city ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth().clickable { viewModel.searchWeather(city) }) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
    onShowAbout: () -> Unit,
    onShowPrivacy: () -> Unit,
    isCelsius: Boolean,
    onUnitChange: (Boolean) -> Unit,
    isNotificationEnabled: Boolean,
    onNotificationToggle: (Boolean) -> Unit,
    context: Context  // 新增 context 参数
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("主题风格", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip("默认", "🌤️", currentTheme == WeatherTheme.DEFAULT) { onThemeChange(WeatherTheme.DEFAULT) }
                        ThemeChip("可爱粉", "🎀", currentTheme == WeatherTheme.CUTE) { onThemeChange(WeatherTheme.CUTE) }
                        ThemeChip("唯美紫", "🌸", currentTheme == WeatherTheme.ROMANTIC) { onThemeChange(WeatherTheme.ROMANTIC) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip("科技蓝", "💙", currentTheme == WeatherTheme.TECH) { onThemeChange(WeatherTheme.TECH) }
                        ThemeChip("自然绿", "🌿", currentTheme == WeatherTheme.NATURE) { onThemeChange(WeatherTheme.NATURE) }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌡️", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("温度单位", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Text("摄氏度 / 华氏度", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                    Switch(checked = isCelsius, onCheckedChange = onUnitChange, thumbContent = { Text(if (isCelsius) "°C" else "°F", fontSize = 10.sp) })
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔔", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("天气通知", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Text("每日天气推送", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                    Switch(checked = isNotificationEnabled, onCheckedChange = onNotificationToggle)
                }
            }
        }
        // 新增：测试通知卡片
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { NotificationHelper.sendTestNotification(context) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔔", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("测试通知", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Text("点击发送测试通知", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                }
            }
        }
        item {
            SettingCard("📄", "隐私协议", "用户隐私保护", onShowPrivacy)
        }
        item {
            SettingCard("ℹ️", "关于", "版本 1.0.0", onShowAbout)
        }
    }
}

@Composable
fun ThemeChip(name: String, icon: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(30.dp), color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f), modifier = Modifier.clickable { onClick() }) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.width(4.dp))
            Text(name, fontSize = 12.sp, color = if (isSelected) Color(0xFF1A237E) else Color.White)
        }
    }
}

@Composable
fun SettingCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    Text(subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
            if (title != "关于" && title != "隐私协议") Text("开发中", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            else Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("😵", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(message, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
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
    val offsetY by infiniteTransition.animateFloat(-50f, 2000f, infiniteRepeatable(tween(12000 + particle.delay, easing = LinearEasing), RepeatMode.Restart))
    Canvas(modifier = Modifier.fillMaxSize().offset(x = (particle.x * 350).dp, y = offsetY.dp)) {
        drawCircle(Color.White.copy(alpha = 0.08f), radius = 2f)
    }
}

@Composable
fun AnimatedSun(rotationAngle: Float, pulseScale: Float) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp, end = 40.dp), contentAlignment = Alignment.TopEnd) {
        Canvas(modifier = Modifier.size(90.dp).graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }) {
            val cx = size.width / 2
            val cy = size.height / 2
            drawCircle(Color.White.copy(alpha = 0.1f), 50f, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.2f), 40f, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.35f), 28f, Offset(cx, cy))
            for (i in 0..11) {
                val angle = Math.toRadians((i * 30.0 + rotationAngle).toDouble())
                val start = Offset(cx + 32f * cos(angle).toFloat(), cy + 32f * sin(angle).toFloat())
                val end = Offset(cx + 52f * cos(angle).toFloat(), cy + 52f * sin(angle).toFloat())
                drawLine(Color.White.copy(alpha = 0.25f), start, end, strokeWidth = 2.5f)
            }
        }
    }
}

@Composable
fun HourDetailDialog(hour: HourWeather, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("详细预报") }, text = {
        Column {
            Text("时间: ${hour.time}")
            Spacer(Modifier.height(8.dp))
            Text("温度: ${hour.tempC.toInt()}°C")
            Spacer(Modifier.height(8.dp))
            Text("天气: ${hour.condition.text}")
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("关于天气助手") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Text("天气助手", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
            Text("Weather Assistant", fontSize = 14.sp, color = Color.Gray)
            Divider()
            AboutItem("📱", "版本", "1.0.0")
            AboutItem("👨‍💻", "开发者", "梅梅子")
            AboutItem("📅", "更新日期", "2026年4月")
            AboutItem("🌐", "数据来源", "WeatherAPI.com")
            AboutItem("🎨", "主题", "5种主题可选")
            Spacer(Modifier.height(12.dp))
            Divider()
            Text("一款简洁美观的天气应用\n为您提供准确的天气信息", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Text("© 2026 Weather Assistant", fontSize = 10.sp, color = Color.Gray.copy(alpha = 0.6f))
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("隐私协议") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 350.dp)) {
            Text("感谢您使用天气助手！我们非常重视您的隐私保护。\n\n" +
                    "1. 信息收集\n本应用仅收集您主动提供的城市名称和位置信息（需授权）。\n\n" +
                    "2. 权限使用\n• 网络权限：获取天气数据\n• 位置权限：自动获取当前城市天气（可拒绝）\n\n" +
                    "3. 数据存储\n偏好设置仅保存在本地设备，不会上传。\n\n" +
                    "4. 第三方服务\n使用 WeatherAPI.com 提供天气数据，不包含个人身份信息。\n\n" +
                    "5. 信息共享\n不会与任何第三方共享您的个人信息。\n\n" +
                    "6. 政策更新\n如有更新会提示。\n\n如您有任何疑问，请联系：woucxzae@163.com")
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("同意并关闭") } })
}

@Composable
fun AboutItem(icon: String, text: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Row { Text(icon, fontSize = 16.sp); Spacer(Modifier.width(12.dp)); Text(text, fontSize = 14.sp, color = Color.Gray) }
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