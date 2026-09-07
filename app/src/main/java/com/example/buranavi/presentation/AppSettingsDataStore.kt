package com.example.buranavi.presentation

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ★追加:アプリの見た目・表示設定をひとまとめにして扱うためのデータクラス
//    dataクラス:値をまとめて持ち運ぶための入れ物(自動でequalsやtoStringが生成される)
data class AppSettings(
    val backgroundColor: Long, // 色は「0xFFRRGGBB」のような形式の数値(Long型)として保存する
    val arrowColor: Long,
    val arrowStyle: Int,       // ArrowStyleのenumを「何番目か」という数値に変換して保存する
    val showTime: Boolean,
    val showBattery: Boolean,
    val showSteps: Boolean,
    val showHeartRate: Boolean
) {
    companion object {
        // ★追加:まだ一度も設定を保存していない(初回起動時)に使うデフォルト値
        val DEFAULT = AppSettings(
            backgroundColor = 0xFF000000L, // 黒
            arrowColor = 0xFFFF0000L,      // 赤
            arrowStyle = 0,                // ArrowStyle.TRIANGLE(三角形、今までの矢印と同じ)
            showTime = false,
            showBattery = false,
            showSteps = false,
            showHeartRate = false
        )
    }
}

// ★追加:DataStoreの中で使うキーをまとめておく置き場所
private object AppSettingsKeys {
    val BACKGROUND_COLOR = longPreferencesKey("background_color")
    val ARROW_COLOR = longPreferencesKey("arrow_color")
    val ARROW_STYLE = intPreferencesKey("arrow_style")
    val SHOW_TIME = booleanPreferencesKey("show_time")
    val SHOW_BATTERY = booleanPreferencesKey("show_battery")
    val SHOW_STEPS = booleanPreferencesKey("show_steps")
    val SHOW_HEART_RATE = booleanPreferencesKey("show_heart_rate")
}

// ★追加:各設定項目を1つずつ更新するための関数群
//    (設定画面で「1項目タップされるたびに、その項目だけ保存する」という使い方を想定)
suspend fun saveBackgroundColor(context: Context, colorValue: Long) {
    context.dataStore.edit { it[AppSettingsKeys.BACKGROUND_COLOR] = colorValue }
}

suspend fun saveArrowColor(context: Context, colorValue: Long) {
    context.dataStore.edit { it[AppSettingsKeys.ARROW_COLOR] = colorValue }
}

suspend fun saveArrowStyle(context: Context, styleOrdinal: Int) {
    context.dataStore.edit { it[AppSettingsKeys.ARROW_STYLE] = styleOrdinal }
}

suspend fun saveShowTime(context: Context, value: Boolean) {
    context.dataStore.edit { it[AppSettingsKeys.SHOW_TIME] = value }
}

suspend fun saveShowBattery(context: Context, value: Boolean) {
    context.dataStore.edit { it[AppSettingsKeys.SHOW_BATTERY] = value }
}

suspend fun saveShowSteps(context: Context, value: Boolean) {
    context.dataStore.edit { it[AppSettingsKeys.SHOW_STEPS] = value }
}

suspend fun saveShowHeartRate(context: Context, value: Boolean) {
    context.dataStore.edit { it[AppSettingsKeys.SHOW_HEART_RATE] = value }
}

// ★追加:保存されているすべての設定を、まとめてリアルタイム監視する関数
fun observeAppSettings(context: Context): Flow<AppSettings> {
    return context.dataStore.data.map { preferences ->
        AppSettings(
            backgroundColor = preferences[AppSettingsKeys.BACKGROUND_COLOR] ?: AppSettings.DEFAULT.backgroundColor,
            arrowColor = preferences[AppSettingsKeys.ARROW_COLOR] ?: AppSettings.DEFAULT.arrowColor,
            arrowStyle = preferences[AppSettingsKeys.ARROW_STYLE] ?: AppSettings.DEFAULT.arrowStyle,
            showTime = preferences[AppSettingsKeys.SHOW_TIME] ?: AppSettings.DEFAULT.showTime,
            showBattery = preferences[AppSettingsKeys.SHOW_BATTERY] ?: AppSettings.DEFAULT.showBattery,
            showSteps = preferences[AppSettingsKeys.SHOW_STEPS] ?: AppSettings.DEFAULT.showSteps,
            showHeartRate = preferences[AppSettingsKeys.SHOW_HEART_RATE] ?: AppSettings.DEFAULT.showHeartRate
        )
    }
}