plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.companion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.companion"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Networking (OkHttp for WebSockets)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // WebRTC (Official Google build)
    implementation("com.infobip:google-webrtc:1.0.0035529")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
}
