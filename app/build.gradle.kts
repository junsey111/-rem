plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cn.liveroom.app"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    defaultConfig {
        applicationId = "cn.liveroom.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SRS_URL", "\"http://10.0.2.2:1985\"")
        buildConfigField("String", "BILLD_API_URL", "\"http://10.0.2.2:4300\"")
        buildConfigField("String", "SRS_PLAYBACK_URL", "\"http://10.0.2.2:8088\"")
        buildConfigField("String", "LIVEKIT_URL", "\"ws://10.0.2.2:7880\"")
        buildConfigField("String", "OPENIM_API_URL", "\"http://10.0.2.2:10002\"")
        buildConfigField("String", "OPENIM_WS_URL", "\"ws://10.0.2.2:10001\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("io.livekit:livekit-android:2.29.2")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    if (file("libs/open_im_sdk.aar").exists()) {
        implementation(files("libs/open_im_sdk.aar"))
    }
    debugImplementation("androidx.compose.ui:ui-tooling")
}
