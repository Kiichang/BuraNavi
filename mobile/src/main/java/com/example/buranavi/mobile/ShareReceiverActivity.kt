package com.example.buranavi.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
// ★追加:タイムアウト機能を使うために必要
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
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import android.net.Uri
import android.content.pm.PackageManager
import java.security.MessageDigest
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

// 座標の取得手段を表す区分
enum class LocationSource {
    URL,            // 共有URL・展開後URLから直接取得
    WEB_VIEW,       // WebView経由
    GEOCODING_API   // API経由
}

class ShareReceiverActivity : ComponentActivity() {

    private fun showSuccessToastAndFinish() {
        val handler = Handler(Looper.getMainLooper())
        val finishTask = Runnable {
            if (!isFinishing && !isDestroyed) {
                finish()
            }
        }

        val toast = Toast.makeText(
            this,
            "ウォッチへ同期します",
            Toast.LENGTH_SHORT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            toast.addCallback(object : Toast.Callback() {

                override fun onToastHidden() {
                    handler.removeCallbacks(finishTask)
                    finishTask.run()
                }
            })

            // 表示が抑制された場合など、コールバックが来なくても終了する
            handler.postDelayed(finishTask, 5000L)
        } else {
            // Android 8～10では、短いToastの表示時間を見込んで終了する
            handler.postDelayed(finishTask, 2500L)
        }

        toast.show()
    }

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

    private val activeConnections =
        ConcurrentHashMap.newKeySet<HttpURLConnection>()

    private fun registerConnection(
        connection: HttpURLConnection
    ) {
        activeConnections.add(connection)
    }

    private fun unregisterConnection(
        connection: HttpURLConnection
    ) {
        activeConnections.remove(connection)
    }

    private fun disconnectAllConnections() {

        val connections =
            activeConnections.toList()

        activeConnections.clear()

        connections.forEach { connection ->

            try {
                connection.disconnect()
            } catch (e: Exception) {

            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val receivedIntent = intent

        if (receivedIntent?.action == Intent.ACTION_SEND && receivedIntent.type == "text/plain") {
            val sharedText = receivedIntent.getStringExtra(Intent.EXTRA_TEXT)

            if (sharedText != null) {

                showLoadingScreen()

                extractCoordinatesAndProceed(
                    sharedText
                )

            } else {

                finish()
            }
        } else {
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

    private fun showFailureAndFinish(
        message: String
    ) {

        vibrateOnFailure()

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()

        finish()
    }

    // ★追加:WebViewを使って、JavaScriptによるURL書き換え後の「座標入りURL」を取得する関数
    //    suspendCancellableCoroutine: コールバック形式のAPI(WebViewClient)を、
    //    コルーチンのsuspend関数として扱えるように橋渡しする仕組み
    // ★改善版: WebViewを使って座標を取得する関数
    private suspend fun resolveViaWebView(
        url: String
    ): Pair<Double, Double>? =
        suspendCancellableCoroutine { continuation ->

            val webView = WebView(this)

            val desktopUserAgent =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Safari/537.36"

            webView.settings.userAgentString =
                desktopUserAgent

            webView.settings.javaScriptEnabled =
                true

            webView.settings.domStorageEnabled =
                true

            var isResolved = false

            val handler =
                Handler(mainLooper)

            lateinit var checkRunnable: Runnable
            lateinit var timeoutRunnable: Runnable

            /*
             * WebView処理を正常終了またはタイムアウト終了するときの
             * 共通処理
             */
            fun finishWebViewResolve(
                result: Pair<Double, Double>?
            ) {
                if (isResolved) {
                    return
                }

                isResolved = true

                handler.removeCallbacks(
                    checkRunnable
                )

                handler.removeCallbacks(
                    timeoutRunnable
                )

                cleanupWebView(
                    webView
                )

                if (continuation.isActive) {
                    continuation.resume(
                        result
                    )
                }
            }

            /*
             * WebViewのURLを200ms間隔で確認する
             */
            checkRunnable =
                object : Runnable {

                    override fun run() {

                        if (isResolved) {
                            return
                        }

                        val currentUrl =
                            webView.url

                        if (currentUrl != null) {

                            val coordinates =
                                extractExplicitCoordinates(
                                    currentUrl
                                )

                            if (coordinates != null) {

                                val (lat, lng) =
                                    coordinates

                                finishWebViewResolve(
                                    Pair(lat, lng)
                                )

                                return
                            }
                        }

                        /*
                         * まだ座標が見つからなければ
                         * 200ms後に再確認
                         */
                        if (!isResolved) {

                            handler.postDelayed(
                                this,
                                200L
                            )
                        }
                    }
                }

            /*
             * WebViewのタイムアウト処理
             */
            timeoutRunnable =
                Runnable {

                    if (!isResolved) {

                        finishWebViewResolve(
                            null
                        )
                    }
                }

            webView.webViewClient =
                object : WebViewClient() {

                    override fun onPageFinished(
                        view: WebView?,
                        finishedUrl: String?
                    ) {
                        super.onPageFinished(
                            view,
                            finishedUrl
                        )

                        if (!isResolved) {
                            handler.post(
                                checkRunnable
                            )
                        }
                    }
                }

            /*
             * 10秒でタイムアウト
             */
            handler.postDelayed(
                timeoutRunnable,
                10000L
            )

            /*
             * API側が先に成功するなどして
             * このコルーチンがキャンセルされた場合
             */
            continuation.invokeOnCancellation {

                handler.post {

                    if (!isResolved) {

                        isResolved = true

                        handler.removeCallbacks(
                            checkRunnable
                        )

                        handler.removeCallbacks(
                            timeoutRunnable
                        )

                        cleanupWebView(
                            webView
                        )
                    }
                }
            }

            webView.loadUrl(
                url
            )

            handler.post(
                checkRunnable
            )
        }

    // クリーンアップ共通関数
    private fun cleanupWebView(
        webView: WebView
    ) {
        try {

            webView.stopLoading()

            webView.loadUrl(
                "about:blank"
            )

            webView.clearHistory()

            webView.removeAllViews()

            webView.destroy()

        } catch (e: Exception) {

        }
    }

    private fun extractCoordinatesAndProceed(
        sharedText: String
    ) {
        lifecycleScope.launch {

            val watchdog =
                launch(Dispatchers.IO) {

                    delay(16000L)

                    while (isActive) {

                        disconnectAllConnections()

                        delay(200L)
                    }
                }

            val result =
                try {

                    resolveCoordinatesFromText(
                        sharedText
                    )

                } catch (
                    e: Exception
                ) {

                    null

                } finally {

                    watchdog.cancel()
                }

            if (result != null) {

                val coordinates = result.first

                val (lat, lng) = coordinates

                sendDestinationToWatch(
                    lat,
                    lng
                )

            } else {

                showFailureAndFinish(
                    "目的地を取得できませんでした"
                )
            }
        }
    }

    private fun isGoogleShortUrl(
        url: String
    ): Boolean {

        val host =
            try {
                Uri.parse(url).host
            } catch (_: Exception) {
                null
            }

        return host.equals(
            "maps.app.goo.gl",
            ignoreCase = true
        ) ||
                host.equals(
                    "goo.gl",
                    ignoreCase = true
                )
    }

    // ★追加:今までextractCoordinatesAndProceedに書いていた抽出ロジックを、
    //    戻り値を返す独立した関数として切り出したもの
    // ★同時並行（レース）方式で最速取得する関数
    private suspend fun resolveCoordinatesFromText(
        sharedText: String
    ): Pair<Pair<Double, Double>, LocationSource>? {

        val urlRegex =
            Regex("https?://\\S+")

        val matchedUrl =
            urlRegex.find(sharedText)?.value
                ?: return null

        // 共有URLそのものに明示座標があれば、
        // WebView/APIを起動せず即座に返す
        extractExplicitCoordinates(
            matchedUrl
        )?.let { coordinates ->

            return Pair(
                coordinates,
                LocationSource.URL
            )
        }

        // ここから先は従来どおりWebView/APIを並列実行
        return coroutineScope {
            // ① Geocoding API 側の非同期タスクを開始
            val apiDeferred = async(Dispatchers.IO) {

                val resolvedUrl =
                    if (isGoogleShortUrl(matchedUrl)) {

                        resolveRedirectUrl(matchedUrl)
                            ?: matchedUrl

                    } else {

                        matchedUrl
                    }

                // 短縮URL展開後に明示座標が出た場合は
                // Geocoding APIを呼ばずそのまま返す
                val explicitCoordinates =
                    extractExplicitCoordinates(
                        resolvedUrl
                    )

                if (explicitCoordinates != null) {

                    Pair(
                        explicitCoordinates,
                        LocationSource.URL
                    )

                } else {

                    val placeName =
                        extractPlaceName(
                            resolvedUrl
                        )

                    val coordinates =
                        if (placeName != null) {
                            geocodeAddress(placeName)
                        } else {
                            null
                        }

                    if (coordinates != null) {
                        Pair(
                            coordinates,
                            LocationSource.GEOCODING_API
                        )
                    } else {
                        null
                    }
                }
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

                        webViewDeferred.cancel()

                        apiResult

                    } else {

                        null
                    }
                }

                // WebView 側が先に完了した場合
                webViewDeferred.onAwait { webViewResult ->

                    if (webViewResult != null) {

                        apiDeferred.cancel()

                        // API側で実行中の通信も即座に切断
                        disconnectAllConnections()

                        Pair(
                            webViewResult,
                            LocationSource.WEB_VIEW
                        )

                    } else {

                        null
                    }
                }
            }

            result ?: run {

                val apiRes =
                    apiDeferred.await()

                if (apiRes != null) {
                    return@coroutineScope apiRes
                }

                val webViewRes =
                    webViewDeferred.await()

                if (webViewRes != null) {
                    return@coroutineScope Pair(
                        webViewRes,
                        LocationSource.WEB_VIEW
                    )
                }

                null
            }
        }
    }

    private fun sendDestinationToWatch(latitude: Double, longitude: Double) {
        val nodeClient = Wearable.getNodeClient(this)
        val dataClient = Wearable.getDataClient(this)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->

            // ノードが見つからない場合
            if (nodes.isEmpty()) {
                showFailureAndFinish(
                    "ウォッチを確認できませんでした"
                )
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
                    showSuccessToastAndFinish()
                }
                // DataItem送信失敗
                .addOnFailureListener {
                    showFailureAndFinish(
                        "ウォッチへの送信に失敗しました"
                    )
                }
        }
        // ノード取得失敗
        .addOnFailureListener {
            showFailureAndFinish(
                "ウォッチとの接続確認に失敗しました"
            )
        }
    }

    private fun extractExplicitCoordinates(
        url: String
    ): Pair<Double, Double>? {

        // Google Mapsの目的地座標
        // 例: !3d35.12345!4d139.12345
        val placeCoordinatePattern =
            Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")

        // 明示的な緯度経度
        // 例:
        // search/35.12345,139.12345
        // ll=35.12345,139.12345
        val directCoordinatePattern =
            Regex(
                """(?:search/|ll=)(-?\d+(?:\.\d+)?)(?:%2C|%2c|,\+|\+|,)(-?\d+(?:\.\d+)?)"""
            )

        fun parse(
            match: MatchResult?
        ): Pair<Double, Double>? {

            if (match == null) return null

            val lat =
                match.groupValues[1].toDoubleOrNull()
                    ?: return null

            val lng =
                match.groupValues[2].toDoubleOrNull()
                    ?: return null

            if (
                !lat.isFinite() ||
                !lng.isFinite() ||
                lat !in -90.0..90.0 ||
                lng !in -180.0..180.0
            ) {
                return null
            }

            return Pair(lat, lng)
        }

        // 優先度1: !3d / !4d
        parse(
            placeCoordinatePattern.find(url)
        )?.let {
            return it
        }

        // 優先度2: search/lat,lng や ll=
        parse(
            directCoordinatePattern.find(url)
        )?.let {
            return it
        }

        return null
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

    private fun geocodeAddress(
        address: String
    ): Pair<Double, Double>? {

        val cleanedAddress =
            normalizeFullwidth(
                removePostalCode(address)
            ).trim()

        if (cleanedAddress.isBlank()) {
            return null
        }

        return queryGeocodingApi(
            cleanedAddress
        )
    }

    private val appSigningCertificateSha1: String? by lazy {
        loadAppSigningCertificateSha1()
    }

    private fun loadAppSigningCertificateSha1(): String? {

        return try {

            val signatureBytes =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                    val packageInfo =
                        packageManager.getPackageInfo(
                            packageName,
                            PackageManager.GET_SIGNING_CERTIFICATES
                        )

                    val signingInfo =
                        packageInfo.signingInfo
                            ?: return null

                    val signature =
                        signingInfo.apkContentsSigners
                            .firstOrNull()
                            ?: return null

                    signature.toByteArray()

                } else {

                    @Suppress("DEPRECATION")
                    val packageInfo =
                        packageManager.getPackageInfo(
                            packageName,
                            PackageManager.GET_SIGNATURES
                        )

                    @Suppress("DEPRECATION")
                    val signature =
                        packageInfo.signatures
                            ?.firstOrNull()
                            ?: return null

                    signature.toByteArray()
                }

            val digest =
                MessageDigest.getInstance("SHA-1")
                    .digest(signatureBytes)

            digest.joinToString("") { byte ->
                "%02X".format(byte)
            }

        } catch (e: Exception) {

            null
        }
    }

    private fun queryGeocodingApi(
        query: String
    ): Pair<Double, Double>? {

        var connection: HttpURLConnection? = null

        return try {

            val encodedQuery =
                URLEncoder.encode(
                    query,
                    "UTF-8"
                )

            val apiUrl =
                URL(
                    "https://maps.googleapis.com/maps/api/geocode/json" +
                            "?address=$encodedQuery" +
                            "&language=ja" +
                            "&region=jp" +
                            "&key=${BuildConfig.GEOCODING_API_KEY}"
                )

            connection =
                apiUrl.openConnection()
                        as HttpURLConnection

            registerConnection(
                connection
            )

            connection.connectTimeout =
                4000

            connection.readTimeout =
                4000

            connection.setRequestProperty(
                "X-Android-Package",
                packageName
            )

            appSigningCertificateSha1?.let { sha1 ->

                connection.setRequestProperty(
                    "X-Android-Cert",
                    sha1
                )
            }

            connection.connect()

            val responseText =
                connection.inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            val jsonObject =
                org.json.JSONObject(
                    responseText
                )

            if (
                jsonObject.getString(
                    "status"
                ) == "OK"
            ) {

                val location =
                    jsonObject
                        .getJSONArray(
                            "results"
                        )
                        .getJSONObject(0)
                        .getJSONObject(
                            "geometry"
                        )
                        .getJSONObject(
                            "location"
                        )

                Pair(
                    location.getDouble(
                        "lat"
                    ),
                    location.getDouble(
                        "lng"
                    )
                )

            } else {

                val status =
                    jsonObject.optString(
                        "status"
                    )

                val errorMessage =
                    jsonObject.optString(
                        "error_message",
                        "なし"
                    )

                null
            }

        } catch (e: Exception) {

            null

        } finally {

            connection?.let {

                unregisterConnection(
                    it
                )

                try {
                    it.disconnect()
                } catch (_: Exception) {
                }
            }
        }
    }

    // ★変更:試行回数を減らし(4→2)、待ち時間の上限も抑える
    private fun resolveRedirectUrl(shortUrl: String): String? {
        repeat(3) { attempt ->
            try {
                var currentUrl = shortUrl
                repeat(5) {

                    var connection: HttpURLConnection? =
                        null

                    try {

                        val url =
                            URL(currentUrl)

                        connection =
                            url.openConnection()
                                    as HttpURLConnection

                        registerConnection(
                            connection
                        )

                        connection.connectTimeout =
                            3000

                        connection.readTimeout =
                            3000

                        connection.instanceFollowRedirects =
                            false

                        connection.setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
                        )

                        connection.connect()

                        val redirectUrl =
                            connection.getHeaderField(
                                "Location"
                            )

                        if (redirectUrl != null) {

                            currentUrl =
                                URL(
                                    url,
                                    redirectUrl
                                ).toString()
                        } else {

                            if (
                                !isGoogleShortUrl(
                                    currentUrl
                                )
                            ) {

                                return currentUrl

                            } else {

                                throw IllegalStateException(
                                    "短縮URL未解決"
                                )
                            }
                        }

                    } finally {

                        connection?.let {

                            unregisterConnection(
                                it
                            )

                            try {
                                it.disconnect()
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
                return currentUrl
            } catch (e: Exception) {
                if (attempt < 2) Thread.sleep(500)
            }
        }
        return null
    }

    override fun onDestroy() {

        disconnectAllConnections()

        super.onDestroy()
    }

    private fun showLoadingScreen() {

        val density =
            resources.displayMetrics.density

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    (24 * density).toInt(),
                    (24 * density).toInt(),
                    (24 * density).toInt(),
                    (24 * density).toInt()
                )

                setBackgroundColor(
                    Color.WHITE
                )
            }

        val progressBar =
            ProgressBar(this).apply {

                isIndeterminate = true
            }

        val message =
            TextView(this).apply {

                text =
                    "目的地を取得しています…"

                textSize =
                    16f

                setTextColor(
                    Color.DKGRAY
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    (16 * density).toInt(),
                    0,
                    0
                )
            }

        container.addView(
            progressBar
        )

        container.addView(
            message
        )

        setContentView(
            container
        )
    }

}