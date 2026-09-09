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
    0xFF000000L,
    0xFF021A27L,
    0xFF111344L,
    0xFF1F2A33L,
    0xFF262626L,
    0xFF2C3440L,
    0xFF2E3B40L,
    0xFF3E4A59L,
    0xFF4A5954L,
    0xFF556773L,
    0xFF6B6258L,
    0xFF8C7B77L,
    0xFF9295A6L,
    0xFFBAC2D9L,
    0xFFD1E3E6L,
    0xFFB4E1FFL,

    0xFF40302AL,
    0xFF593E3CL,
    0xFF63524DL,
    0xFF8C4F2BL,
    0xFF8C715AL,
    0xFF9C8D71L,
    0xFFBF8069L,
    0xFFD9A689L,
    0xFFCCC0AAL,
    0xFFD9D1C7L,
    0xFFF2E4DCL,
    0xFFF2CA80L,
    0xFFF2E7C4L,

    0xFF4C533EL,
    0xFF868C56L,
    0xFF678C56L,
    0xFF5D735EL,
    0xFF97A69CL,
    0xFFA9BF9FL,
    0xFFCCD9CFL,

    0xFF5A3F46L,
    0xFF52154EL,
    0xFF593F55L,
    0xFFA6808EL,
    0xFFD9B4BBL,
    0xFFF2C9D4L,
    0xFFD9BAD1L,

    0xFF51A696L,
    0xFF038C8CL,
    0xFF95BFBBL,
    0xFFB4D9D5L,
    0xFFF2DFEBL
)

// ★追加:矢印の色の候補(はっきり見えやすい色を中心に用意)
private val arrowColorOptions = listOf(
    0xFFFFFFFFL, // White
    0xFFF2F2F2L, // Soft White
    0xFFFFE082L, // Soft Yellow
    0xFFFFC857L, // Warm Amber
    0xFFFF8A65L, // Soft Orange
    0xFFFF6B6BL, // Coral Red
    0xFFFF8FB3L, // Soft Pink
    0xFFD9A7FFL, // Lavender
    0xFF9FA8FFL, // Soft Indigo
    0xFF64B5F6L, // Sky Blue
    0xFF4DD0E1L, // Cyan
    0xFF63D7C6L, // Mint
    0xFF8BCB88L, // Soft Green
    0xFFC5D86DL, // Yellow Green
    0xFF4A5568L, // Slate
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