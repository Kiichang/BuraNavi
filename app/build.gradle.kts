plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.buranavi"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "jp.buranavi.app"
        minSdk = 30
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"

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
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.play.services.wearable)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)
    debugImplementation(libs.ui.tooling)
    // ② Googleが提供する位置情報取得ライブラリ(FusedLocationProviderClientを使うために必要)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Wear OS専用のCompose Materialライブラリ
    implementation("androidx.wear.compose:compose-material:1.4.0")
    // ★追加:設定値やちょっとしたデータを保存しておくための仕組み(DataStore)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // ★追加:歯車アイコンなど、Material Designの標準アイコンを使うためのライブラリ
    implementation("androidx.compose.material:material-icons-core:1.6.8")
    // ★追加
    implementation("androidx.health:health-services-client:1.1.0-rc02")
    // ★追加:Ambient Mode対応のための、Google製の補助ライブラリ(Horologist)
    implementation("com.google.android.horologist:horologist-compose-layout:0.7.14-beta")
}