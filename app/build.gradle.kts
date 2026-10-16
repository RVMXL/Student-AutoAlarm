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

    // INFRA-9 (0.11.0): build-tools запинен — иначе сборка тихо берёт любую установленную
    // версию, и aapt/apksigner-поведение может плавать между машинами и CI
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "ru.uniautoalarm.kgeu"
        minSdk = 26
        targetSdk = 35
        // Версионирование: versionCode = major*10000 + minor*100 + patch (0.12.0 -> 1200)
        versionCode = 1200
        versionName = "0.12.0"
    }

    buildTypes {
        release {
            // INFRA-4/SEC-8 (0.10.0): R8 + shrinkResources включены — ранее dex был 45 МБ
            // (icons-extended целиком) при APK 12,5 МБ; теперь мёртвый код/ресурсы вырезаются,
            // читаемый реверс тоже. Правила — proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // ── Подпись релиза: один стабильный ключ из Secrets CI ──────────────────────
    // Обновления поверх старых установок возможны ТОЛЬКО при неизменном ключе
    // подписи. Поэтому ключ не живёт в git и НЕ генерируется сборкой:
    // если env-переменных нет, release-сборка падает ЯВНО (см. guard ниже),
    // а не молча выезжает неподписанной или с новым ключом.
    signingConfigs {
        create("release")
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

// DATA-11: история схемы Room — JSON пишется в app/schemas и коммитится;
// это позволяет MigrationTestHelper и честный diff при будущих миграциях
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Заполнение release-подписи из окружения (в CI — из Secrets GitHub Actions).
// ВАЖНО: signingConfig назначается строго на фазе КОНФИГУРАЦИИ — packageRelease
// захватывает подпись при конфигурировании, назначение в taskGraph.whenReady
// опаздывает и даёт неподписанный app-release-unsigned.apk.
val releaseSigning = android.signingConfigs.getByName("release")
val ksPathEnv = System.getenv("KEYSTORE_FILE")
if (ksPathEnv != null) {
    releaseSigning.storeFile = rootProject.file(ksPathEnv)
    releaseSigning.storePassword = System.getenv("KEYSTORE_PASSWORD")
        ?: throw GradleException("KEYSTORE_FILE задан, но KEYSTORE_PASSWORD нет — подпись неполная")
    releaseSigning.keyAlias = System.getenv("KEY_ALIAS")
        ?: throw GradleException("KEYSTORE_FILE задан, но KEY_ALIAS нет — подпись неполная")
    releaseSigning.keyPassword = System.getenv("KEY_PASSWORD")
        ?: throw GradleException("KEYSTORE_FILE задан, но KEY_PASSWORD нет — подпись неполная")
    android.buildTypes.getByName("release").signingConfig = releaseSigning
} else {
    // Ключа нет: релизная сборка запрещена ЯВНО (автогенерация нового ключа
    // сломала бы обновления поверх старых установок).
    // INFRA-8 (0.11.0): дыра guard закрыта — раньше голый `./gradlew assemble`/
    // `bundle`/`build` собирал unsigned-release тихо (имена задач не содержат
    // «Release», и регэксп их не ловил).
    val requested = gradle.startParameter.taskNames.joinToString(" ")
    val releaseRequested = requested.contains("Release") ||
        Regex("(^|[ :])(build|assemble|bundle)$").containsMatchIn(requested)
    if (releaseRequested) {
        throw GradleException(
            "Сборка release без ключа подписи запрещена: KEYSTORE_FILE не задан. " +
                "Ключ подписи один и хранится в Secrets CI (.github/workflows/release.yml). " +
                "Локально передайте KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD. " +
                "Автогенерация нового ключа запрещена: она ломает обновления поверх старых установок."
        )
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

    // Юнит-тесты (0.9.0: AlarmPlanner, правила доставки, парсер, паритет локалей)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
