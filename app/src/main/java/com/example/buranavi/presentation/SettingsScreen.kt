package com.example.buranavi.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // ToggleChip: Wear OS標準の「ラベル+スイッチ」がセットになったUI部品
    ToggleChip(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = { Text(text = label) },
        toggleControl = {
            Switch(checked = checked, onCheckedChange = null) // 実際の切り替えはToggleChip側で処理するのでnull
        },
        colors = ToggleChipDefaults.toggleChipColors()
    )
}

// ★追加:設定画面で選べる背景色の候補(黒に近い、目に優しい色を中心に用意)
private val backgroundColorOptions = listOf(
    0xFF000000L, // 黒
    0xFF1A1A2E, // 濃紺
    0xFF1B2A1B, // 濃い緑
    0xFF2A1B1B, // 濃い赤茶
    0xFF1A1A1A, // ダークグレー
    0xFFFFFFFF  // 白(将来的に明るい背景も使えるように)
)

// ★追加:矢印の色の候補(はっきり見えやすい色を中心に用意)
private val arrowColorOptions = listOf(
    0xFFFF0000L, // 赤
    0xFFFF9800, // オレンジ
    0xFFFFEB3B, // 黄色
    0xFF00E5FF, // シアン(明るい水色)
    0xFF00FF00, // 緑
    0xFFFFFFFF  // 白
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
                        tint = Color.White
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
                    }
                )
            }
            item {
                ToggleRow(
                    label = "バッテリー",
                    checked = currentSettings.showBattery,
                    onCheckedChange = { newValue ->
                        coroutineScope.launch { saveShowBattery(context, newValue) }
                    }
                )
            }
            item {
                ToggleRow(
                    label = "歩数",
                    checked = currentSettings.showSteps,
                    onCheckedChange = { newValue ->
                        coroutineScope.launch { saveShowSteps(context, newValue) }
                    }
                )
            }
            item {
                ToggleRow(
                    label = "心拍数",
                    checked = currentSettings.showHeartRate,
                    onCheckedChange = { newValue ->
                        coroutineScope.launch { saveShowHeartRate(context, newValue) }
                    }
                )
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
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for (colorValue in colorOptions) {
            val isSelected = colorValue == selectedColor
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape) // 丸い形に切り抜く
                    .background(Color(colorValue))
                    // ★選択中の色にだけ、白い枠線をつけて分かりやすくする
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = Color.White,
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