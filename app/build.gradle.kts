plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun buildConfigString(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "cn.liveroom.app"
    compileSdk = 35

    val backendHost = providers.gradleProperty("backendHost").orElse("10.0.2.2").get()
    val billdPort = providers.gradleProperty("billdPort").orElse("4300").get()
    val srsApiPort = providers.gradleProperty("srsApiPort").orElse("1985").get()
    val srsPlaybackPort = providers.gradleProperty("srsPlaybackPort").orElse("8088").get()
    val srsRtmpPort = providers.gradleProperty("srsRtmpPort").orElse("1935").get()
    val livekitPort = providers.gradleProperty("livekitPort").orElse("7880").get()
    val openImApiPort = providers.gradleProperty("openImApiPort").orElse("10002").get()
    val openImWsPort = providers.gradleProperty("openImWsPort").orElse("10001").get()
    val updateHost = providers.gradleProperty("updateHost").orElse(backendHost).get()
    val updatePort = providers.gradleProperty("updatePort").orElse("4310").get()
    val appVersionCode = providers.gradleProperty("appVersionCode").orElse("1").get().toInt()
    val appVersionName = providers.gradleProperty("appVersionName").orElse("0.1.0").get()
    val previewQumiLogin = providers.gradleProperty("previewQumiLogin").orElse("false").get().toBoolean()
    val firebaseApiKey = providers.gradleProperty("firebaseApiKey").orElse("").get()
    val firebaseAppId = providers.gradleProperty("firebaseAppId").orElse("").get()
    val firebaseProjectId = providers.gradleProperty("firebaseProjectId").orElse("").get()
    val firebaseSenderId = providers.gradleProperty("firebaseSenderId").orElse("").get()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    defaultConfig {
        applicationId = "cn.liveroom.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = providers.gradleProperty("deviceCheckRunner").orElse("cn.liveroom.app.BeautyDeviceCheck").get()
        buildConfigField("String", "SRS_URL", "\"http://$backendHost:$srsApiPort\"")
        buildConfigField("String", "BILLD_API_URL", "\"http://$backendHost:$billdPort\"")
        buildConfigField("String", "SRS_PLAYBACK_URL", "\"http://$backendHost:$srsPlaybackPort\"")
        buildConfigField("String", "SRS_RTMP_URL", "\"rtmp://$backendHost:$srsRtmpPort\"")
        // LiveKit the phone can reach. Emulator default 10.0.2.2:7880; VMware uses -PlivekitPort=17880.
        // 1v1 private calls dial this URL. The token still comes from Billd and does not include the API secret.
        buildConfigField("String", "LIVEKIT_URL", "\"ws://$backendHost:$livekitPort\"")
        buildConfigField("String", "OPENIM_API_URL", "\"http://$backendHost:$openImApiPort\"")
        buildConfigField("String", "OPENIM_WS_URL", "\"ws://$backendHost:$openImWsPort\"")
        buildConfigField("String", "APP_UPDATE_URL", "\"http://$updateHost:$updatePort/version.json\"")
        buildConfigField("boolean", "PREVIEW_QUMI_LOGIN", previewQumiLogin.toString())
        buildConfigField("String", "FIREBASE_API_KEY", buildConfigString(firebaseApiKey))
        buildConfigField("String", "FIREBASE_APP_ID", buildConfigString(firebaseAppId))
        buildConfigField("String", "FIREBASE_PROJECT_ID", buildConfigString(firebaseProjectId))
        buildConfigField("String", "FIREBASE_SENDER_ID", buildConfigString(firebaseSenderId))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("io.livekit:livekit-android:2.29.2")
    implementation("com.github.pedroSG94.RootEncoder:library:2.6.7")
    // Apache-2.0. Models are bundled for offline, on-device face/body tracking.
    implementation("com.google.mediapipe:tasks-vision:0.10.35")
    implementation("jp.co.cyberagent.android:gpuimage:2.1.0")
    implementation("io.socket:socket.io-client:2.1.0")
    // YYEVA-Android 1.1.73, Apache-2.0. TextureView player is EvaAnimViewV3.
    implementation("com.github.yylive.YYEVA-Android:yyeva:1.1.73")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("com.google.zxing:core:3.5.4")
    if (file("libs/open_im_sdk.aar").exists()) {
        implementation(files("libs/open_im_sdk.aar"))
    }
    debugImplementation("androidx.compose.ui:ui-tooling")
}
