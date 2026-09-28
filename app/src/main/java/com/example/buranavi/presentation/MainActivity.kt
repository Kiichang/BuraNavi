package com.example.buranavi.presentation
// ↑ パッケージ名は自分のプロジェクトに合わせて自動生成されたものをそのまま使ってください

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.Sensor // ★追加:センサーの種類を指定するために必要
import android.hardware.SensorEvent // ★追加:センサーの値が変化したときに渡されるデータ
import android.hardware.SensorEventListener // ★追加:センサーの値変化を受け取るための仕組み
import android.hardware.SensorManager // ★追加:各種センサーを管理・取得するためのシステムサービス
import android.location.Location // ★追加:目的地との計算にLocationクラスを直接使うため
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.android.horologist.compose.ambient.AmbientAware
// ★追加: Health Services
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas // ★追加:矢印を自分で描画するためのCompose部品
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// ★追加:背景色を指定するために必要
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.MaterialTheme  // ← Wear OS用のみでOK
import androidx.wear.compose.material.Text            // ← Wear OS用のみでOK
import com.google.android.gms.location.*
// ★追加:MessageClientとMessageListenerを使うために必要
// ★追加:Flowを画面(Compose)で監視するために必要
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
// ★追加:画面の常時点灯設定に必要
import android.view.WindowManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material3.IconButton
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
// ★追加:バッテリー情報を扱うためのimport
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
// ★追加:時刻表示に必要なimport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import com.example.buranavi.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import android.os.SystemClock

// ★追加:バッテリー残量を保持する状態
private val batteryLevelText = mutableStateOf("-")

// ★追加:バッテリー残量が変化するたびに呼ばれる受信機(レシーバー)
private val batteryReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Intent(システムからの通知)の中から、現在の残量と満充電時の値を取り出す
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        if (level != -1 && scale != -1) {
            // パーセンテージに変換(例:level=80, scale=100 → 80%)
            val batteryPercent = (level * 100) / scale
            batteryLevelText.value = "$batteryPercent%"
        }
    }
}

// ★追加:同じ目的地に対して、到着通知を既に鳴らしたかどうか(2重通知防止)
private var hasNotifiedArrival = false

// ★追加:「ナビ終了」ボタンを表示するかどうか
private val showEndNavButton = mutableStateOf(false)

// ★追加:到着通知の距離(Composable側で毎回、設定値に更新される)
private var arrivalNotificationDistanceMeters = 100

// ★追加:最後に位置情報が届いてから、この時間(ミリ秒)を超えたら「途絶」とみなす
private const val GPS_STALE_THRESHOLD_MS = 15_000L
// ★追加:接近通知を出してよい、位置情報の誤差(メートル)の上限
private const val ARRIVAL_MAX_ACCURACY_METERS = 50f

// ★追加:最後に位置情報が届いた時刻(端末起動からの経過ミリ秒)。0は「まだ一度も届いていない」
private var lastFixElapsedMs = 0L
// ★追加:GPSが途絶(または未取得)の状態かどうか。画面表示に使う
private val isGpsStale = mutableStateOf(false)
// ★追加:システムから「今は位置情報が使えない」と通知されたかどうか
private val locationUnavailable = mutableStateOf(false)

// ComponentActivity: Composeを使う画面のベースとなるクラス
// ★変更点:SensorEventListenerを実装(=センサーの値変化を受け取れるようにする)
class MainActivity : ComponentActivity(), SensorEventListener {

    // ★追加:到着通知用の振動(座標受信時とは違うパターンにして区別する)
    private fun vibrateOnArrivalNotification() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        // 短い振動を3回繰り返すパターン
        val pattern = longArrayOf(0, 150, 100, 150, 100, 150)
        vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
    }

    // ★追加:距離(メートル)を、表示用の「数値」と「単位」のペアに変換する関数
    //    1000m未満: 整数のメートル表示
    //    1000m以上: 小数点第1位までのキロメートル表示
    // ★変更:decimalPlacesを引数として受け取るようにした
    private fun formatDistance(meters: Float, decimalPlaces: Int): Pair<String, String> {
        return if (meters >= 1000f) {
            val km = meters / 1000f
            // ★変更:桁数を動的に組み立てる("%.1f" や "%.2f" になる)
            val formatPattern = "%.${decimalPlaces}f"
            val kmText = String.format(Locale.getDefault(), formatPattern, km)
            Pair(kmText, "km")
        } else {
            Pair(meters.toInt().toString(), "m")
        }
    }

    // ★追加:歩数・心拍数を保持するセンサー変数
    // private var stepCounterSensor: Sensor? = null
    private var heartRateSensor: Sensor? = null

    // ★追加:画面表示用の状態(取得できるまでは "-" と表示しておく)
    private val stepsText = mutableStateOf("-")
    private val heartRateText = mutableStateOf("-")

    // ★追加:歩数センサーは「起動からの累計歩数」を返す仕様のため、
    //    アプリを開いた時点の歩数を基準値として記録しておき、そこからの増分を「今回の歩数」として表示する
    // ★変更:1日の歩数に変更のため削除
    // private var stepCounterBaseline: Float? = null

    // ★追加:歩数センサーを取得して登録する関数
    // ★変更:1日の歩数に変更のため削除
    //    private fun registerStepCounterSensor() {
    //        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    //        stepCounterSensor?.also { sensor ->
    //            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    //        }
    //    }

    // ★Health Services
    private val passiveMonitoringClient by lazy {
        HealthServices.getClient(this).passiveMonitoringClient
    }
    private val passiveListenerCallback =
        object : PassiveListenerCallback {
            override fun onNewDataPointsReceived(
                dataPoints: DataPointContainer
            ) {
                val dailySteps =
                    dataPoints
                        .getData(DataType.STEPS_DAILY)
                        .lastOrNull()
                        ?.value
                if (dailySteps != null) {
                    stepsText.value = dailySteps.toString()
                }
            }
            override fun onRegistrationFailed(
                throwable: Throwable
            ) {
                Log.e(
                    "HealthServices",
                    "歩数取得の登録に失敗",
                    throwable
                )
                stepsText.value = "-"
            }
        }

    private fun registerDailySteps() {
        lifecycleScope.launch {
            try {
                val capabilities =
                    passiveMonitoringClient.getCapabilities()

                if (
                    DataType.STEPS_DAILY !in
                    capabilities.supportedDataTypesPassiveMonitoring
                ) {
                    Log.e(
                        "HealthServices",
                        "STEPS_DAILYはこの端末でサポートされていません"
                    )

                    stepsText.value = "-"
                    return@launch
                }

                val config =
                    PassiveListenerConfig.builder()
                        .setDataTypes(
                            setOf(DataType.STEPS_DAILY)
                        )
                        .build()

                passiveMonitoringClient.setPassiveListenerCallback(
                    config,
                    passiveListenerCallback
                )

            } catch (e: Exception) {
                Log.e(
                    "HealthServices",
                    "日次歩数の登録に失敗",
                    e
                )

                stepsText.value = "-"
            }
        }
    }

    // ★追加:心拍センサーを取得して登録する関数
    private fun registerHeartRateSensor() {
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        heartRateSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    // ③ 位置情報を取得するためのクライアント(Googleのライブラリが用意してくれているクラス)
    //    lateinit var = 「後で必ず値を入れます」という宣言(onCreateの中で初期化する)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ★追加:センサーを管理するシステムサービス
    private lateinit var sensorManager: SensorManager
    // ★追加:回転ベクトルセンサー(端末の向きを計算するためのセンサー)
    private var rotationVectorSensor: Sensor? = null

    // ④ 「権限をユーザーに許可してもらうためのランチャー」を用意する
    //    registerForActivityResultは「結果が返ってくる処理」を登録する仕組み
    //    ユーザーが許可/拒否をタップした後、isGranted(true/false)がここに返ってくる
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // permissionsは Map<String, Boolean> の形で返ってくる(各権限名がキー、許可結果が値)
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

            if (locationGranted) {
                startLocationUpdates()
            } else {
                locationText.value = "位置情報の許可が必要です"
            }

            // ★追加:歩数・心拍センサーは、許可されていればそれぞれ登録処理を呼ぶ
            //   (許可されなかった場合は、該当項目を表示しないだけにして、アプリ自体は問題なく動くようにする)
            val activityRecognitionGranted = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: false
            val bodySensorsGranted = permissions[Manifest.permission.BODY_SENSORS] ?: false

            if (activityRecognitionGranted) {
                registerDailySteps()
            }
            if (bodySensorsGranted) {
                registerHeartRateSensor()
            }
        }

    // ⑤ 画面に表示する「緯度・経度の文字列」を保持する変数
    //    mutableStateOf = Composeにおいて「この値が変わったら画面を再描画してね」という目印をつけた変数
    private val locationText = mutableStateOf("取得中...")

    // ★追加:距離(メートル)を表示するための状態変数
    // ★変更:文字列ではなく、生の距離(メートル)をFloat?型で保持する
    //    nullは「まだ計算されていない」状態を表す
    private val distanceInMeters = mutableStateOf<Float?>(null)
    // ★追加:目的地への方位角(0〜360度、真北=0度)を表示するための状態変数
    // private val bearingText = mutableStateOf("-")

    // ★変更:bearingTextは矢印の角度計算に使うので、Float型の状態として持っておく
    //    (画面表示用の文字列は別途Text側で丸めて使う)
    private var targetBearing by mutableFloatStateOf(0f) // 目的地への方位角(真北基準)
    private var currentAzimuth by mutableFloatStateOf(0f) // ウォッチ自身が向いている方位角(真北基準)

    // ★追加:目的地の緯度・経度(今は固定値でテスト。例として東京タワーの座標を設定)
    //    実際に使う際は、自分が試したい場所の緯度経度に書き換えて試してください
    // private val destinationLatitude = 35.6586
    // private val destinationLongitude = 139.7454

    // ★追加:現在の目的地を保持する状態(まだ受信していない場合はnull)
    //    これを直接書き換えるのではなく、DataStoreからのFlowを監視して自動更新させる
    private var currentDestination by mutableStateOf<Pair<Double, Double>?>(null)

    // ★追加:今どちらの画面を表示しているかを管理する状態
    //    シンプルなBoolean(true/false)で「設定画面を表示中かどうか」を表す
    private var showSettingsScreen by mutableStateOf(false)

    // ★追加:歯車アイコンを表示するかどうかの状態
    private var showGearIcon by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ★追加:一定時間操作がなくても、画面が消えたりウォッチフェイスに戻ったりしないようにする
        //    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON:
        //    「このActivityが表示されている間は、システムの自動スリープを無視して画面を点けたままにする」という指定
        // window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // ⑥ 位置情報クライアントを初期化(このActivityに紐付ける)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // ★追加:センサーマネージャーを初期化
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        // ★追加:端末に搭載されている「回転ベクトルセンサー」を取得
        //    (機種によっては存在しない場合もあるので、nullの可能性を考慮した型になっている)
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // ⑦ Composeで画面のUIを組み立てる部分
        setContent {

            // ★追加:現在時刻を保持する状態。1分ごとに自動更新する
            var currentTimeText by remember { mutableStateOf("") }

            // ★追加:LaunchedEffect(Unit):画面が表示されている間、ずっと動き続ける処理
            //    無限ループで「時刻を更新→1分待つ→また更新」を繰り返す
            LaunchedEffect(Unit) {
                while (true) {
                    // val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    // currentTimeText = sdf.format(Date())
                    // delay(60000) // 60000ミリ秒 = 1分待つ
                    val now = System.currentTimeMillis()
                    val sdf = SimpleDateFormat(
                        "HH:mm",
                        Locale.getDefault()
                    )
                    currentTimeText = sdf.format(Date(now))

                    // 次の「00秒」まで待つ
                    val delayUntilNextMinute = 60_000L - (now % 60_000L)
                    delay(delayUntilNextMinute)
                }
            }

            // ★追加:1秒ごとに、GPSが途絶しているかどうかを判定する
            LaunchedEffect(Unit) {
                while (true) {
                    val elapsed = SystemClock.elapsedRealtime() - lastFixElapsedMs
                    isGpsStale.value = lastFixElapsedMs == 0L ||
                            elapsed > GPS_STALE_THRESHOLD_MS
                    delay(1000)
                }
            }

            // ★追加:DataStoreのFlowを画面側で監視する
            //    collectAsState(): Flowから流れてくる値を、Composeが再描画できる「状態」に変換してくれる
            //    initial = null: まだ最初の値が届く前は、nullとして扱う
            val destinationFromStore by observeDestination(applicationContext)
                .collectAsState(initial = null)

            // ★追加:Flowから新しい値が届くたびに、currentDestinationを更新する
            //    (LaunchedEffectを使うのが一般的だが、まずはシンプルにこの形で進める)
            currentDestination = destinationFromStore

            // ★追加:保存されている見た目の設定を監視する
            val appSettings by observeAppSettings(applicationContext)
                .collectAsState(initial = AppSettings.DEFAULT)

            // ★追加:設定値を、位置情報コールバック側からも参照できるクラスのプロパティに反映
            arrivalNotificationDistanceMeters = appSettings.arrivalNotificationDistance

            // ★追加:目的地が変わる(新しく設定される/リセットされる)たびに、通知状態をリセットする
            LaunchedEffect(currentDestination) {
                hasNotifiedArrival = false
                showEndNavButton.value = false
            }

            // ★追加:実際に使う文字色を計算する
            //    syncTextColorWithArrowがONなら矢印色、OFFなら選択された文字色を使う
            val effectiveTextColor = if (appSettings.syncTextColorWithArrow) {
                appSettings.arrowColor
            } else {
                appSettings.textColor
            }

            MaterialTheme {
                // ★追加:AmbientAwareでラップすることで、Ambient状態の変化を検知できるようになる
                //    isAlwaysOnScreen = true:常時表示(Always-On)を有効にする指定
                AmbientAware { ambientStateUpdate ->
                    // ★追加:今、Ambient状態(簡易表示)かどうかを取り出す
                    val isAmbient = ambientStateUpdate.isAmbient
                    // ★追加:showSettingsScreenの値によって、表示する画面を丸ごと切り替える
                    if (showSettingsScreen) {
                        // ★追加:設定画面(中身は次のステップで作り込みます。今は仮の内容)
                        SettingsScreen(
                            currentSettings = appSettings, // ★追加:現在の設定値を渡す
                            onBack = {
                                // 「戻る」が押されたら、メイン画面表示に戻す
                                showSettingsScreen = false
                            }
                        )
                    } else {
                        // ★追加:歯車アイコンをタップから3秒後に自動的に隠す処理
                        //    LaunchedEffect(showGearIcon): showGearIconの値が変わるたびに、この中の処理が実行される
                        LaunchedEffect(showGearIcon) {
                            if (showGearIcon) {
                                delay(3000) // 3秒待つ
                                showGearIcon = false // 3秒経ったら非表示に戻す
                            }
                        }
                        // ★変更:ColumnからBoxに変更。Box内の要素は「重ねて」配置でき、
                        //    それぞれ個別に画面上の位置(上下左右)を指定できる
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // ★変更:固定のColor.Blackから、設定値の色を反映するように変更
                                // ★変更:Ambient中は常に黒背景にする(設定色を無視。焼き付き・省電力のため)
                                .background(if (isAmbient) Color.Black else Color(appSettings.backgroundColor))
                                // ★追加:画面のどこかをタップしたことを検知する仕組み
                                // ★変更:Ambient中はタップ検知を無効にする(どのみち反応しないため)
                                .let { modifier ->
                                    if (isAmbient) modifier else modifier.pointerInput(Unit) {
                                        detectTapGestures(onTap = { showGearIcon = true })
                                    }
                                }
                        ) {
                            // ★追加:GPSが途絶しているかどうか(矢印・距離の表示で使う)
                            val gpsStale = isGpsStale.value
                            // ★変更:currentDestinationがある時だけ矢印を描画する
                            if (currentDestination != null) {
                                // ★変更:矢印を画面のちょうど中央に固定配置
                                //    Modifier.align(Alignment.Center):この要素だけを親(Box)の中央に置く指定
                                Canvas(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .align(Alignment.Center)
                                ) {
                                    val arrowRotation = targetBearing - currentAzimuth
                                    rotate(degrees = arrowRotation) {
                                        // ★変更:Ambient中は専用のシンプルな矢印(白の線)、通常時は今まで通りの設定を反映
                                        if (isAmbient) {
                                            // ★変更:途絶中はグレーにする
                                            drawAmbientArrowShape(color = if (gpsStale) Color.Gray else Color.White)
                                        } else {
                                            drawArrowShape(
                                                style = ArrowStyle.values()[appSettings.arrowStyle],
                                                color = if (gpsStale) Color.Gray else Color(appSettings.arrowColor)
                                            )
                                        }
                                    }
                                }
                            }

                            // ★変更:距離表示は、矢印より少し上側に固定配置
                            //    Alignment.TopCenter:上部中央に配置
                            //    padding(top = ...)で、矢印と重ならないよう少し下げて調整
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 時刻は距離の上へ移動
                                if (appSettings.showTime) {
                                    Text(
                                        text = currentTimeText,
                                        // ★変更:Ambient中はColor.White固定、通常時は設定色を反映
                                        color = if (isAmbient) Color.White else Color(effectiveTextColor),
                                        fontSize = 14.sp
                                    )
                                }
                                if (currentDestination == null) {
                                    Text(text = "目的地未設定", color = Color.Yellow)
                                } else {
                                    // ★追加:distanceInMetersがまだnullの場合は「計算中」を考慮しつつ処理
                                    val meters = distanceInMeters.value
                                    if (meters != null) {
                                        // ★変更:formatDistance関数を使って、数値と単位を取得する
                                        // ★変更:appSettings.kmDecimalPlacesを渡すように変更
                                        val (distanceValueText, distanceUnitText) = formatDistance(
                                            meters,
                                            appSettings.kmDecimalPlaces
                                        )
                                        // ★変更:数値と単位を横並びにするため、Rowで囲む
                                        Row(
                                            // ★重要:文字サイズが違う要素を横に並べる際、下端を揃えることで見た目が綺麗になる
                                            verticalAlignment = Alignment.Bottom,
                                            // ★追加:このRow自体にタップ検知を追加(Ambient中は無効にする)
                                            modifier = if (isAmbient) {
                                                Modifier
                                            } else {
                                                Modifier.clickable(
                                                    // ★追加:タップ時の波紋エフェクトを非表示にする(お好みで外してもOK)
                                                    indication = null,
                                                    interactionSource = remember { MutableInteractionSource() }
                                                ) {
                                                    // ★追加:1桁目⇄2桁目を切り替えて保存する
                                                    val newDecimalPlaces = if (appSettings.kmDecimalPlaces == 1) 2 else 1
                                                    lifecycleScope.launch {
                                                        saveKmDecimalPlaces(applicationContext, newDecimalPlaces)
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = distanceValueText, // ★修正:distanceInMeters.value → distanceValueText
                                                color = if (gpsStale) Color.Gray else if (isAmbient) Color.White else Color(effectiveTextColor),
                                                fontSize = 24.sp,
                                                // ★変更:Ambient中は太字を解除(塗りの多い文字は焼き付きリスクが上がるため)
                                                fontWeight = if (isAmbient) FontWeight.Normal else FontWeight.Bold
                                            )
                                            // ★追加:数値と単位の間に少しだけ隙間を空ける
                                            Spacer(modifier = Modifier.width(1.dp))
                                            Text(
                                                text = distanceUnitText, // ★変更:固定の"m"から、判定結果の単位に変更
                                                color = if (gpsStale) Color.Gray else if (isAmbient) Color.White else Color(effectiveTextColor),
                                                fontSize = 16.sp,
                                                // ★追加:単位の文字が数値の下端よりさらに沈んで見えるのを防ぐための微調整
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                    }
                                    // ★追加:GPSが途絶(または未取得)のとき、距離の下に案内を表示する
                                    if (gpsStale) {
                                        Text(text = "GPS待機中", color = Color.Yellow, fontSize = 11.sp)
                                    }
                                }
                            }

                            // ★追加:歯車アイコン本体。showGearIconがtrueの間だけ表示される
                            // ★変更:if文で直接出し分けていた部分を、AnimatedVisibilityに置き換える
                            // ★変更:Ambient中は歯車アイコンを完全に非表示にする
                            if (!isAmbient) {
                                AnimatedVisibility(
                                    visible = showGearIcon,
                                    // ★追加:表示されるときのアニメーション(フェードイン、即時表示)
                                    enter = fadeIn(animationSpec = tween(durationMillis = 0)),
                                    // ★追加:消えるときのアニメーション(フェードアウト、0.5秒かけてゆっくり消える)
                                    exit = fadeOut(animationSpec = tween(durationMillis = 500)),
                                    modifier = Modifier.align(Alignment.CenterEnd) // アイコンの位置
                                ) {
                                    IconButton(
                                        onClick = {
                                            // アイコンがタップされたら設定画面へ切り替える
                                            showSettingsScreen = true
                                        },
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "設定",
                                            tint = if (isAmbient) Color.White else Color(effectiveTextColor)
                                        )
                                    }
                                }
                            }

                            // ★追加:接近通知後に表示される「ナビ終了」ボタン(誤タップ防止の2段階確認付き)
                            if (showEndNavButton.value && currentDestination != null && !isAmbient) {
                                var isConfirmingEndNav by remember { mutableStateOf(false) }

                                Chip(
                                    onClick = {
                                        if (isConfirmingEndNav) {
                                            // 2回目のタップ:実際に目的地をクリアしてナビを終了する
                                            lifecycleScope.launch {
                                                clearDestination(applicationContext)
                                            }
                                            isConfirmingEndNav = false
                                        } else {
                                            // 1回目のタップ:確認状態にする
                                            isConfirmingEndNav = true
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = if (isConfirmingEndNav) "終了しますか？" else "ナビ終了",
                                            fontSize = 10.sp,
                                            lineHeight = 0.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                        )
                                    },
                                    colors = ChipDefaults.chipColors(
                                        backgroundColor = if (isConfirmingEndNav) Color(0xFFBF3D3D) else Color(0xFF333333),
                                    ),
                                    // ★追加:Chip内部の余白を上書きして小さくする
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 0.dp) // 画面下部の情報行と重ならない位置に調整
                                        // ★ここを追加: Chip標準の最小高さ制限（約52dp）を最小化する
                                        .defaultMinSize(minHeight = 1.dp, minWidth = 1.dp)
                                        // ★必要に応じて高さ自体を直接固定指定することも可能です
                                        .height(26.dp)
                                        .fillMaxWidth()
                                )

                                // ★追加:確認状態のまま5秒操作がなければ自動的に元に戻す
                                LaunchedEffect(isConfirmingEndNav) {
                                    if (isConfirmingEndNav) {
                                        delay(5000)
                                        isConfirmingEndNav = false
                                    }
                                }
                            }

                            // ★追加:時刻・バッテリー・歩数・心拍数を、画面下部にまとめて表示する
                            // ★変更:Ambient中はアイコン付きの行を簡略化(アイコン画像を消して文字だけにする)
                            //    アイコン画像は塗りつぶし面積が大きくなりがちなため、Ambient中は避けるのが無難
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 30.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // ★追加:設定でONになっている項目だけを表示する
                                if (appSettings.showBattery) {
                                    if (isAmbient) {
                                        Text(
                                            text = batteryLevelText.value,
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
//                                        Spacer(modifier = Modifier.width(10.dp))
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.battery),
                                                contentDescription = "バッテリー",
                                                tint = if (isAmbient) Color.White else Color(effectiveTextColor),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(0.dp))
                                            Text(
                                                text = batteryLevelText.value,
                                                color = if (isAmbient) Color.White else Color(effectiveTextColor),
                                                fontSize = 12.sp
                                            )
//                                            Spacer(modifier = Modifier.width(10.dp))
                                        }
                                    }
                                }
                                if (appSettings.showSteps) {
                                    if (isAmbient) {
                                        Text(
                                            text = stepsText.value,
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
//                                        Spacer(modifier = Modifier.width(10.dp))
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.footsteps),
                                                contentDescription = "歩数",
                                                tint = if (isAmbient) Color.White else Color(effectiveTextColor),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = stepsText.value,
                                                color = if (isAmbient) Color.White else Color(effectiveTextColor),
                                                fontSize = 12.sp
                                            )
//                                            Spacer(modifier = Modifier.width(10.dp))
                                        }
                                    }
                                }
                                if (appSettings.showHeartRate) {
                                    if (isAmbient) {
                                        Text(
                                            text = heartRateText.value,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                        )
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.heart),
                                                contentDescription = "心拍",
                                                tint = if (isAmbient) Color.White else Color(effectiveTextColor),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = heartRateText.value,
                                                color = if (isAmbient) Color.White else Color(effectiveTextColor),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ⑧ アプリ起動時に権限の状態をチェックする
        checkAndRequestPermissions()
    }

    // ★追加:画面が表示されている間だけセンサーの値を受け取るようにする
    //    (バッテリー消費を抑えるため、画面が見えていないときは止めるのが定石)
    override fun onResume() {
        super.onResume()
        rotationVectorSensor?.also { sensor ->
            // このActivity自身(this)がリスナーとして登録される
            // SENSOR_DELAY_UI: UI表示用に適した更新頻度(頻繁すぎずカクつかない程度)
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        // ★追加:既に許可されている場合のみ、歩数・心拍センサーも再登録する
        // stepCounterSensor?.also { sensor ->
        //     sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        // }
        heartRateSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        // ★追加:バッテリー情報の受信登録
        //    IntentFilter(Intent.ACTION_BATTERY_CHANGED):
        //    「バッテリー残量が変化した」というシステムからの通知だけを受け取る、という絞り込み条件
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    // ★追加:画面から離れたらセンサーの購読を止める(バッテリー節約)
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        // ★追加:バッテリー受信の解除
        unregisterReceiver(batteryReceiver)
    }

    // ★追加:SensorEventListenerを実装したことで必須になるメソッド①
    //    センサーの値が変化するたびに、システムから自動的に呼ばれる
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
            // センサーの生データ(event.values)から「回転行列」を計算する
            // 回転行列:3D空間での端末の傾き・向きを表す数学的なデータ形式
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            // 回転行列から「方位角・ピッチ・ロール」の3つの角度を取り出す
            // orientationAngles[0] = 方位角(azimuth、真北からの角度)← 今回欲しいのはこれ
            // orientationAngles[1] = ピッチ(前後の傾き)← 今回は未使用
            // orientationAngles[2] = ロール(左右の傾き)← 今回は未使用
            val orientationAngles = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // 計算結果は「ラジアン」という単位で返ってくるので、見慣れた「度」に変換する
            val azimuthInRadians = orientationAngles[0]
            val azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()

            // 度の値は-180〜180の範囲で返ってくるので、0〜360度に変換して保持
            currentAzimuth = (azimuthInDegrees + 360) % 360
        }
            // ★追加:歩数センサーの処理
//            Sensor.TYPE_STEP_COUNTER -> {
//                val totalStepsSinceBoot = event.values[0]
//
//                // まだ基準値を記録していなければ、今回の値を基準として保存する
//                if (stepCounterBaseline == null) {
//                    stepCounterBaseline = totalStepsSinceBoot
//                }
//
//                // 基準値からの増分を「今回の歩数」として計算する
//                val stepsThisSession = (totalStepsSinceBoot - (stepCounterBaseline ?: totalStepsSinceBoot)).toInt()
//                stepsText.value = stepsThisSession.toString()
//            }

            // ★追加:心拍センサーの処理
            Sensor.TYPE_HEART_RATE -> {
                val bpm = event.values[0]
                // 心拍センサーは、まだ指(手首)にセンサーが密着していないときなどに「0」を返すことがあるため、
                // 0以下の場合は「取得中」の表示のままにしておく
                if (bpm > 0) {
                    heartRateText.value = bpm.toInt().toString()
                }
            }
        }
    }

    // ★追加:SensorEventListenerを実装したことで必須になるメソッド②
    //    センサー自体の精度が変化したときに呼ばれる(今回は特に処理不要なので空のままでOK)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 今回は使わないが、実装しないとコンパイルエラーになるため空実装として残す
    }

    // ⑨ 権限が既に許可されているかどうかを確認する関数
// ★変更:関数名も内容に合わせて変更(位置情報だけでなく複数権限をまとめてチェックするため)
    private fun checkAndRequestPermissions() {
        val locationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val activityRecognitionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        val bodySensorsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        // ★追加:まだ許可されていない権限だけをリストアップする
        val permissionsToRequest = mutableListOf<String>()
        if (!locationGranted) permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!activityRecognitionGranted) permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (!bodySensorsGranted) permissionsToRequest.add(Manifest.permission.BODY_SENSORS)

        if (permissionsToRequest.isEmpty()) {
            // すべて許可済みの場合、そのまま各機能を開始する
            startLocationUpdates()
            registerDailySteps()
            registerHeartRateSensor()
        } else {
            // 未許可のものだけ、まとめてダイアログでリクエストする
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // ⑩ 実際に位置情報の更新を開始する関数
    //    @SuppressLint("MissingPermission"):
    //    本来ここでも権限チェックのコードが必要だとAndroid Studioが警告を出すが、
    //    呼び出し元(checkLocationPermission)で必ず確認済みのため警告を抑制している
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // 位置情報をどのくらいの頻度・精度で取得するかの設定
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, // 高精度モード(GPSを積極的に使う)
            5000L // 更新間隔:5000ミリ秒 = 5秒ごとに位置を更新
        ).build()

        // 位置情報が更新されるたびに呼ばれる「コールバック」を定義
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // 最新の位置情報を1件取り出す
                val location = result.lastLocation
                if (location != null) {
                    // ★追加:位置が届いた時刻を記録し、途絶状態を解除する
                    lastFixElapsedMs = SystemClock.elapsedRealtime()
                    locationUnavailable.value = false
                    isGpsStale.value = false
                    // 緯度(latitude)と経度(longitude)を文字列にして画面表示用の変数に代入
                    locationText.value =
                        "緯度: ${location.latitude}\n経度: ${location.longitude}"

                    // ★ここから追加:目的地との距離・方位角を計算する処理
                    // ★変更:目的地が設定されている場合のみ、距離・方位を計算する
                    val destination = currentDestination
                    if (destination != null) {
                        // Locationクラスのインスタンスを「目的地用」としてもう1つ作る
                        // (distanceTo/bearingToは「Location同士」で計算するメソッドのため、
                        //  目的地の緯度経度もLocationオブジェクトにしておく必要がある)
                        val destinationLocation = Location("destination").apply {
                            latitude = destination.first
                            longitude = destination.second
                        }

                        // distanceTo(): 現在地から引数のLocationまでの直線距離を「メートル」で返す
                        // ★修正:ローカル変数名を distanceInMeters → rawDistanceMeters に変更(クラスの状態変数と名前が被らないように)
                        val rawDistanceMeters = location.distanceTo(destinationLocation)

                        // bearingTo(): 現在地から見て引数のLocationが「真北から時計回りに何度の方向か」を返す
                        // 戻り値は -180〜180度の範囲なので、0〜360度に変換しておくと後々扱いやすい
                        val rawBearing = location.bearingTo(destinationLocation)

                        // ★変更:targetBearing(Float型の状態)に直接代入するようにした
                        //    これでCanvas内のarrowRotation計算に自動的に反映される
                        targetBearing = (rawBearing + 360) % 360

                        // 画面表示用の変数を更新(小数点以下は見づらいので整数に丸める)
                        // ★変更:整数の文字列化(toInt().toString())をやめ、生の値をそのまま保存
                        // ★修正:distanceMeters → rawDistanceMeters に変更し、クラスの状態変数(distanceInMeters)へ正しく代入する
                        distanceInMeters.value = rawDistanceMeters
                        // bearingText.value = bearingInDegrees.toInt().toString()
                        // ★追加ここまで
                        // ★追加:設定距離以内に入り、かつまだ通知していなければ振動+ボタン表示
                        // ★変更:位置情報の誤差が大きいときは、接近通知の判定を行わない
                        //    誤差の上限は「設定距離」と50mの小さい方(ただし最低15m)にする。
                        //    例えば設定が20mなのに誤差が40mの位置で判定すると、実際は離れていても通知が鳴ってしまうため
                        val maxAccuracy = minOf(ARRIVAL_MAX_ACCURACY_METERS, arrivalNotificationDistanceMeters.toFloat())
                            .coerceAtLeast(15f)
                        val isAccurateEnough = location.hasAccuracy() && location.accuracy <= maxAccuracy
                        if (!hasNotifiedArrival && isAccurateEnough && rawDistanceMeters <= arrivalNotificationDistanceMeters) {
                            hasNotifiedArrival = true
                            vibrateOnArrivalNotification()
                            showEndNavButton.value = true
                        }
                    } else {
                        // 目的地未設定の場合は、距離表示を初期状態にしておく
                        // ★変更:未設定時はnullに戻す
                        distanceInMeters.value = null
                    }
                }
            }
            // ★追加:位置情報が使える状態かどうかが変わったときに、システムから呼ばれる
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    locationUnavailable.value = true
                }
            }
        }

        // ★ここを修正:戻り値のTask(処理結果)を受け取って、成功/失敗をチェックする
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        ).addOnSuccessListener {
            // リクエスト自体が正常に受け付けられた場合にここが呼ばれる
            // (ただし、これは「受付成功」であって「位置情報を取得できた」ではない点に注意)
            locationText.value = "位置情報のリクエスト成功。取得待ち..."
        }.addOnFailureListener { exception ->
            // ★ここが重要:何が原因で失敗しているかがexceptionに入っている
            locationText.value = "エラー: ${exception.message}"
        }

        // ★念のため、Google Play servicesが端末で使える状態かも確認する
        val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(this)
        if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            // Google Play servicesが使えない/古い場合、ここでエラーコードが分かる
            locationText.value = "Google Play services利用不可: コード $resultCode"
        }
    }
}

// ★追加:設定画面のComposable関数
//    @Composable:「これはCompose画面の部品です」という目印(必須のアノテーション)
//    onBack: 「戻る」ボタンが押されたときに、呼び出し元(MainActivity)に伝えるための仕組み
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "設定画面(仮)", color = Color.White)

            Spacer(modifier = Modifier.height(16.dp))

            // ★仮の「戻る」ボタン。次のステップで、ここに実際の設定項目を追加していきます
            IconButton(onClick = onBack) {
                Text(text = "戻る", color = Color.White)
            }
        }
    }
}