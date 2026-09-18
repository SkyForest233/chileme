package com.agon.app.data

import androidx.datastore.preferences.core.edit

/**
 * 仓库的**设置写入域：外观、同步节奏、分类阈值、分类与位置清单（都只写自己那一两个 key）**（路线图 #5c，2026-09-18）。
 *
 * 形状为什么是「同包 `internal` 扩展函数」而不是类成员、以及为什么不选"门面转发"和"领域对象"，
 * 完整取舍写在 `RepositoryCore.kt` 的文件 KDoc 里（一次说清，别处只指路）。
 *
 * 对外调用写法一个字没变：同包内扩展函数用隐式接收者就能解析；唯一要改的是**别的包**的调用方
 * —— `AppViewModel` 为搬走的每个函数加一行 import（本仓禁通配导入）。
 */

internal suspend fun FoodRepository.setCategoryThreshold(categoryId: String, days: Int) {
    dataStore.edit { prefs ->
        val current = decodeThresholds(prefs[thresholdsKey]).toMutableMap()
        current[categoryId] = days.coerceIn(1, 365)
        prefs[thresholdsKey] = json.encodeToString(current.toMap())
    }
}

internal suspend fun FoodRepository.setCategories(categories: List<CategoryDef>) {
    dataStore.edit { prefs ->
        prefs[categoriesKey] = json.encodeToString(categories)
    }
}

internal suspend fun FoodRepository.setLocations(locations: List<String>) {
    dataStore.edit { prefs ->
        prefs[locationsKey] = json.encodeToString(locations)
    }
}

internal suspend fun FoodRepository.setDynamicColor(enabled: Boolean) {
    dataStore.edit { it[dynamicColorKey] = enabled }
}

internal suspend fun FoodRepository.setDarkMode(mode: Int) {
    dataStore.edit { it[darkModeKey] = mode }
}

internal suspend fun FoodRepository.setPalette(name: String) {
    dataStore.edit { it[paletteKey] = name }
}

internal suspend fun FoodRepository.setThemeStyle(name: String) {
    dataStore.edit { it[themeStyleKey] = name }
}

internal suspend fun FoodRepository.setFloatingNav(enabled: Boolean) {
    dataStore.edit { it[floatingNavKey] = enabled }
}

internal suspend fun FoodRepository.setAutoSyncDays(days: Int) {
    dataStore.edit { it[autoSyncDaysKey] = days.coerceIn(0, 30) }
}

internal suspend fun FoodRepository.setLastAutoSyncEpochDay(epochDay: Long) {
    dataStore.edit { it[lastAutoSyncEpochDayKey] = epochDay.toString() }
}

internal suspend fun FoodRepository.setLastSync(text: String) {
    dataStore.edit { it[lastSyncKey] = text }
}
