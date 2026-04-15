package com.example.weatherapp

import android.content.Context
import androidx.compose.ui.graphics.Color

// 主题类型
enum class WeatherTheme(val displayName: String) {
    DEFAULT("默认"),
    CUTE("可爱粉"),
    ROMANTIC("唯美紫"),
    TECH("科技蓝"),
    NATURE("自然绿")
}

// 主题颜色配置
data class ThemeColors(
    val primary: Color,
    val secondary: Color,
    val gradientStart: Color,
    val gradientEnd: Color
)

object ThemeConfig {
    fun defaultColors(): ThemeColors = ThemeColors(
        primary = Color(0xFF1A237E),
        secondary = Color(0xFF0D47A1),
        gradientStart = Color(0xFF1A237E),
        gradientEnd = Color(0xFF0D47A1)
    )

    fun cuteColors(): ThemeColors = ThemeColors(
        primary = Color(0xFFE91E63),
        secondary = Color(0xFFF48FB1),
        gradientStart = Color(0xFFFF9A9E),
        gradientEnd = Color(0xFFFECFEF)
    )

    fun romanticColors(): ThemeColors = ThemeColors(
        primary = Color(0xFF9C27B0),
        secondary = Color(0xFFE1BEE7),
        gradientStart = Color(0xFFE1BEE7),
        gradientEnd = Color(0xFFCE93D8)
    )

    fun techColors(): ThemeColors = ThemeColors(
        primary = Color(0xFF00BCD4),
        secondary = Color(0xFF80DEEA),
        gradientStart = Color(0xFF84FFFF),
        gradientEnd = Color(0xFF18FFFF)
    )

    fun natureColors(): ThemeColors = ThemeColors(
        primary = Color(0xFF4CAF50),
        secondary = Color(0xFFA5D6A7),
        gradientStart = Color(0xFFC8E6C9),
        gradientEnd = Color(0xFFA5D6A7)
    )
}

object ThemePreference {
    private const val PREFS_NAME = "weather_prefs"
    private const val KEY_THEME = "selected_theme"

    fun saveTheme(context: Context, theme: WeatherTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun getTheme(context: Context): WeatherTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeName = prefs.getString(KEY_THEME, WeatherTheme.DEFAULT.name)
        return try {
            WeatherTheme.valueOf(themeName ?: WeatherTheme.DEFAULT.name)
        } catch (e: Exception) {
            WeatherTheme.DEFAULT
        }
    }
}