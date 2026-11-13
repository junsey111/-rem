package cn.liveroom.app

import kotlin.math.abs

internal data class BeautyPoint(val x: Float, val y: Float)
internal data class BeautyFaceGeometry(
    val center: BeautyPoint, val width: Float, val height: Float,
    val leftEye: BeautyPoint, val rightEye: BeautyPoint, val nose: BeautyPoint,
    val mouth: BeautyPoint, val leftMouth: BeautyPoint, val rightMouth: BeautyPoint,
    val measuredChin: BeautyPoint? = null, val measuredForehead: BeautyPoint? = null,
) {
    val chin get() = measuredChin ?: BeautyPoint(center.x, center.y - height * .46f)
    val forehead get() = measuredForehead ?: BeautyPoint(center.x, center.y + height * .35f)
}

internal data class BeautyWarp(
    val center: BeautyPoint, val radiusX: Float, val radiusY: Float,
    val moveX: Float = 0f, val moveY: Float = 0f, val scaleX: Float = 0f, val scaleY: Float = 0f,
)

/** Inverse texture mapping: a positive scale samples farther away, making that region smaller. */
internal fun beautyWarps(settings: CameraBeautySettings, face: BeautyFaceGeometry?): List<BeautyWarp> {
    if (!settings.enabled || face == null || face.width <= 0f || face.height <= 0f) return emptyList()
    val result = mutableListOf<BeautyWarp>()
    val w = face.width
    val h = face.height
    fun scale(key: String, center: BeautyPoint, rx: Float, ry: Float, x: Float, y: Float) {
        val amount = settings.amount(key)
        if (amount > 0f) result += BeautyWarp(center, rx, ry, scaleX = x * amount, scaleY = y * amount)
    }
    fun move(key: String, center: BeautyPoint, rx: Float, ry: Float, x: Float, y: Float) {
        val amount = settings.amount(key)
        if (amount > 0f) result += BeautyWarp(center, rx, ry, moveX = x * amount, moveY = y * amount)
    }
    val lower = face.center.copy(y = face.center.y - h * .18f)
    scale("slim", lower, w * .6f, h * .5f, .16f, 0f)
    scale("vFace", face.chin.copy(y = face.chin.y + h * .18f), w * .5f, h * .35f, .25f, -.04f)
    scale("narrow", face.center, w * .65f, h * .65f, .16f, 0f)
    scale("short", face.center, w * .65f, h * .65f, 0f, .14f)
    scale("small", face.center, w * .68f, h * .68f, .12f, .12f)
    for (side in listOf(-1f, 1f)) {
        val cheek = BeautyPoint(face.center.x + side * w * .36f, face.center.y + h * .03f)
        move("cheek", cheek, w * .27f, h * .24f, side * w * .055f, 0f)
        val jaw = cheek.copy(y = face.center.y - h * .24f)
        move("jaw", jaw, w * .29f, h * .3f, side * w * .065f, 0f)
    }
    for (eye in listOf(face.leftEye, face.rightEye)) {
        scale("bigEyes", eye, w * .22f, h * .16f, -.20f, -.20f)
        scale("roundEyes", eye, w * .2f, h * .14f, 0f, -.24f)
        val side = if (eye.x < face.center.x) -1f else 1f
        move("eyeDistance", eye, w * .22f, h * .18f, -side * w * .035f, 0f)
        move("eyePosition", eye, w * .23f, h * .2f, 0f, -h * .022f)
        move("brows", eye.copy(y = eye.y + h * .09f), w * .25f, h * .12f, 0f, -h * .03f)
    }
    move("chin", face.chin, w * .4f, h * .25f, 0f, h * .05f)
    move("forehead", face.forehead, w * .45f, h * .3f, 0f, -h * .04f)
    scale("nose", face.nose, w * .21f, h * .23f, .3f, 0f)
    move("noseLength", face.nose, w * .22f, h * .23f, 0f, h * .025f)
    scale("mouth", face.mouth, w * .28f, h * .16f, .2f, 0f)
    scale("lips", face.mouth, w * .24f, h * .12f, 0f, -.3f)
    move("philtrum", face.mouth, w * .3f, h * .2f, 0f, -h * .03f)
    for (corner in listOf(face.leftMouth, face.rightMouth)) {
        move("smile", corner, w * .15f, h * .13f, 0f, -h * .022f)
    }
    // Keep this in sync with the fixed shader loop and its uniform arrays.
    return result.take(40)
}
