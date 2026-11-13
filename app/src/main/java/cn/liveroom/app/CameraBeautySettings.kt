package cn.liveroom.app

import android.content.Context

data class CameraBeautySettings(
    val smoothing: Boolean = false,
    val brightness: Float = 0f,
    val saturation: Float = 1f,
    val enabled: Boolean = true,
    val highPerformance: Boolean = true,
    val filter: String = "original",
    val filterStrength: Float = .65f,
    val amounts: Map<String, Float> = emptyMap(),
) {
    fun amount(key: String): Float = (amounts[key] ?: when (key) {
        "smooth" -> if (smoothing) .4f else 0f
        "whiten" -> brightness.coerceAtLeast(0f) / .3f
        else -> 0f
    }).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

    fun withAmount(key: String, value: Float) = copy(amounts = amounts + (key to value.coerceIn(0f, 1f)))

    val needsFace: Boolean get() = enabled && beautyShapeOptions.any { amount(it.key) > 0f } ||
        enabled && listOf("brightEyes", "whiteTeeth", "darkCircles", "smileLines").any { amount(it) > 0f }

    val needsBody: Boolean get() = enabled && beautyBodyOptions.any { amount(it.key) > 0f }

    val hasEffects: Boolean get() = enabled && (beautySkinOptions.any { amount(it.key) > 0f } ||
        beautyShapeOptions.any { amount(it.key) > 0f } || needsBody || brightness < 0f || saturation != 1f ||
        (filter != "original" && filterStrength > 0f))

    fun reset(section: BeautySection): CameraBeautySettings = when (section) {
        BeautySection.SKIN -> copy(smoothing = false, brightness = 0f, saturation = 1f,
            amounts = amounts - beautySkinOptions.map { it.key }.toSet())
        BeautySection.SHAPE -> copy(amounts = amounts - beautyShapeOptions.map { it.key }.toSet())
        BeautySection.BODY -> copy(amounts = amounts - beautyBodyOptions.map { it.key }.toSet())
        BeautySection.FILTER -> copy(filter = "original", filterStrength = .65f)
    }
}

enum class BeautySection(val title: String) { SKIN("美肤"), SHAPE("美型"), BODY("美体"), FILTER("滤镜") }
data class BeautyOption(val key: String, val title: String)
internal val beautySkinOptions = listOf(
    BeautyOption("smooth", "磨皮"), BeautyOption("blemish", "祛斑痘"), BeautyOption("whiten", "美白"),
    BeautyOption("rosy", "红润"), BeautyOption("clarity", "清晰"), BeautyOption("sharpen", "锐化"),
    BeautyOption("brightEyes", "亮眼"), BeautyOption("whiteTeeth", "美牙"),
    BeautyOption("darkCircles", "黑眼圈"), BeautyOption("smileLines", "法令纹"),
)
internal val beautyShapeOptions = listOf(
    BeautyOption("slim", "瘦脸"), BeautyOption("vFace", "V脸"), BeautyOption("narrow", "窄脸"),
    BeautyOption("short", "短脸"), BeautyOption("small", "小脸"), BeautyOption("cheek", "瘦颧骨"),
    BeautyOption("jaw", "瘦下颌"), BeautyOption("bigEyes", "大眼"), BeautyOption("roundEyes", "圆眼"),
    BeautyOption("chin", "下巴"), BeautyOption("forehead", "额头"), BeautyOption("nose", "瘦鼻"),
    BeautyOption("mouth", "嘴型"), BeautyOption("lips", "丰唇"), BeautyOption("eyeDistance", "眼距"),
    BeautyOption("eyePosition", "眼位置"), BeautyOption("noseLength", "鼻长"),
    BeautyOption("philtrum", "人中"), BeautyOption("smile", "微笑嘴角"), BeautyOption("brows", "眉位置"),
)
internal val beautyBodyOptions = listOf(
    BeautyOption("bodyWaist", "瘦腰"), BeautyOption("bodyShoulders", "窄肩"),
    BeautyOption("bodyHips", "收胯"), BeautyOption("bodyLegs", "瘦腿"),
)
internal val beautyFilterOptions = listOf(
    BeautyOption("original", "原图"), BeautyOption("natural", "自然"), BeautyOption("gray", "质感灰"),
    BeautyOption("bright", "白亮"), BeautyOption("pink", "粉嫩"), BeautyOption("cool", "冷色调"),
    BeautyOption("warm", "暖阳"), BeautyOption("film", "胶片"),
    BeautyOption("sepia", "琥珀"), BeautyOption("mono", "黑白"), BeautyOption("vignette", "暗角"),
)

object CameraBeautyPreferences {
    private fun preferences(context: Context, userId: Long) =
        context.getSharedPreferences("camera_beauty_$userId", Context.MODE_PRIVATE)

    fun load(context: Context, userId: Long) = preferences(context, userId).let { prefs ->
        CameraBeautySettings(
            smoothing = prefs.getBoolean("smoothing", false),
            brightness = prefs.getFloat("brightness", 0f).coerceIn(-0.3f, 0.3f),
            saturation = prefs.getFloat("saturation", 1f).coerceIn(0.5f, 1.5f),
            enabled = prefs.getBoolean("enabled", true),
            highPerformance = prefs.getBoolean("highPerformance", true),
            filter = prefs.getString("filter", "original")?.takeIf { key -> beautyFilterOptions.any { it.key == key } } ?: "original",
            filterStrength = prefs.getFloat("filterStrength", .65f).coerceIn(0f, 1f),
            amounts = (beautySkinOptions + beautyShapeOptions + beautyBodyOptions).filter { prefs.contains("amount_${it.key}") }
                .associate { it.key to prefs.getFloat("amount_${it.key}", 0f).coerceIn(0f, 1f) },
        )
    }

    fun save(context: Context, userId: Long, settings: CameraBeautySettings) {
        preferences(context, userId).edit()
            .putBoolean("smoothing", settings.smoothing)
            .putFloat("brightness", settings.brightness.coerceIn(-0.3f, 0.3f))
            .putFloat("saturation", settings.saturation.coerceIn(0.5f, 1.5f))
            .putBoolean("enabled", settings.enabled)
            .putBoolean("highPerformance", settings.highPerformance)
            .putString("filter", settings.filter)
            .putFloat("filterStrength", settings.filterStrength)
            .also { editor -> (beautySkinOptions + beautyShapeOptions + beautyBodyOptions).forEach { option ->
                val amount = settings.amounts[option.key]
                if (amount == null) editor.remove("amount_${option.key}") else editor.putFloat("amount_${option.key}", amount)
            } }
            .apply()
    }
}
