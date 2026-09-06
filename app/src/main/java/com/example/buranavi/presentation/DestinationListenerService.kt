package com.example.buranavi.presentation

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// WearableListenerService: スマホからのメッセージを裏側で待ち受け続けるサービス
class DestinationListenerService : WearableListenerService() {

    // ★追加:DataItemが変化した(新しく送られてきた)ときに自動的に呼ばれるメソッド
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)

        // Log.e("DestinationListener", "onDataChangedが呼ばれました。件数: ${dataEvents.count}")

        // 複数のデータ変更が一度にまとめて届く可能性があるため、ループで処理する
        for (event in dataEvents) {
            // TYPE_CHANGED: データが新規作成/更新されたことを示す(削除の場合はTYPE_DELETED)
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/destination") {
                // DataMapItem: 送られてきたDataItemの中身を取り出すためのクラス
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val latitude = dataMap.getDouble("latitude")
                val longitude = dataMap.getDouble("longitude")

                Log.d("DestinationListener", "受信(DataItem): 緯度$latitude, 経度$longitude")

                // ★次のステップで、ここで受け取った値を実際に保存する処理を追加します
                // ★追加:受け取った緯度経度をDataStoreに保存する
                //    Serviceクラスにはライフサイクル(画面のような生存期間)がないため、
                //    CoroutineScope(Dispatchers.IO)を自分で用意して、その中で保存処理を実行する
                CoroutineScope(Dispatchers.IO).launch {
                    saveDestination(applicationContext, latitude, longitude)
                    Log.d("DestinationListener", "DataStoreへの保存が完了しました")
                }

                // ★追加:正常に受信できたことをユーザーに伝えるため、ウォッチを振動させる
                vibrateOnReceived()
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
}