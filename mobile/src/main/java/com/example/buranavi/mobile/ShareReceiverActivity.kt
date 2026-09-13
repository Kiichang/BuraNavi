package com.example.buranavi.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
// ★追加:タイムアウト機能を使うために必要
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.net.CookieManager
import java.net.CookiePolicy
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.coroutineScope

private const val TAG = "BuraNaviShare" // ★追加:このファイル専用のログタグ

// 座標の取得手段を表す区分
enum class LocationSource {
    WEB_VIEW,      // WebView経由
    GEOCODING_API  // API経由
}

class ShareReceiverActivity : ComponentActivity() {

    // ★追加:クラスが初めて使われるタイミングで1回だけ実行される初期化ブロック
    companion object {
        init {
            // ★重要:CookieManagerをアプリ全体のデフォルトとして設定する
            //    ACCEPT_ALL: サーバーから送られてきたCookieをすべて受け入れて保存する
            //    これにより、HttpURLConnectionが自動的にCookieを保存・再送信するようになる
            val cookieManager = CookieManager()
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
            java.net.CookieHandler.setDefault(cookieManager)
        }
    }

    // ★追加:現在開いている通信を覚えておくための変数
    //    @Volatile: 複数スレッドから読み書きされても、常に最新の値が見えるようにする指定
    @Volatile
    private var activeConnection: HttpURLConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val receivedIntent = intent

        // ★追加:受信したIntentの中身(action, type)をまず記録
        Log.e(TAG, "onCreate開始 action=${receivedIntent?.action} type=${receivedIntent?.type}")

        if (receivedIntent?.action == Intent.ACTION_SEND && receivedIntent.type == "text/plain") {
            val sharedText = receivedIntent.getStringExtra(Intent.EXTRA_TEXT)

            // ★追加:共有された生テキストをそのままログに残す(ここが今回一番重要)
            Log.e(TAG, "共有された生テキスト: $sharedText")

            if (sharedText != null) {
                extractCoordinatesAndProceed(sharedText)
            } else {
                Log.e(TAG, "共有テキストがnullでした")
                finish()
            }
        } else {
            Log.e(TAG, "対応していないIntentでした")
            finish()
        }
    }

    // ★追加:失敗時にスマホ自体を振動させる
    private fun vibrateOnFailure() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        // 2回短く振動させることで「成功(1回)」と区別する
        val pattern = longArrayOf(0, 150, 100, 150) // 待機, 振動, 待機, 振動
        vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
    }

    // ★追加:WebViewを使って、JavaScriptによるURL書き換え後の「座標入りURL」を取得する関数
    //    suspendCancellableCoroutine: コールバック形式のAPI(WebViewClient)を、
    //    コルーチンのsuspend関数として扱えるように橋渡しする仕組み
    // ★改善版: WebViewを使って座標を取得する関数
    private suspend fun resolveViaWebView(url: String): Pair<Double, Double>? =
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(this)

            // ① User-AgentをPC版(Desktop)に偽装して、Googleマップにフル版のJSを実行させる
            val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            webView.settings.userAgentString = desktopUserAgent
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true

            // ② 判定用正規表現を拡張（様々なGoogleマップの座標形式に対応）
            // 1. @緯度,経度
            val patternA = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
            // 2. !3d緯度!4d経度
            val patternB = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
            // 3. search/緯度,+経度 または ll=緯度,経度 (モバイル特有の形式に対応)
            val patternC = Regex("""(?:search/|ll=)(-?\d+\.\d+)(?:%2C|,\+|\+|,)(-?\d+\.\d+)""")

            var isResolved = false
            val handler = android.os.Handler(mainLooper)

            // 定期チェック処理
            val checkRunnable = object : Runnable {
                override fun run() {
                    if (isResolved) return
                    val currentUrl = webView.url

                    if (currentUrl != null) {
                        val match = patternA.find(currentUrl)
                            ?: patternB.find(currentUrl)
                            ?: patternC.find(currentUrl)

                        if (match != null) {
                            isResolved = true
                            val lat = match.groupValues[1].toDoubleOrNull()
                            val lng = match.groupValues[2].toDoubleOrNull()

                            if (lat != null && lng != null) {
                                Log.e(TAG, "WebView経由で座標検出成功: $lat, $lng (URL: $currentUrl)")
                                cleanupWebView(webView)
                                if (continuation.isActive) continuation.resume(Pair(lat, lng))
                                return
                            }
                        }
                    }

                    // まだ見つからなければ200ms後に再チェック
                    if (!isResolved) {
                        handler.postDelayed(this, 200)
                    }
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                    super.onPageFinished(view, finishedUrl)
                    Log.e(TAG, "WebViewページ読込中: $finishedUrl")
                    handler.post(checkRunnable)
                }
            }

            // ③ タイムアウト時間を少し延長（PC版描画のJS完了を待つため 10秒に設定）
            handler.postDelayed({
                if (!isResolved) {
                    isResolved = true
                    Log.e(TAG, "WebView経由の解決がタイムアウト")
                    cleanupWebView(webView)
                    if (continuation.isActive) continuation.resume(null)
                }
            }, 10000L)

            continuation.invokeOnCancellation {
                handler.post { cleanupWebView(webView) }
            }

            webView.loadUrl(url)
            handler.post(checkRunnable)
        }

    // クリーンアップ共通関数
    private fun cleanupWebView(webView: WebView) {
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "WebView破棄時の例外: ${e.message}")
        }
    }

    private fun extractCoordinatesAndProceed(sharedText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val watchdog = launch {
                delay(16000L)
                while (isActive) {
                    activeConnection?.disconnect()
                    delay(200L)
                }
            }

            val result = try {
                resolveCoordinatesFromText(sharedText)
            } catch (e: Exception) {
                Log.e(TAG, "強制切断または例外により中断: ${e.message}")
                null
            } finally {
                watchdog.cancel()
            }

            if (result != null) {
                val (coordinates, source) = result
                val (lat, lng) = coordinates

                // ★ここで「どちら経由で取得成功したか」をログ出力！
                when (source) {
                    LocationSource.WEB_VIEW -> {
                        Log.e(TAG, "【成功】WebView経由で座標を取得しました: $lat, $lng")
                    }
                    LocationSource.GEOCODING_API -> {
                        // 目的である「WebViewタイムアウト→API成功」のパターン
                        Log.e(TAG, "【フォールバック成功】WebViewがタイムアウトしたため、Geocoding API経由で座標を取得しました: $lat, $lng")
                    }
                }

                withContext(Dispatchers.Main) {
                    sendDestinationToWatch(lat, lng)
                }
            } else {
                Log.e(TAG, "【失敗】WebView・Geocoding APIのいずれでも座標を取得できませんでした")
                withContext(Dispatchers.Main) {
                    vibrateOnFailure()
                }
            }

            withContext(Dispatchers.Main) { finish() }
        }
    }

    // ★追加:今までextractCoordinatesAndProceedに書いていた抽出ロジックを、
    //    戻り値を返す独立した関数として切り出したもの
    // ★同時並行（レース）方式で最速取得する関数
    private suspend fun resolveCoordinatesFromText(sharedText: String): Pair<Pair<Double, Double>, LocationSource>? {
        val urlRegex = Regex("https?://\\S+")
        val matchedUrl = urlRegex.find(sharedText)?.value ?: return null

        return coroutineScope {
            // ① Geocoding API 側の非同期タスクを開始
            val apiDeferred = async(Dispatchers.IO) {
                val placeName = if (matchedUrl.contains("goo.gl")) {
                    val finalUrl = resolveRedirectUrl(matchedUrl)
                    extractPlaceName(finalUrl ?: "")
                } else {
                    extractPlaceName(matchedUrl)
                }

                if (placeName != null) {
                    geocodeAddress(placeName)
                } else null
            }

            // ② WebView 側の非同期タスクを開始（UIスレッドで動かす）
            val webViewDeferred = async(Dispatchers.Main) {
                resolveViaWebView(matchedUrl)
            }

            // ③ 【重要】select を使って「先に成功した方」の結果を即座に採用！
            val result = select<Pair<Pair<Double, Double>, LocationSource>?> {
                // API 側が先に完了した場合
                apiDeferred.onAwait { apiResult ->
                    if (apiResult != null) {
                        Log.e(TAG, "API 側が勝利！(最速完了)")
                        webViewDeferred.cancel() // 遅れた WebView 側を即キャンセル
                        Pair(apiResult, LocationSource.GEOCODING_API)
                    } else null // APIが失敗した場合は採用せず、WebView側を待つ
                }

                // WebView 側が先に完了した場合
                webViewDeferred.onAwait { webViewResult ->
                    if (webViewResult != null) {
                        Log.e(TAG, "WebView 側が勝利！")
                        apiDeferred.cancel() // 遅れた API 側を即キャンセル
                        Pair(webViewResult, LocationSource.WEB_VIEW)
                    } else null // WebViewが失敗した場合は採用せず、API側を待つ
                }
            }

            // もし片方が null（失敗）で select を抜けた場合、もう片方の完了を待つ
            result ?: run {
                val apiRes = apiDeferred.await()
                if (apiRes != null) return@coroutineScope Pair(apiRes, LocationSource.GEOCODING_API)

                val webViewRes = webViewDeferred.await()
                if (webViewRes != null) return@coroutineScope Pair(webViewRes, LocationSource.WEB_VIEW)

                null
            }
        }
    }

    private fun sendDestinationToWatch(latitude: Double, longitude: Double) {
        val nodeClient = Wearable.getNodeClient(this)
        val dataClient = Wearable.getDataClient(this)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            // ★追加:見つかったノードを記録
            Log.e(TAG, "接続中ノード数: ${nodes.size}, ノード: ${nodes.joinToString { it.displayName }}")

            if (nodes.isEmpty()) {
                Log.e(TAG, "ノードが見つかりません")
                return@addOnSuccessListener
            }

            val putDataMapRequest = PutDataMapRequest.create("/destination").apply {
                dataMap.putDouble("latitude", latitude)
                dataMap.putDouble("longitude", longitude)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }

            val putDataRequest = putDataMapRequest.asPutDataRequest()
            putDataRequest.setUrgent()

            dataClient.putDataItem(putDataRequest)
                .addOnSuccessListener {
                    // ★追加:送信成功ログ
                    Log.e(TAG, "DataItem送信成功")
                }
                .addOnFailureListener { exception ->
                    // ★追加:送信失敗ログ(これが一番怪しい箇所)
                    Log.e(TAG, "DataItem送信失敗: ${exception.message}", exception)
                }
        }.addOnFailureListener { exception ->
            // ★追加:ノード取得自体の失敗ログ
            Log.e(TAG, "ノード取得失敗: ${exception.message}", exception)
        }
    }

    private fun extractPlaceName(url: String): String? {
        val placeNameRegex = Regex("/place/([^/]+)")
        val rawName = placeNameRegex.find(url)?.groupValues?.get(1) ?: return null
        return try {
            URLDecoder.decode(rawName, "UTF-8")
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeFullwidth(input: String): String {
        val sb = StringBuilder()
        for (char in input) {
            val code = char.code
            when {
                code in 0xFF10..0xFF19 -> sb.append((code - 0xFF10 + '0'.code).toChar())
                char == '－' || char == '−' -> sb.append('-')
                else -> sb.append(char)
            }
        }
        return sb.toString()
    }

    private fun removePostalCode(address: String): String {
        return address.replace(Regex("〒\\d{3}-\\d{4}\\s*"), "")
    }

    private fun geocodeAddress(address: String): Pair<Double, Double>? {
        val cleanedAddress = normalizeFullwidth(removePostalCode(address)).trim()
        var result = queryGeocodingApi(cleanedAddress)
        if (result == null) {
            val lastWord = cleanedAddress.split(" ", "　").lastOrNull()
            if (lastWord != null && lastWord != cleanedAddress) {
                result = queryGeocodingApi("$lastWord 東京")
            }
        }
        return result
    }

    private fun queryGeocodingApi(query: String): Pair<Double, Double>? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val apiUrl = URL(
                "https://maps.googleapis.com/maps/api/geocode/json" +
                        "?address=$encodedQuery&language=ja&region=jp&key=${BuildConfig.GEOCODING_API_KEY}"
            )
            val connection = apiUrl.openConnection() as HttpURLConnection
            activeConnection = connection // ★追加

            // ★追加:明示的なタイムアウト(これが無かったのが今回の主原因の1つ)
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            connection.setRequestProperty("X-Android-Package", packageName)
            connection.setRequestProperty("X-Android-Cert", "FE54F26E6B03D1309B71075D6670167BE5841DA6")
            connection.connect()

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val jsonObject = org.json.JSONObject(responseText)
            if (jsonObject.getString("status") == "OK") {
                val location = jsonObject.getJSONArray("results")
                    .getJSONObject(0).getJSONObject("geometry").getJSONObject("location")
                Pair(location.getDouble("lat"), location.getDouble("lng"))
            } else {
                val status = jsonObject.optString("status")
                val errorMessage =
                    jsonObject.optString("error_message", "なし")
                Log.e(TAG, "Geocoding API異常: status=$status, error=$errorMessage")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding API例外: ${e.message}", e)
            null
        }
    }

    // ★変更:試行回数を減らし(4→2)、待ち時間の上限も抑える
    private fun resolveRedirectUrl(shortUrl: String): String? {
        repeat(3) { attempt ->
            try {
                var currentUrl = shortUrl
                repeat(5) {
                    val url = URL(currentUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    activeConnection = connection // ★追加:見張り役に「今これを見てね」と伝える
                    // ★変更:5秒→3秒に短縮(遅い接続を早めに見切る)
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
                    )
                    connection.connect()
                    val redirectUrl = connection.getHeaderField("Location")
                    connection.disconnect()

                    if (redirectUrl != null) {
                        connection.disconnect()
                        currentUrl = redirectUrl
                    } else {
                        if (!currentUrl.contains("goo.gl")) {
                            connection.disconnect()
                            return currentUrl
                        } else {
                            // ★追加:リダイレクトではなかった場合、実際に何が返ってきたかを記録する
                            val responseCode = connection.responseCode
                            val bodySnippet = try {
                                connection.inputStream.bufferedReader().use { it.readText().take(200) }
                            } catch (e: Exception) {
                                "(本文取得失敗: ${e.message})"
                            }
                            Log.e(TAG, "リダイレクトなし。応答コード: $responseCode, 本文冒頭: $bodySnippet")
                            connection.disconnect()
                            throw IllegalStateException("短縮URL未解決")
                        }
                    }
                }
                return currentUrl
            } catch (e: Exception) {
                Log.e(TAG, "リダイレクト解決失敗(試行${attempt + 1}回目): ${e.message}")
                if (attempt < 2) Thread.sleep(500) // ★変更:attempt < 1 → attempt < 2
            }
        }
        return null
    }

    private fun copyToClipboard(text: String) {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("debug_url", text)
        clipboardManager.setPrimaryClip(clipData)
    }
}