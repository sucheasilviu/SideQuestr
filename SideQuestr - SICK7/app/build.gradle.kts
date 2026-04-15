import java.util.Properties
import java.io.FileInputStream

// --- ADDED FOR SECRETS: Load the local.properties file ---
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
// --------------------------------------------------------

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.gps_project"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.gps_project"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- ADDED FOR SECRETS: Extract properties ---
        val mapsApiKey = localProperties.getProperty("MAPS_API_KEY") ?: ""
        val mapId = localProperties.getProperty("MAP_ID") ?: ""
        val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""

        // Expose to AndroidManifest.xml
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        // Expose to Kotlin files via BuildConfig
        buildConfigField("String", "MAP_ID", "\"$mapId\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        // ---------------------------------------------
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
    buildFeatures {
        compose = true
        buildConfig = true // --- ADDED FOR SECRETS: Enables BuildConfig generation ---
    }
}

dependencies {
    // Firebase Storage (to save the images)
    implementation("com.google.firebase:firebase-storage")

// Google AI client for Gemini Vision
    implementation("com.google.ai.client.generativeai:generativeai:0.7.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation(platform("com.google.firebase:firebase-bom:32.7.0")) // Import the BOM
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")


    implementation("io.coil-kt:coil-compose:2.5.0")
// CameraX core library
    val camerax_version = "1.3.3"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
// If you want to use the CameraX lifecycle library
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
// If you want to use the CameraX view class (simplifies preview setup)
    implementation("androidx.camera:camera-view:${camerax_version}")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}