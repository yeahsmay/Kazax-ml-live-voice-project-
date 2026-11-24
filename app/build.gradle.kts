import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ver = Properties().apply { file("../version.properties").inputStream().use { load(it) } }
val vMajor = ver.getProperty("MAJOR").toInt()
val vMinor = ver.getProperty("MINOR").toInt()
val vPatch = ver.getProperty("PATCH").toInt()

android {
    namespace = "id.kazax.mlassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.kazax.mlassistant"
        minSdk = 24            // Android 7.0+ (sekitar 97% HP aktif)
        targetSdk = 34
        versionCode = vMajor * 10000 + vMinor * 100 + vPatch
        versionName = "$vMajor.$vMinor.$vPatch"
    }

    // Kunci tanda tangan tetap -> APK versi baru bisa menimpa (update) versi lama.
    // Untuk distribusi publik, ganti dengan keystore milik Anda sendiri.
    signingConfigs {
        create("kazax") {
            storeFile = file("../kazax-release.jks")
            storePassword = "kazax-ml-2026"
            keyAlias = "kazax"
            keyPassword = "kazax-ml-2026"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("kazax") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("kazax")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
