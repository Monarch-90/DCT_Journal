plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dct_journal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dct_journal"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

    /** 1. Определяем измерение (группу) для наших вариантов */
    flavorDimensions.add("scannerType") // Любое название

    /** 2. Определяем сами варианты (flavors) */
    productFlavors {
        create("cameraScanner") {
            dimension = "scannerType"
            applicationIdSuffix = ".camera" // Добавляет уникальный суффикс
            versionNameSuffix = "-camera"   // Уникальная версия для этого варианта
            buildConfigField("String", "SCANNER_TYPE", "\"Camera\"") // Поле для BuildConfig
        }

        create("hardwareScanner") {
            dimension = "scannerType"
            applicationIdSuffix = ".hardware"
            versionNameSuffix = "-hardware"
            buildConfigField("String", "SCANNER_TYPE", "\"Hardware\"") // Поле для BuildConfig
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    /** Для работы AppCompatActivity() */
    implementation("androidx.appcompat:appcompat:1.7.0")

    /** ZXing для системы сканирования */
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.4.1")

    /** Retrofit — для реализации API-клиента и отправки данных на сервер */
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    /** Koin */
    implementation("io.insert-koin:koin-android:3.4.0")

    /** Kotlin coroutines (Flow) */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    /** OkHttp (Transitively уже есть, но можно указать явно) */
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}