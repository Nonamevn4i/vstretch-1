plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vstretch.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vstretch.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2400
        versionName = "2.4.0"
        // App không có mã native -> chạy thuần trên arm64-v8a; filter để chắc chắn không lẫn ABI khác
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Ký bằng debug key để APK release cài được ngay (tự dùng cá nhân)
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
