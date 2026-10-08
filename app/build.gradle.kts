plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.muse.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.muse.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 19
        versionName = "1.0.19"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Default placeholder for YouTube API Key (can be overridden in local.properties)
        buildConfigField("String", "YOUTUBE_API_KEY", "\"AIzaSyYOUR_DEFAULT_YOUTUBE_API_KEY_HERE\"")
    }

    signingConfigs {
        // Usa il debug keystore di Android SDK: firmato ma installabile via sideload
        create("debugKeystore") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // ── BUILD 1: app-debug.apk ────────────────────────────────────────────
        // Standard Android debug: debuggabile via USB, senza ottimizzazioni.
        // Ideale per sviluppo e test rapidi in Android Studio.
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            // Mantiene il nome default: app-debug.apk
        }

        // ── BUILD 2: Aura-{versionName}-release.apk ───────────────────────────
        // Build ottimizzata: R8 minification + resource shrinking + firmata.
        // ~30-50% più piccola e più veloce del debug. Installabile via sideload.
        release {
            isDebuggable = false
            isMinifyEnabled = true          // R8: rimuove codice inutilizzato
            isShrinkResources = true        // rimuove risorse non referenziate
            signingConfig = signingConfigs.getByName("debugKeystore")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Rinomina l'output in Aura-{versionName}-release.apk
            applicationVariants.all {
                if (buildType.name == "release") {
                    outputs
                        .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
                        .forEach { it.outputFileName = "Aura-$versionName-release.apk" }
                }
            }
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui.text.google.fonts)
    debugImplementation(libs.androidx.ui.tooling)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit & Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil)

    // Palette API - estrazione colore dominante dalle copertine
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Firebase & Auth
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.play.services.auth)

    // ExoPlayer (Media3) - riproduzione audio/video nativa
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")

    // NewPipeExtractor
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.4")
    // ExoPlayer OkHttp Extension (utile per il passaggio del downloader)
    implementation("androidx.media3:media3-datasource-okhttp:1.3.1")

    // FFmpeg per estrarre e muxare video (usato per lo stato WhatsApp)
    // Fork mantenuto dalla community, drop-in replacement di com.arthenica (ritirato apr 2025)
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-https:8.1.7")
    // Dipendenza transitiva richiesta da ffmpegkit
    implementation("com.arthenica:smart-exception-java:0.2.1")

    // Google Cast SDK (Chromecast / Google Home / Nest Audio)
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    // MediaRouter per discovery dei dispositivi Cast
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    // Compose Reorderable per Drag and Drop
    implementation("sh.calvin.reorderable:reorderable:2.1.1")
    
    // Core SplashScreen
    implementation("androidx.core:core-splashscreen:1.0.1")
}

configurations.all {
    exclude(group = "com.google.protobuf", module = "protobuf-javalite")
}
