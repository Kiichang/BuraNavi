import java.util.Properties // ★追加:local.propertiesを読み込むために必要
import java.io.FileInputStream // ★追加:ファイルを読み込むために必要

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.buranavi.mobile"
    compileSdk {
        version = release(37)
    }

    // ★追加:local.propertiesファイルを読み込む処理
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
    }

    defaultConfig {
        applicationId = "jp.buranavi.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"

        // ★追加:読み込んだAPIキーを、コード側から BuildConfig.GEOCODING_API_KEY として使えるようにする
        buildConfigField(
            "String",
            "GEOCODING_API_KEY",
            "\"${localProperties.getProperty("GEOCODING_API_KEY", "")}\""
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // ★追加:BuildConfigクラスの生成を有効にする
    }
}

dependencies {
    // ★確認・なければ追加:スマホ⇔ウォッチ間の通信(Data Layer API)に必要
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.material3)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    debugImplementation(libs.ui.tooling)
}