plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.rangervault"
    compileSdk = 35 // Changed to 35 (Stable) unless you are sure you have SDK 36 installed

    defaultConfig {
        applicationId = "com.example.rangervault"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // --- 1. CORE ANDROID & COMPOSE (Standard) ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // --- 2. ACTIVITY & APPCOMPAT (Needed for Biometrics) ---
    implementation("androidx.appcompat:appcompat:1.6.1")

    // --- 3. BIOMETRICS (Fingerprint Security) ---
    implementation("androidx.biometric:biometric:1.1.0")

    // --- 4. NAVIGATION (Switching Screens) ---
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // --- 5. LOCATION (Geo-Tagging) ---
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // --- 6. ICONS (Lock, Fingerprint, Camera icons) ---
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // --- 7. QR CODE SCANNER & GENERATOR ---
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.1") // Updated to 3.5.1

    // --- 8. NETWORKING (Retrofit) ---
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // --- 9. TESTING (Standard) ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}










//plugins {
//    alias(libs.plugins.android.application)
//    alias(libs.plugins.kotlin.android)
//    alias(libs.plugins.kotlin.compose)
//}
//
//android {
//    namespace = "com.example.rangervault"
//    compileSdk = 36
//
//    defaultConfig {
//        applicationId = "com.example.rangervault"
//        minSdk = 24
//        targetSdk = 36
//        versionCode = 1
//        versionName = "1.0"
//
//        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
//    }
//
//    buildTypes {
//        release {
//            isMinifyEnabled = false
//            proguardFiles(
//                getDefaultProguardFile("proguard-android-optimize.txt"),
//                "proguard-rules.pro"
//            )
//        }
//    }
//    compileOptions {
//        sourceCompatibility = JavaVersion.VERSION_11
//        targetCompatibility = JavaVersion.VERSION_11
//    }
//    kotlinOptions {
//        jvmTarget = "11"
//    }
//    buildFeatures {
//        compose = true
//    }
//}
//
//dependencies {
//    //adding biometric
//    implementation("androidx.appcompat:appcompat:1.6.1")
//    implementation("androidx.biometric:biometric:1.1.0")
//    // Navigation
//    implementation("androidx.navigation:navigation-compose:2.7.7")
//
//    //Location - Vibhu
//    implementation("com.google.android.gms:play-services-location:21.0.1")
//
//    // QR Tools
//    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
//    implementation("com.google.zxing:core:3.4.1")
//
//    // Networking
//    implementation("com.squareup.retrofit2:retrofit:2.9.0")
//    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
//
//    // Icons
//    implementation("androidx.compose.material:material-icons-extended:1.6.0")
//
////    // Networking (To talk to Node.js)
////    implementation("com.squareup.retrofit2:retrofit:2.9.0")
////    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
//
////    // QR Code Generation
////    implementation("com.google.zxing:core:3.5.1")
////    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
//
//    implementation(libs.androidx.core.ktx)
//    implementation(libs.androidx.lifecycle.runtime.ktx)
//    implementation(libs.androidx.activity.compose)
//    implementation(platform(libs.androidx.compose.bom))
//    implementation(libs.androidx.compose.ui)
//    implementation(libs.androidx.compose.ui.graphics)
//    implementation(libs.androidx.compose.ui.tooling.preview)
//    implementation(libs.androidx.compose.material3)
//    implementation(libs.androidx.appcompat)
//    testImplementation(libs.junit)
//    androidTestImplementation(libs.androidx.junit)
//    androidTestImplementation(libs.androidx.espresso.core)
//    androidTestImplementation(platform(libs.androidx.compose.bom))
//    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
//    debugImplementation(libs.androidx.compose.ui.tooling)
//    debugImplementation(libs.androidx.compose.ui.test.manifest)
//}