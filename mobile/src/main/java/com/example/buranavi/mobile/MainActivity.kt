package com.example.buranavi.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.buranavi.mobile.ui.theme.BuraNaviTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            BuraNaviTheme {

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color =
                        MaterialTheme.colorScheme.background
                ) {

                    BuraNaviGuideScreen()
                }
            }
        }
    }
}

@Composable
fun BuraNaviGuideScreen() {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 32.dp,
                    vertical = 48.dp
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "BuraNavi",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color =
                MaterialTheme.colorScheme.primary
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text =
                "Google Mapsで目的地を開き、\n" +
                        "「共有」からBuraNaviを選択してください。",
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            lineHeight = 25.sp
        )

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        Text(
            text = "使い方",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text =
                "1. Google Mapsで目的地を開く\n" +
                        "2. 「共有」をタップ\n" +
                        "3. BuraNaviを選択\n" +
                        "4. ウォッチで方向と距離を確認",
            fontSize = 16.sp,
            lineHeight = 26.sp
        )

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        Text(
            text =
                "BuraNaviを使用するには、対応するWear OSウォッチに" +
                        "BuraNaviのウォッチアプリがインストールされている必要があります。",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text =
                "このスマホアプリから目的地を直接入力する必要はありません。",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(
    showBackground = true
)
@Composable
fun BuraNaviGuidePreview() {

    BuraNaviTheme {
        BuraNaviGuideScreen()
    }
}