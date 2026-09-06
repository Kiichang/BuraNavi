package com.example.buranavi.mobile
// ↑ 自分のパッケージ名に合わせてください

// ★追加:クリップボード操作に必要なimport
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray // ★追加:Nominatimからの返答(JSON形式)を読み取るために必要
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder // ★追加:URLエンコードされた日本語を元の文字列に戻すために必要
import java.net.URLEncoder // ★追加:検索キーワードをURLに埋め込める形式に変換するために必要
// ★追加:Wearable Data Layer APIを使うために必要なimport
import com.google.android.gms.wearable.Wearable
// import com.google.android.gms.wearable.MessageClient
// ★変更:MessageClientの代わりにDataClientを使う
import com.google.android.gms.wearable.PutDataMapRequest

// ComponentActivity: 画面表示をほぼ行わない「裏方専用」のActivityとして使う
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ① このActivityを起動した「Intent(意図)」を取得する
        //    Googleマップから共有された場合、この中に共有された文字列が入っている
        val receivedIntent = intent

        // ② Intentのactionが本当に「共有(SEND)」かどうかを確認
        if (receivedIntent?.action == Intent.ACTION_SEND && receivedIntent.type == "text/plain") {
            // ③ 共有されたテキスト本体を取り出す
            //    EXTRA_TEXTというキーに、共有元アプリが入れた文字列が格納されている
            val sharedText = receivedIntent.getStringExtra(Intent.EXTRA_TEXT)

            if (sharedText != null) {
                // ④ 座標を抜き出す処理を開始(ネット通信が絡むので後述の理由でコルーチンを使う)
                extractCoordinatesAndProceed(sharedText)
            } else {
                Toast.makeText(this, "共有データが空でした", Toast.LENGTH_SHORT).show()
                finish() // このActivityを終了(画面を閉じる)
            }
        } else {
            // 共有以外の方法で起動された場合(通常は起こらないが念のため)
            Toast.makeText(this, "対応していない起動方法です", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ⑤ 共有されたテキストから緯度経度を抜き出すメイン処理
    private fun extractCoordinatesAndProceed(sharedText: String) {
        // CoroutineScope(Dispatchers.IO):
        //   ネットワーク通信は「時間がかかる処理」なので、
        //   画面を止めないよう別スレッド(IO用スレッド)で実行する仕組み
        CoroutineScope(Dispatchers.IO).launch {
            // まず、共有テキストの中から「URLらしき部分」を正規表現で抜き出す
            // (Googleマップの共有テキストは「場所の名前 + URL」のような形式で来ることが多いため)
            val urlRegex = Regex("https?://\\S+")
            val matchedUrl = urlRegex.find(sharedText)?.value

            if (matchedUrl == null) {
                // URLが含まれていなかった場合
                withContext(Dispatchers.Main) {
                    // ★デバッグ用:共有された生のテキストをそのまま表示してみる
                    Toast.makeText(this@ShareReceiverActivity, "URL未検出。元テキスト: $sharedText", Toast.LENGTH_LONG).show()
                    // Toast.makeText(this@ShareReceiverActivity, "URLが見つかりませんでした", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@launch
            }

            // ★デバッグ用:抜き出せたURLをまず表示(ここで一旦止めて確認したい場合はコメントアウトを外す)
            // withContext(Dispatchers.Main) {
            //     Toast.makeText(this@ShareReceiverActivity, "検出したURL: $matchedUrl", Toast.LENGTH_LONG).show()
            // }

            // ⑥ URLが短縮URL(maps.app.goo.gl)かどうかで処理を分ける
            // val finalUrl = if (matchedUrl.contains("maps.app.goo.gl") || matchedUrl.contains("goo.gl")) {
            val finalUrl = if (matchedUrl.contains("goo.gl")) {
                // 短縮URLの場合、実際にアクセスして「リダイレクト先」の本当のURLを取得する
                resolveRedirectUrl(matchedUrl)
            } else {
                // 既に通常URLの場合はそのまま使う
                matchedUrl
            }

            // 座標のパターンを2種類チェックするように変更
            // パターンA: @35.6586,139.7454,17z のような形式
            val patternA = Regex("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
            // パターンB: !3d35.658581!4d139.745438 のような形式(Googleマップの別の内部表現)
            val patternB = Regex("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)")

            var latitude: Double? = null
            var longitude: Double? = null

            // ★デバッグ用:各段階の状況を溜めておく文字列
            val debugLog = StringBuilder()
            debugLog.appendLine("finalUrl: $finalUrl")

            val matchResult = finalUrl?.let { patternA.find(it) ?: patternB.find(it) }

            if (matchResult != null) {
                // 従来通り、URLに直接座標が入っていたケース
                latitude = matchResult.groupValues[1].toDouble()
                longitude = matchResult.groupValues[2].toDouble()
                debugLog.appendLine("パターンA/Bで座標を直接検出")
            } else {
                // ★追加:座標が直接見つからなかった場合、住所・施設名を抜き出してジオコーディングを試みる
                val placeName = extractPlaceName(finalUrl ?: "")
                // ★デバッグ用:抜き出した住所名をログに残す
                debugLog.appendLine("抜き出した住所名: $placeName")

                if (placeName != null) {
                    val geocodedResult = geocodeAddress(placeName, debugLog) // ★debugLogを渡すように変更
                    if (geocodedResult != null) {
                        latitude = geocodedResult.first
                        longitude = geocodedResult.second
                    }
                }
            }

            // ⑧ 結果をメインスレッドに戻して画面表示(Toastなど)を行う
            withContext(Dispatchers.Main) {
                if (latitude != null && longitude != null) {
                    // Toast.makeText(
                    //     this@ShareReceiverActivity,
                    //     "座標を取得: $latitude, $longitude",
                    //     Toast.LENGTH_LONG
                    // ).show()

                    // ★追加:座標が取得できたら、ウォッチへ送信する
                    sendDestinationToWatch(latitude, longitude)
                } else {
                    // ★変更:finalUrlだけでなく、debugLog全体をクリップボードにコピーする
                    copyToClipboard(debugLog.toString())
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        "抜き出し失敗(詳細をクリップボードにコピー済み)",
                        Toast.LENGTH_LONG
                    ).show()
                }
                finish()
            }
        }
    }

    // ★追加:URLの中の「/place/」と「/data=」(または「/@」)の間から、施設名・住所を取り出す関数
    private fun extractPlaceName(url: String): String? {
        // 正規表現の意味:
        //   "/place/" の直後から、次に "/data=" か "/@" が出てくる直前までの文字列を取得
        val placeNameRegex = Regex("/place/([^/]+)")
        val rawName = placeNameRegex.find(url)?.groupValues?.get(1) ?: return null

        return try {
            // URLエンコードされている文字(%E3%80%92など)を、元の日本語に戻す
            URLDecoder.decode(rawName, "UTF-8")
        } catch (e: Exception) {
            null
        }
    }

    // ★追加:全角文字を半角に変換する関数
    private fun normalizeFullwidth(input: String): String {
        val sb = StringBuilder()
        for (char in input) {
            val code = char.code
            when {
                code in 0xFF10..0xFF19 -> sb.append((code - 0xFF10 + '0'.code).toChar())
                // ★修正:「ー」(カタカナ長音記号 U+30FC)を対象から外した
                //    全角ハイフン(－ U+FF0D、− U+2212)だけを半角に変換する
                char == '－' || char == '−' -> sb.append('-')
                else -> sb.append(char)
            }
        }
        return sb.toString()
    }

    // ★追加:住所文字列から郵便番号部分(〒から始まる部分)を取り除く関数
    private fun removePostalCode(address: String): String {
        // 正規表現の意味:「〒」+ 数字3桁 + 「-」+ 数字4桁 + 空白、というパターンを削除
        return address.replace(Regex("〒\\d{3}-\\d{4}\\s*"), "")
    }

    // ★変更:geocodeAddress関数の中身を、Nominatim呼び出しからGoogle Geocoding API呼び出しに置き換え
    private fun geocodeAddress(address: String, debugLog: StringBuilder): Pair<Double, Double>? {
        val cleanedAddress = normalizeFullwidth(removePostalCode(address)).trim()
        debugLog.appendLine("正規化後の住所: $cleanedAddress")

        // ★変更:queryNominatim → queryGeocodingApi に置き換え
        var result = queryGeocodingApi(cleanedAddress, debugLog)

        if (result == null) {
            val lastWord = cleanedAddress.split(" ", "　").lastOrNull()
            if (lastWord != null && lastWord != cleanedAddress) {
                debugLog.appendLine("フォールバック検索キーワード: $lastWord")
                result = queryGeocodingApi("$lastWord 東京", debugLog) // ★「日本」より「東京」の方が精度が上がりやすいため変更
            }
        }

        return result
    }

    // ★変更:Nominatim用の関数を、Google Geocoding API用に置き換え
    private fun queryGeocodingApi(query: String, debugLog: StringBuilder): Pair<Double, Double>? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            // language=ja: 結果を日本語で受け取る設定(必須ではないが、精度向上に役立つことがある)
            // region=jp: 日本国内を優先して検索する設定
            val apiUrl = URL(
                "https://maps.googleapis.com/maps/api/geocode/json" +
                        "?address=$encodedQuery" +
                        "&language=ja" +
                        "&region=jp" +
                        "&key=${BuildConfig.GEOCODING_API_KEY}" // ★local.propertiesから読み込んだキーを使用
            )

            val connection = apiUrl.openConnection() as HttpURLConnection

            // ★重要:APIキーに「Androidアプリ制限」をかけている場合、この2つのヘッダーが必須
            connection.setRequestProperty("X-Android-Package", "com.example.buranavi.mobile")
            // ↓先ほど確認したSHA-1を、コロンを除去してすべて大文字にした文字列に置き換えてください
            connection.setRequestProperty("X-Android-Cert", "FE54F26E6B03D1309B71075D6670167BE5841DA6")

            connection.connect()

            val responseCode = connection.responseCode
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            debugLog.appendLine("Geocoding APIクエリ「$query」→ 応答コード: $responseCode / 内容: $responseText")

            // Google Geocoding APIのレスポンス構造から、緯度経度を取り出す
            // 構造: { "results": [ { "geometry": { "location": { "lat": ..., "lng": ... } } } ], "status": "OK" }
            val jsonObject = org.json.JSONObject(responseText)
            val status = jsonObject.getString("status")

            if (status == "OK") {
                val results = jsonObject.getJSONArray("results")
                val firstResult = results.getJSONObject(0)
                val location = firstResult.getJSONObject("geometry").getJSONObject("location")
                val lat = location.getDouble("lat")
                val lng = location.getDouble("lng")
                Pair(lat, lng)
            } else {
                // OK以外(ZERO_RESULTS, REQUEST_DENIEDなど)の場合は見つからなかった扱いにする
                debugLog.appendLine("ステータスがOKではありません: $status")
                null
            }
        } catch (e: Exception) {
            debugLog.appendLine("例外発生: ${e.javaClass.simpleName} - ${e.message}")
            null
        }
    }

    // ★変更:リトライ機能と、複数回のリダイレクトに対応したバージョン
    private fun resolveRedirectUrl(shortUrl: String): String? {
        // ★変更:試行回数を4回に増やし、失敗のたびに待ち時間を伸ばす(バックオフ)ようにした
        repeat(4) { attempt ->
            try {
                var currentUrl = shortUrl
                repeat(5) {
                    val url = URL(currentUrl)
                    val connection = url.openConnection() as HttpURLConnection

                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.instanceFollowRedirects = false
                    // ★追加:デフォルトのUser-Agent(Java/…)だと、Google側が
                    //    ブラウザではないアクセスとして通常と違う応答を返す可能性があるため、
                    //    一般的なブラウザを装うUser-Agentを設定しておく
                    connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
                    )
                    connection.connect()

                    val responseCode = connection.responseCode
                    val redirectUrl = connection.getHeaderField("Location")
                    connection.disconnect()

                    if (redirectUrl != null) {
                        // リダイレクト先が見つかったので、そのURLで続行
                        currentUrl = redirectUrl
                    } else {
                        // ★変更:Locationが無い場合、「本当に解決済みか」をチェックする
                        if (!currentUrl.contains("goo.gl")) {
                            // goo.glドメインを抜け出せていれば、正常に最終URLへ到達したとみなす
                            return currentUrl
                        } else {
                            // まだgoo.glドメインのまま = 反映待ちの可能性が高いので、失敗として例外を投げる
                            throw IllegalStateException(
                                "短縮URLがまだ解決できません(応答コード: $responseCode)"
                            )
                        }
                    }
                }
                return currentUrl
            } catch (e: Exception) {
                // ★変更:失敗のたびに待ち時間を伸ばす(1回目失敗→0.8秒待機、2回目失敗→1.6秒待機、3回目失敗→2.4秒待機)
                if (attempt < 3) {
                    Thread.sleep(800L * (attempt + 1))
                }
            }
        }
        return null
    }

    // ★追加:文字列をクリップボードにコピーするための関数
    private fun copyToClipboard(text: String) {
        // システムのクリップボード機能を管理するサービスを取得
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        // ClipData: クリップボードにコピーするデータそのもの
        //   第1引数の"debug_url"はラベル(クリップボードの履歴などで表示される名前、任意の文字列でOK)
        //   第2引数がコピーしたい実際の中身
        val clipData = ClipData.newPlainText("debug_url", text)

        // 作成したデータをクリップボードにセット(コピー)する
        clipboardManager.setPrimaryClip(clipData)
    }

    // ★追加:緯度・経度をウォッチへ送信する関数
    private fun sendDestinationToWatch(latitude: Double, longitude: Double) {
        // NodeClient:ペアリングされているウォッチの情報を取得するためのクライアント
        val nodeClient = Wearable.getNodeClient(this)
        // MessageClient:実際にメッセージを送信するためのクライアント
        // val messageClient = Wearable.getMessageClient(this)

        val dataClient = Wearable.getDataClient(this)

        // PutDataMapRequest: 「/destination」というパスに紐づくデータの入れ物を作る
        val putDataMapRequest = PutDataMapRequest.create("/destination").apply {
            dataMap.putDouble("latitude", latitude)
            dataMap.putDouble("longitude", longitude)
            // ★重要:タイムスタンプを含めておく
            //    DataItemは「前回と全く同じ内容」だと変更なしとみなされ、
            //    onDataChangedが呼ばれないことがあるため、毎回必ず値が変わるようにする工夫
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }

        // ① ペアリングされている端末(ウォッチ)の一覧を取得する
        //    (Bluetoothで直接ペアリングしたスマートウォッチが対象。複数台持っている場合は全端末に送る)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                // ウォッチが接続されていない場合
                Toast.makeText(this, "接続中のウォッチが見つかりません", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            // ★追加:ノードの「名前」だけでなく「ID」も表示するようにする
            // val nodeInfo = nodes.joinToString(", ") { "${it.displayName}(ID: ${it.id})" }
            // Toast.makeText(this, "送信先ノード: $nodeInfo", Toast.LENGTH_LONG).show()

            // ★追加:見つかったノードの「名前」を一覧表示して、本当にTicWatchが対象になっているか確認する
            // val nodeNames = nodes.joinToString(", ") { it.displayName }
            // Toast.makeText(this, "送信先ノード: $nodeNames", Toast.LENGTH_LONG).show()

            // ② 緯度・経度を、送信できる形式(バイト配列)に変換する
            //    今回は単純に "緯度,経度" という文字列にしてから、バイト配列化する
            val payload = "$latitude,$longitude".toByteArray()

            val putDataRequest = putDataMapRequest.asPutDataRequest()
            // setUrgent(): 通常は多少まとめて同期されることがあるが、これを指定すると優先的に即座の同期を試みる
            putDataRequest.setUrgent()

            dataClient.putDataItem(putDataRequest)
                .addOnSuccessListener {
                    Toast.makeText(this, "目的地をウォッチへ送信しました", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "DataItem送信失敗: ${exception.message}", Toast.LENGTH_SHORT).show()
                }

            // ③ 見つかった端末それぞれに対してメッセージを送信する
//            for (node in nodes) {
//                messageClient.sendMessage(
//                    node.id,              // 送信先の端末ID
//                    "/destination",       // ★パス:「これは目的地データですよ」という目印(ウォッチ側でこの文字列を見て判別する)
//                    payload                // 送信する中身(バイト配列)
//                ).addOnSuccessListener {
//                    // ★変更:成功した場合も、どのノードに送れたかを表示する
//                    Toast.makeText(this, "送信成功 → ${node.displayName}", Toast.LENGTH_SHORT).show()
//                }.addOnFailureListener { exception ->
//                    Toast.makeText(this, "送信失敗(${node.displayName}): ${exception.message}", Toast.LENGTH_SHORT).show()
//                }
//            }
        }
    }
}