plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.marmaton.agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marmaton.agent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
        freeCompilerArgs = freeCompilerArgs + listOf("-Xskip-metadata-version-check")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    val postHogApiKey = (project.findProperty("POSTHOG_API_KEY") as? String) ?: System.getenv("POSTHOG_API_KEY") ?: ""
    val postHogHost = (project.findProperty("POSTHOG_HOST") as? String) ?: System.getenv("POSTHOG_HOST") ?: "https://eu.i.posthog.com"

    defaultConfig {
        buildConfigField("String", "POSTHOG_API_KEY", "\"$postHogApiKey\"")
        buildConfigField("String", "POSTHOG_HOST", "\"$postHogHost\"")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.posthog.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ML Kit GenAI Prompt API
    implementation(libs.google.mlkit.genai.prompt)

    // MediaPipe GenAI (LLM Inference)
    implementation(libs.google.mediapipe.genai)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Security Crypto (for EncryptedSharedPreferences)
    implementation(libs.androidx.security.crypto)

    // OkHttp Client
    implementation(libs.okhttp.core)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
}
