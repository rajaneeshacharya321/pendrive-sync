plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.school.pendrivesync"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.school.pendrivesync"
        minSdk = 24
        targetSdk = 30   // keeps plain File access to USB drives working with "All files access"
        // goes up automatically with every GitHub build, so updates always install over the old app
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.2"
    }
    signingConfigs {
        // Same key on every build => new APKs install as UPDATES (data and PIN are kept)
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.core:core-ktx:1.12.0") }
