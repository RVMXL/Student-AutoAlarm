plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ru.uniautoalarm.kgeu"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.uniautoalarm.kgeu"
        minSdk = 26
        targetSdk = 35
        // Версионирование: versionCode = major*10000 + minor*100 + patch (0.1.0 -> 100)
        versionCode = 100
        versionName = "0.1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM управляет версиями)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Навигация
    implementation(libs.androidx.navigation.compose)

    // Room (кэш расписания, исключения, свои занятия)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (настройки)
    implementation(libs.androidx.datastore.preferences)

    // WorkManager (фоновая перепроверка расписания)
    implementation(libs.androidx.work.runtime.ktx)

    // Сеть: OkHttp + kotlinx.serialization (API kabinet.kgeu.ru)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Корутины
    implementation(libs.kotlinx.coroutines.android)

    // Coil (своя картинка на фон)
    implementation(libs.coil.compose)
}
