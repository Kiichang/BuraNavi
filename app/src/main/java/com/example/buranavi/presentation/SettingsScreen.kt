package com.example.buranavi.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.launch
// ★追加:ON/OFFのトグルスイッチ付きの行を表示する部品
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.Switch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp

// ★追加:到着通知距離の選択肢
private val arrivalDistanceOptions = listOf(20, 50, 100, 200)

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    // ★追加:呼び出し側からmodifierを渡せるようにする
    //    デフォルト値としてModifier(何も指定しない場合の標準値)を設定しておくことで、
    //    modifierを指定しなかった場合も今まで通り動作する
    modifier: Modifier = Modifier
) {
    // ToggleChip: Wear OS標準の「ラベル+スイッチ」がセットになったUI部品
    ToggleChip(
        // ★追加:受け取ったmodifierをToggleChipに渡す
        modifier = modifier,
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = { Text(text = label) },
        toggleControl = {
            Switch(checked = checked, onCheckedChange = null) // 実際の切り替えはToggleChip側で処理するのでnull
        },
        colors = ToggleChipDefaults.toggleChipColors()
    )
}

// ★追加:設定画面で選べる背景色の候補
private val backgroundColorOptions = listOf(
    // === 1. モノトーン・OLED超省電力系 ===
    0xFF000000L, // ブラック
    0xFF1B1B1BL, // 【追加】グラファイトグレー（超省電力）
    0xFF2C3440L, // ダークチャコール

    // === 2. ネイビー・ブルー・くすみブルー系（暗 ➔ 明） ===
    0xFF0D1B2AL, // 【追加】ミッドナイトインディゴ（超省電力）
    0xFF021A27L, // ディープネイビー
    0xFF002244L, // 【追加】クラシックネイビー（高コントラスト）
    0xFF111344L, // ナイトブルー
    0xFF2E3B40L, // ダークブルーグレー
    0xFF3E4A59L, // ブルーグレー
    0xFF556773L, // ミディアムブルーグレー
    0xFF9295A6L, // ラベンダーグレー
    0xFFBAC2D9L, // ライトペールブルー
    0xFFD1E3E6L, // アイスブルー
    0xFFB4E1FFL, // スカイブルー

    // === 3. ティール・ターコイズ・ミント系（暗 ➔ 明） ===
    0xFF038C8CL, // ダークティール
    0xFF51A696L, // セージティール
    0xFF81B29AL, // 【追加】セージミント（カジュアル）
    0xFF95BFBBL, // ライトティール
    0xFFB4D9D5L, // ペールティール

    // === 4. グリーン・オリーブ系（暗 ➔ 明） ===
    0xFF003322L, // 【追加】ディープフォレストグリーン（高視認性）
    0xFF4C533EL, // ダークオリーブ
    0xFF5D735EL, // セージグリーン
    0xFF678C56L, // ミディアムグリーン
    0xFF868C56L, // オリーブイエロー
    0xFF97A69CL, // ニュートラルグリーン
    0xFFA9BF9FL, // ライトセージ
    0xFFCCD9CFL, // ペールグリーン

    // === 5. イエロー・クリーム・アイボリー系（暗 ➔ 明） ===
    0xFF9C8D71L, // カーキベージュ
    0xFFF2CA80L, // ウォームイエロー
    0xFFF2E7C4L, // ライトアイボリー

    // === 6. ブラウン・オレンジ・テラコッタ系（暗 ➔ 明） ===
    0xFF40302AL, // ダークブラウン
    0xFF6B6258L, // ウォームグレーブラウン
    0xFF63524DL, // ミディアムブラウン
    0xFF8C4F2BL, // テラコッタブラウン
    0xFFE07A5FL, // 【追加】ウォームテラコッタ（アクティブ）
    0xFF8C715AL, // キャメルブラウン
    0xFFBF8069L, // テラコッタピンク
    0xFFD9A689L, // サンドベージュ
    0xFFD9D1C7L, // ペールベージュ
    0xFFF2E4DCL, // アイボリーピンク

    // === 7. パープル・モーヴ・ピンク系（暗 ➔ 明） ===
    0xFF52154EL, // ディープパープル
    0xFF593F46L, // ダークローズ
    0xFF593F55L, // モーヴパープル
    0xFFA6808EL, // ダスティローズ
    0xFFD9BAD1L, // ライトモーヴ
    0xFFD9B4BBL, // ソフトローズ
    0xFFF2C9D4L  // ライトピンク
)

// ★追加:矢印の色の候補(はっきり見えやすい色を中心に用意)
private val arrowColorOptions = listOf(
// === 1. ホワイト・ライト系（万能） ===
    0xFFFFFFFFL, // White
    0xFFF2F2F2L, // Soft White

    // === 2. 高輝度・ネオン系（夜間・暗い背景で最高視認性） ===
    0xFFCCFF00L, // 【追加】Lime Neon（ナビの定番・高輝度）
    0xFFFFD700L, // 【追加】Electric Gold（高発色ゴールド）
    0xFFFFE082L, // Soft Yellow
    0xFFFFC857L, // Warm Amber

    // === 3. オレンジ・レッド・ピンク系 ===
    0xFFFF9100L, // 【追加】Signal Orange（屋外視認性強化）
    0xFFFF8A65L, // Soft Orange
    0xFFFF6B6BL, // Coral Red
    0xFFFF3366L, // 【追加】Vivid Pink（補色で目立つマゼンタ）
    0xFFFF8FB3L, // Soft Pink

    // === 4. パープル・ブルー・ティール系 ===
    0xFFD9A7FFL, // Lavender
    0xFF9FA8FFL, // Soft Indigo
    0xFF64B5F6L, // Sky Blue
    0xFF4DD0E1L, // Cyan
    0xFF63D7C6L, // Mint
    0xFF00E676L, // 【追加】Bright Emerald（鮮やかエメラルド）
    0xFF8BCB88L, // Soft Green
    0xFFC5D86DL, // Yellow Green

    // === 5. ダーク系（明るい背景・昼間用） ===
    0xFF4A5568L, // Slate
    0xFF432818L, // Brown
    0xFF003366L, // 【追加】Deep Navy（明るい背景向け）
    0xFF1A1A1AL  // Near Black
)

// ★追加:文字色の選択肢(白・黒の2択)
private val textColorOptions = listOf(
    0xFFFFFFFFL, // 白
    0xFF292929  // 黒
)

@Composable
fun SettingsScreen(currentSettings: AppSettings, onBack: () -> Unit) {
    // ★追加:設定変更(保存)は時間がかかる処理なので、コルーチンスコープを用意しておく
    //    rememberCoroutineScope():この画面が表示されている間、使い続けられるスコープを作る
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ★追加:ScalingLazyColumn:Wear OSに最適化された、スクロールできる縦並びリスト
        //    通常のLazyColumnと違い、中央付近の項目が少し大きく見えるような
        //    「丸い画面に合わせた」表示効果が自動でつく
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ★追加:リストの一番上に「戻るボタン」を配置
            item {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "戻る",
                        tint = Color.White,
                    )
                }
            }

            // ★追加:「背景色」セクションの見出し
            item {
                ListHeader {
                    Text(text = "背景色")
                }
            }
            // ★追加:背景色の選択肢を、丸いスウォッチ(色見本)として横並びで表示
            item {
                ColorSwatchRow(
                    colorOptions = backgroundColorOptions,
                    selectedColor = currentSettings.backgroundColor,
                    onColorSelected = { selectedColor ->
                        // タップされたら、コルーチンの中でDataStoreに保存する
                        coroutineScope.launch {
                            saveBackgroundColor(context, selectedColor)
                        }
                    }
                )
            }

            // ★追加:「矢印の色」セクション
            item {
                ListHeader {
                    Text(text = "矢印の色")
                }
            }
            item {
                ColorSwatchRow(
                    colorOptions = arrowColorOptions,
                    selectedColor = currentSettings.arrowColor,
                    onColorSelected = { selectedColor ->
                        coroutineScope.launch {
                            saveArrowColor(context, selectedColor)
                        }
                    }
                )
            }

            // ★追加:「矢印のデザイン」セクション
            item {
                ListHeader {
                    Text(text = "矢印のデザイン")
                }
            }
            item {
                ArrowStyleRow(
                    currentStyleOrdinal = currentSettings.arrowStyle,
                    arrowColor = Color(currentSettings.arrowColor),
                    onStyleSelected = { styleOrdinal ->
                        coroutineScope.launch {
                            saveArrowStyle(context, styleOrdinal)
                        }
                    }
                )
            }

            // ★追加:「文字色」セクション
            item {
                ListHeader {
                    Text(text = "文字色")
                }
            }
            // ★追加:「矢印色に合わせる」トグル
            item {
                ToggleRow(
                    label = "矢印色に合わせる",
                    checked = currentSettings.syncTextColorWithArrow,
                    onCheckedChange = { newValue ->
                        coroutineScope.launch {
                            saveSyncTextColorWithArrow(context, newValue)
                        }
                    }
                )
                Spacer(modifier = Modifier.padding(30.dp))
            }
            item {
                ColorSwatchRow(
                    colorOptions = textColorOptions,
                    selectedColor = currentSettings.textColor,
                    onColorSelected = { selectedColor ->
                        coroutineScope.launch {
                            saveTextColor(context, selectedColor)
                        }
                    }
                )
            }

            // ★追加:「表示項目」セクション
            item {
                ListHeader {
                    Text(text = "表示項目")
                }
            }
            item {
                ToggleRow(
                    label = "時刻",
                    checked = currentSettings.showTime,
                    onCheckedChange = { newValue ->
                        coroutineScope.launch { saveShowTime(context, newValue) }
                    },
                    modifier = Modifier.width(140.dp)
                )
            }
            item {
                ToggleRow(
                    label = "バッテリー",
                    checked = currentSettings.showBattery,
                    onCheckedChange = { newValue ->
                        coroutineScope.launch { saveShowBattery(context, newValue) }
                    },
                    modifier = Modifier.width(140.dp)
                )
            }
            item {
                ToggleRow(
                    label = "歩数",
                    checked = currentSettings.showSteps,
                    onCheckedChange = { newValue ->
                        coroutineScope.launch { saveShowSteps(context, newValue) }
                    },
                    modifier = Modifier.width(140.dp)
                )
            }
            item {
                ToggleRow(
                    label = "心拍数",
                    checked = currentSettings.showHeartRate,
                    onCheckedChange = { newValue ->
                        coroutineScope.launch { saveShowHeartRate(context, newValue) }
                    },
                    modifier = Modifier.width(140.dp)
                )
                Spacer(modifier = Modifier.padding(30.dp))
            }

            item {
                ListHeader {
                    Text(text = "到着通知の距離")
                }
            }
            item {
                ArrivalDistanceRow(
                    currentDistance = currentSettings.arrivalNotificationDistance,
                    onDistanceSelected = { newValue ->
                        coroutineScope.launch {
                            saveArrivalNotificationDistance(context, newValue)
                        }
                    }
                )
            }

            // ★追加:「目的地リセット」セクション
            item {
                ListHeader {
                    Text(text = "目的地")
                }
            }
            item {
                // ★追加:誤タップ防止のため、1回目のタップで確認状態にし、
                //    もう一度タップされたら実際にリセットする、という2段階の仕組みにする
                var isConfirming by remember { mutableStateOf(false) }

                Chip(
                    onClick = {
                        if (isConfirming) {
                            // 2回目のタップ:実際にリセットを実行
                            coroutineScope.launch {
                                clearDestination(context)
                            }
                            isConfirming = false
                        } else {
                            // 1回目のタップ:確認状態にする
                            isConfirming = true
                        }
                    },
                    label = {
                        Text(
                            text = if (isConfirming) "本当にリセットしますか？" else "目的地をリセット"
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        // ★確認状態のときだけ、赤系の色にして注意を促す
                        backgroundColor = if (isConfirming) Color(0xFFBF3D3D) else Color(0xFF333333)
                    )
                )

                // ★追加:確認状態のまま5秒操作がなければ、自動的に元の表示に戻す
                //    (誤って確認状態のまま放置され、後で誤タップしてしまうのを防ぐため)
                LaunchedEffect(isConfirming) {
                    if (isConfirming) {
                        delay(5000)
                        isConfirming = false
                    }
                }
            }

            // ★追加:リストの一番下にも「戻るボタン」を配置
            item {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "戻る",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ArrivalDistanceRow(
    currentDistance: Int,
    onDistanceSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for (distance in arrivalDistanceOptions) {
            val isSelected = distance == currentDistance
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Color.DarkGray else Color.Transparent)
                    .clickable { onDistanceSelected(distance) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = "${distance}m", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

// ★追加:色見本(スウォッチ)を横並びで表示し、タップで選択できるようにする部品
@Composable
private fun ColorSwatchRow(
    colorOptions: List<Long>,
    selectedColor: Long,
    onColorSelected: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // ★追加:横方向にスクロールできるようにする
            //    rememberScrollState():どこまでスクロールしたかを記憶しておく仕組み
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        // ★変更:SpaceEvenly(画面幅に均等配置)だと、スクロール時にうまく機能しないため、
        //    spacedBy(一定間隔で並べる)に変更する
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (colorValue in colorOptions) {
            val isSelected = colorValue == selectedColor
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape) // 丸い形に切り抜く
                    .background(Color(colorValue))
                    // ★選択中の色にだけ、白い枠線をつけて分かりやすくする
                    // ★変更:黒に近い色が背景に埋もれて見えなくなるのを防ぐため、
                    //    未選択時も薄いグレーの枠を必ずつけるようにする
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color.White else Color.Gray,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(colorValue) }
            )
        }
    }
}

// ★追加:矢印のデザインをプレビュー付きで横並びに表示し、タップで選択できるようにする部品
@Composable
private fun ArrowStyleRow(
    currentStyleOrdinal: Int,
    arrowColor: Color,
    onStyleSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // ArrowStyle.values(): enumの全パターンを配列として取得できる
        ArrowStyle.values().forEachIndexed { index, style ->
            val isSelected = index == currentStyleOrdinal
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    // 選択中のデザインだけ、背景を少し明るくして枠っぽく見せる
                    .background(if (isSelected) Color.DarkGray else Color.Transparent)
                    .clickable { onStyleSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                // ★実際の矢印デザインを小さいCanvasでプレビュー表示する
                Canvas(modifier = Modifier.size(24.dp)) {
                    drawArrowShape(style = style, color = arrowColor)
                }
            }
        }
    }
}