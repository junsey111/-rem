plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cn.liveroom.app"
    compileSdk = 35

    val backendHost = providers.gradleProperty("backendHost").orElse("10.0.2.2").get()
    val billdPort = providers.gradleProperty("billdPort").orElse("4300").get()
    val srsApiPort = providers.gradleProperty("srsApiPort").orElse("1985").get()
    val srsPlaybackPort = providers.gradleProperty("srsPlaybackPort").orElse("8088").get()
    val livekitPort = providers.gradleProperty("livekitPort").orElse("7880").get()
    val openImApiPort = providers.gradleProperty("openImApiPort").orElse("10002").get()
    val openImWsPort = providers.gradleProperty("openImWsPort").orElse("10001").get()

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
        buildConfigField("String", "SRS_URL", "\"http://$backendHost:$srsApiPort\"")
        buildConfigField("String", "BILLD_API_URL", "\"http://$backendHost:$billdPort\"")
        buildConfigField("String", "SRS_PLAYBACK_URL", "\"http://$backendHost:$srsPlaybackPort\"")
        buildConfigField("String", "LIVEKIT_URL", "\"ws://$backendHost:$livekitPort\"")
        buildConfigField("String", "OPENIM_API_URL", "\"http://$backendHost:$openImApiPort\"")
        buildConfigField("String", "OPENIM_WS_URL", "\"ws://$backendHost:$openImWsPort\"")
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
    implementation("io.socket:socket.io-client:2.1.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    if (file("libs/open_im_sdk.aar").exists()) {
        implementation(files("libs/open_im_sdk.aar"))
    }
    debugImplementation("androidx.compose.ui:ui-tooling")
}
