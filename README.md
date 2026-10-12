# BuraNavi

BuraNaviは、Androidスマートフォンで選択した目的地をWear OSウォッチへ送り、  
ウォッチ上で目的地までの**方向と距離**をシンプルに確認できるナビゲーションアプリです。

公式サイト：  
https://buranavi.app

---

## Features

- Google Mapsから目的地をBuraNaviへ共有
- スマートフォンからWear OSウォッチへ目的地を同期
- ウォッチ上で目的地までの方向を表示
- ウォッチ上で目的地までの距離を表示
- 到着時の通知
- 歩数表示
- 心拍数表示
- Ambient Mode対応

---

## Requirements

### Android smartphone

- Android 8.0以上

### Wear OS

- Wear OS 3以降
- AndroidスマートフォンとWear OSウォッチの接続が必要です

すべての端末での動作を保証するものではありません。

---

## Download

最新版はGitHub Releasesからダウンロードできます。

https://github.com/Kiichang/BuraNavi/releases/latest

以下の2つのAPKを使用します。

- `BuraNavi-mobile.apk` — Androidスマートフォン用
- `BuraNavi-wear.apk` — Wear OSウォッチ用

スマートフォン版とWear OS版の両方をインストールしてください。

詳しいインストール方法は公式サイトをご確認ください。

https://buranavi.app

---

## How to use

1. スマートフォンのGoogle Mapsで目的地を選択します
2. 「共有」からBuraNaviを選択します
3. 目的地がWear OSウォッチへ同期されます
4. ウォッチ上で目的地までの方向と距離を確認します

---

## Project structure

    BuraNavi/
    ├── app/        # Wear OS app
    ├── mobile/     # Android smartphone app
    └── ...

`app` がWear OS側、`mobile` がAndroidスマートフォン側です。

---

## Privacy

BuraNaviは、ナビゲーション機能を提供するために端末の位置情報を使用します。

目的地を座標へ変換する必要がある場合、Google Geocoding APIを利用することがあります。

Wear OS版では、ユーザーが機能を有効にし必要な権限を許可した場合、歩数や心拍数などの情報を取得してウォッチ上に表示します。

BuraNaviの開発者が運営する独自サーバーへ、現在地・心拍数・歩数などを保存する機能はありません。

詳細は公式サイトのPrivacy Policyをご確認ください。

https://buranavi.app/#privacy

---

## Distribution

BuraNaviは現在、Google Playでは配布していません。

公式サイトおよびGitHub ReleasesからAPKファイルを直接配布しています。

---

## Release

Current release:

**v1.0.0**

https://github.com/Kiichang/BuraNavi/releases/tag/v1.0.0

---

## Contact

BuraNaviに関するお問い合わせ：

**contact@buranavi.app**

公式サイト：

https://buranavi.app

---

## Disclaimer

BuraNaviはGoogle Mapsとの公式提携アプリではありません。

Google Maps、Android、Wear OSおよびその他のGoogle製品・サービスは、Google LLCの商標または登録商標です。
