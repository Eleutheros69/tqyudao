package com.example.weatherapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_settings")

class DataStoreManager(private val context: Context) {

    companion object {
        private val LAST_CITY = stringPreferencesKey("last_city")
        private val FAVORITE_CITIES = stringPreferencesKey("favorite_cities")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    // 保存最后搜索的城市
    suspend fun saveLastCity(city: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CITY] = city
        }
    }

    // 获取最后搜索的城市
    val lastCity: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_CITY]
    }

    // 保存收藏城市
    suspend fun saveFavoriteCities(cities: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[FAVORITE_CITIES] = cities.joinToString(",")
        }
    }

    // 获取收藏城市
    val favoriteCities: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val citiesStr = preferences[FAVORITE_CITIES] ?: ""
        if (citiesStr.isEmpty()) emptyList() else citiesStr.split(",")
    }

    // 添加收藏
    suspend fun addFavoriteCity(city: String) {
        val currentList = getFavoriteCitiesSync()
        if (!currentList.contains(city)) {
            saveFavoriteCities(currentList + city)
        }
    }

    // 移除收藏
    suspend fun removeFavoriteCity(city: String) {
        val currentList = getFavoriteCitiesSync()
        saveFavoriteCities(currentList - city)
    }

    // 同步获取收藏列表（用于内部调用）
    private suspend fun getFavoriteCitiesSync(): List<String> {
        return context.dataStore.data.map { preferences ->
            val citiesStr = preferences[FAVORITE_CITIES] ?: ""
            if (citiesStr.isEmpty()) emptyList() else citiesStr.split(",")
        }.first()
    }

    // 检查是否已收藏
    suspend fun isFavorite(city: String): Boolean {
        return getFavoriteCitiesSync().contains(city)
    }
}