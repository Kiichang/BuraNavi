package com.example.buranavi.presentation

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

// WearableListenerService: スマホからのメッセージを裏側で待ち受け続けるサービス
class DestinationListenerService : WearableListenerService() {

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )

    // ★追加:DataItemが変化した(新しく送られてきた)ときに自動的に呼ばれるメソッド
    override fun onDataChanged(
        dataEvents: DataEventBuffer
    ) {
        super.onDataChanged(dataEvents)

        for (event in dataEvents) {

            if (
                event.type != DataEvent.TYPE_CHANGED ||
                event.dataItem.uri.path != "/destination"
            ) {
                continue
            }

            val dataMap =
                DataMapItem
                    .fromDataItem(event.dataItem)
                    .dataMap

            // 必須キーがすべて存在するか確認
            if (
                !dataMap.containsKey("latitude") ||
                !dataMap.containsKey("longitude") ||
                !dataMap.containsKey("timestamp")
            ) {

                continue
            }

            val latitude =
                dataMap.getDouble("latitude")

            val longitude =
                dataMap.getDouble("longitude")

            val timestamp =
                dataMap.getLong("timestamp")

            // 数値として有効か確認
            if (
                !latitude.isFinite() ||
                !longitude.isFinite()
            ) {

                continue
            }

            // 緯度経度の範囲確認
            if (
                latitude !in -90.0..90.0 ||
                longitude !in -180.0..180.0
            ) {

                continue
            }

            // timestampの最低限の妥当性確認
            if (timestamp <= 0L) {

                continue
            }

            serviceScope.launch {

                try {

                    val saved =
                        saveDestinationIfNewer(
                            applicationContext,
                            latitude,
                            longitude,
                            timestamp
                        )

                    if (saved) {

                        // 保存成功後にだけ振動
                        vibrateOnReceived()

                    }

                } catch (_: Exception) {

                }
            }
        }
    }

    // ★追加:端末を短く振動させる関数
    private fun vibrateOnReceived() {
        // Android 12(API 31)以降と、それより前のバージョンで取得方法が異なるため分岐する
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // VibratorManager経由で取得する新しい方式
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            // 従来の取得方式(古いAndroidバージョン向け)
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // VibrationEffect.createOneShot: 「1回だけの振動」を作る
        //   第1引数:振動させる時間(ミリ秒) → 200ミリ秒(0.2秒)の短い振動
        //   第2引数:振動の強さ → DEFAULT_AMPLITUDEはシステムの標準的な強さを使う指定
        val vibrationEffect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(vibrationEffect)
    }

    override fun onDestroy() {

        serviceScope.cancel()

        super.onDestroy()
    }

}