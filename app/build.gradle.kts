plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.se_04.enoti"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.se_04.enoti"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.2.0"
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.impress)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging:25.0.1")

    // Các thư viện khác
    implementation("io.github.chaosleung:pinview:1.4.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.android.volley:volley:1.2.1")
    implementation("com.github.bumptech.glide:glide:5.0.5")

    implementation("com.github.SUPERCILEX.poi-android:poi:3.17")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
}

