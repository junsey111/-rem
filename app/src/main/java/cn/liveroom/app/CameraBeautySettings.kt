package cn.liveroom.app

import android.content.Context

data class CameraBeautySettings(
    val smoothing: Boolean = false,
    val brightness: Float = 0f,
    val saturation: Float = 1f,
)

object CameraBeautyPreferences {
    private fun preferences(context: Context, userId: Long) =
        context.getSharedPreferences("camera_beauty_$userId", Context.MODE_PRIVATE)

    fun load(context: Context, userId: Long) = preferences(context, userId).let { prefs ->
        CameraBeautySettings(
            smoothing = prefs.getBoolean("smoothing", false),
            brightness = prefs.getFloat("brightness", 0f).coerceIn(-0.3f, 0.3f),
            saturation = prefs.getFloat("saturation", 1f).coerceIn(0.5f, 1.5f),
        )
    }

    fun save(context: Context, userId: Long, settings: CameraBeautySettings) {
        preferences(context, userId).edit()
            .putBoolean("smoothing", settings.smoothing)
            .putFloat("brightness", settings.brightness.coerceIn(-0.3f, 0.3f))
            .putFloat("saturation", settings.saturation.coerceIn(0.5f, 1.5f))
            .apply()
    }
}
