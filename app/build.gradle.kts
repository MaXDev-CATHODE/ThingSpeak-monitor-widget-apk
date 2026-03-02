import java.util.Properties
import java.util.Base64
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProperties = Properties()
val rootLocal = rootProject.file("local.properties")
val binLocal = rootProject.file(".bin/local.properties")
val lpFile = if (rootLocal.exists()) rootLocal else binLocal

if (lpFile.exists()) {
    lpFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.thingspeak.monitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thingspeak.monitor"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        resConfigs("en")
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
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
        buildConfig = true
    }

    signingConfigs {
        // Support for Base64 encoded keystore in CI/CD (GitHub Secrets & P0 Fix)
        val storeBase64 = System.getenv("RELEASE_STORE_FILE_BASE64")
        val storeFileVar = System.getenv("RELEASE_STORE_FILE") ?: localProperties.getProperty("releaseStoreFile")
        
        var sFile: File? = null
        if (storeBase64 != null) {
            val tempKeystore = File.createTempFile("signing", ".jks")
            // Strip PEM headers and whitespace before decoding
            val cleanBase64 = storeBase64
                .replace(Regex("-----[A-Z ]+-----"), "")
                .replace(Regex("\\s"), "")
            tempKeystore.writeBytes(Base64.getMimeDecoder().decode(cleanBase64))
            tempKeystore.deleteOnExit()
            sFile = tempKeystore
        } else if (storeFileVar != null) {
            sFile = file(storeFileVar)
        }

        val sPassword = System.getenv("RELEASE_STORE_PASSWORD") ?: localProperties.getProperty("releaseStorePassword")
        val kAlias = System.getenv("RELEASE_KEY_ALIAS") ?: localProperties.getProperty("releaseKeyAlias") ?: "thingspeak"
        val kPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: localProperties.getProperty("releaseKeyPassword")

        if (sFile != null && sPassword != null && kPassword != null) {
            create("release") {
                storeFile = sFile
                storePassword = sPassword
                keyAlias = kAlias
                keyPassword = kPassword
            }
        }
    }

    buildTypes {
        release {
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null && releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

dependencies {
    // ── Compose BOM ──────────────────────────────────────────────
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // ── AndroidX Core ────────────────────────────────────────────
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)

    // ── Lifecycle (StateFlow, ViewModel) ─────────────────────────
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // ── Navigation ───────────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Hilt (Dependency Injection) ──────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // ── Room (Local Database — Offline-First) ────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Network (Retrofit + OkHttp + kotlinx.serialization) ──────
    implementation(libs.retrofit)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    // ── Coroutines ───────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── Glance (Modern App Widget) ───────────────────────────────
    implementation(libs.glance)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // ── WorkManager (Background Sync for Widget) ─────────────────
    implementation(libs.work.runtime)

    // ── DataStore (Lightweight Preferences) ──────────────────────
    implementation(libs.datastore.preferences)

    // ── Charts (MPAndroidChart — pinch-zoom, scroll, date range) ─
    implementation(libs.mpandroidchart)

    // ── Ads (AdMob — banner placeholder) ─────────────────────────
    implementation(libs.admob)
    implementation(libs.security.crypto)

    // ── Testing ──────────────────────────────────────────────────
    testImplementation(libs.junit)
}

