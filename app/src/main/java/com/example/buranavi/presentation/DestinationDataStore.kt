package com.example.buranavi.presentation

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ★重要:Context拡張プロパティとしてDataStoreを定義する(Androidの定番の書き方)
//    "destination_settings" という名前のファイルが、アプリ内部に自動的に作られ、
//    そこにデータが保存される(中身はXMLに似た形式のファイルだが、直接触ることはない)
val Context.dataStore by preferencesDataStore(name = "destination_settings")

// ★追加:DataStoreの中で使う「キー」を定義しておく
//    キーは「保存する値を区別するための名前タグ」のようなもの
object DestinationKeys {
    // doublePreferencesKey: Double型(緯度・経度のような小数)の値を保存するためのキーを作る関数
    val LATITUDE = doublePreferencesKey("destination_latitude")
    val LONGITUDE = doublePreferencesKey("destination_longitude")
}

// ★追加:目的地を保存する関数
//    suspend fun = 「時間がかかる可能性がある処理」であることを示す(コルーチンの中でのみ呼び出せる)
suspend fun saveDestination(context: Context, latitude: Double, longitude: Double) {
    // context.dataStore.edit { }:DataStoreの中身を書き換えるためのブロック
    context.dataStore.edit { preferences ->
        preferences[DestinationKeys.LATITUDE] = latitude
        preferences[DestinationKeys.LONGITUDE] = longitude
    }
}

// ★追加:保存されている目的地を「リアルタイムで監視できる形」で取得する関数
//    Flow<Pair<Double, Double>?> = 「値が変わるたびに、新しい値を流し続けてくれる川(ストリーム)」のようなもの
//    まだ一度も保存されていない場合はnullを返す
fun observeDestination(context: Context): Flow<Pair<Double, Double>?> {
    return context.dataStore.data.map { preferences ->
        val latitude = preferences[DestinationKeys.LATITUDE]
        val longitude = preferences[DestinationKeys.LONGITUDE]

        if (latitude != null && longitude != null) {
            Pair(latitude, longitude)
        } else {
            // まだ目的地が一度も保存されていない状態
            null
        }
    }
}