plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

android {
    namespace = "com.alcint.pargelium"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alcint.pargelium"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "09032026-Unstable"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose (UI)
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("com.airbnb.android:lottie-compose:6.4.0")
    implementation("androidx.compose.material3:material3:1.5.0-alpha14")
    implementation("ir.mahozad.multiplatform:wavy-slider:2.2.0")

    //Coil
    implementation("io.coil-kt:coil:2.6.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Icons & Palette
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Images (Glide)
    implementation("com.github.bumptech.glide:compose:1.0.0-beta01")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    //Language
    implementation("androidx.appcompat:appcompat:1.6.1")

    //Canvas
    implementation("io.coil-kt:coil-gif:2.5.0")

    // Audio Engine (Media3)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Navigation & Permissions
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    implementation("androidx.core:core-splashscreen:1.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}