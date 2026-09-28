package com.example.buranavi.presentation

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
// ★追加:角を丸めるための、Android標準の描画処理を使うためのimport
import android.graphics.CornerPathEffect
import android.graphics.Paint as AndroidPaint
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas // ★追加
import androidx.compose.ui.graphics.toArgb        // ★追加

// ★追加:矢印のデザインパターンを列挙型(enum)で管理する
//    enumは「決まった選択肢の中から1つを表す型」で、今回は4種類のデザインを表現する
enum class ArrowStyle {
    TRIANGLE, // 三角形(今まで使っていたデザイン)
    SHADOW,   // 三角立体感
    NEEDLE,   // コンパスの針のように、上下で色の濃さが変わるデザイン
    LARGE,    // 大きめ三角
    CHEVRON,  // 山形(V字)を重ねたようなデザイン
    THIN      // 細い線だけで表現するシンプルなデザイン
}

// ★追加:DrawScope(Canvas内での描画命令が使える領域)の拡張関数として、
//    「指定したスタイル・色で矢印を描く」処理をまとめる
//    こうしておくことで、MainActivity(実際の表示)とSettingsScreen(プレビュー表示)の
//    両方から同じ描画ロジックを使い回せる
fun DrawScope.drawArrowShape(style: ArrowStyle, color: Color) {
    when (style) {
        ArrowStyle.TRIANGLE -> {
            val path = Path().apply {
                moveTo(size.width / 2, 0f)
                lineTo(size.width * 0.75f, size.height)
                lineTo(size.width / 2, size.height * 0.75f)
                lineTo(size.width * 0.25f, size.height)
                close()
            }
            drawPath(path = path, color = color)
        }

        ArrowStyle.LARGE -> {
            val largeTop = Path().apply {
                moveTo(size.width / 2, size.height * -0.2f)
                lineTo(size.width * 1.1f, size.height * 1.0f)
                lineTo(size.width / 2, size.height * 0.85f)
                lineTo(size.width * -0.1f, size.height * 1.0f)
                close()
            }
            val largeBottom = Path().apply {
                moveTo(size.width * 0.2f, size.height / 2)
                lineTo(size.width * 0.8f, size.height / 2)
                lineTo(size.width / 2, size.height * 1.3f)
                close()
            }
            // ★変更:通常のdrawPathではなく、Android標準のCanvas描画に切り替えて角を丸める
            //    drawContext.canvas.nativeCanvas:Composeの裏側で使われている、
            //    本来のAndroid Canvas(より細かい制御ができる低レベルな描画領域)を取り出す
            // ★変更:applyを使わず、androidPaint.〜と明示的に指定することで
            //    引数の"style"との名前衝突を避けている
            val androidPaint = AndroidPaint()
            // アンチエイリアス:図形のフチがギザギザに見えないよう滑らかにする設定
            androidPaint.isAntiAlias = true
            androidPaint.color = color.toArgb() // Composeの色をAndroid標準の色形式に変換
            androidPaint.style = AndroidPaint.Style.FILL // 塗りつぶし指定

            // ★重要:CornerPathEffect(半径):
            //    パス(図形の輪郭線)の「尖った角」を、指定した半径の丸みに
            //    自動的に置き換えてくれる効果。数値が大きいほど丸みが強くなる
            androidPaint.pathEffect = CornerPathEffect(18f)

            // ComposeのPathを、Android標準のPath形式に変換してから描画する
            drawContext.canvas.nativeCanvas.drawPath(largeTop.asAndroidPath(), androidPaint)
            drawContext.canvas.nativeCanvas.drawPath(largeBottom.asAndroidPath(), androidPaint)
            // drawPath(path = bottomHalf, color = color)
        }

        ArrowStyle.SHADOW -> {
            val rightHalf = Path().apply {
                moveTo(size.width / 2, 0f)
                lineTo(size.width * 1, size.height)
                lineTo(size.width / 2, size.height * 0.75f)
                close()
            }
            val leftHalf = Path().apply {
                moveTo(size.width / 2, 0f)
                lineTo(size.width * 0, size.height)
                lineTo(size.width / 2, size.height * 0.75f)
                close()
            }
            drawPath(path = rightHalf, color = color)
            drawPath(path = leftHalf, color = color.copy(alpha = 0.35f))
        }

        ArrowStyle.CHEVRON -> {
            // 上向きの「山形」を描く(矢印の先端部分だけのシンプルな形)
            val path = Path().apply {
                moveTo(size.width / 2, 0f)
                lineTo(size.width, size.height * 0.55f)
                lineTo(size.width * 0.68f, size.height * 0.55f)
                lineTo(size.width * 0.68f, size.height)
                lineTo(size.width * 0.32f, size.height)
                lineTo(size.width * 0.32f, size.height * 0.55f)
                lineTo(0f, size.height * 0.55f)
                close()
            }
            drawPath(path = path, color = color)
        }

        ArrowStyle.NEEDLE -> {
            // コンパスの針のように、上半分(先端側)と下半分(尾側)で濃さを変える
            val topHalf = Path().apply {
                moveTo(size.width / 2, -15f)
                lineTo(size.width * 0.68f, size.height / 2)
                lineTo(size.width * 0.32f, size.height / 2)
                close()
            }
            val bottomHalf = Path().apply {
                moveTo(size.width * 0.32f, size.height / 2)
                lineTo(size.width * 0.68f, size.height / 2)
                lineTo(size.width / 2, size.height + 15f)
                close()
            }
            drawPath(path = topHalf, color = color)
            // ★下半分は同じ色を半透明(alpha = 0.35)にして、針のような立体感を出す
            drawPath(path = bottomHalf, color = color.copy(alpha = 0.35f))
        }

        ArrowStyle.THIN -> {
            // 塗りつぶしではなく、線(Stroke)だけで矢印の形を描くシンプルなデザイン
            val path = Path().apply {
                // 縦の軸線
                moveTo(size.width / 2, size.height * 0.1f)
                lineTo(size.width / 2, size.height)
                // 先端の「く」の字部分
                moveTo(size.width * 0.28f, size.height * 0.35f)
                lineTo(size.width / 2, size.height * 0.1f)
                lineTo(size.width * 0.72f, size.height * 0.35f)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = 6f,
                    cap = StrokeCap.Round, // 線の端を丸くする(角ばらせないための指定)
                    join = StrokeJoin.Round // 線の折れ曲がり部分も丸くする
                )
            )
        }
    }
}

// ★追加:Ambient Mode用の、線だけで描くシンプルな矢印
//    デザイン設定に関わらず、Ambient中は常にこの形状を使う(焼き付き防止・省電力のため)
// ★変更:Ambient Mode用の矢印を、デフォルト矢印(TRIANGLE)と同じ輪郭のアウトラインに変更
fun DrawScope.drawAmbientArrowShape(color: Color) {
    // ★変更:TRIANGLEと全く同じ形の輪郭を使う
    val path = Path().apply {
        moveTo(size.width / 2, 0f)
        lineTo(size.width * 0.75f, size.height)
        lineTo(size.width / 2, size.height * 0.75f)
        lineTo(size.width * 0.25f, size.height)
        close()
    }

    // ★変更:drawPathのstyleにStrokeを指定することで、塗りつぶしではなく輪郭線だけを描く
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 3f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round // 角の接続部分も丸くして、とがりすぎないようにする
        )
    )
}