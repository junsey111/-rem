package cn.liveroom.app

import kotlin.math.abs
import kotlin.math.hypot

internal data class BeautyBodyGeometry(
    val leftShoulder: BeautyPoint, val rightShoulder: BeautyPoint,
    val leftHip: BeautyPoint, val rightHip: BeautyPoint,
    val leftKnee: BeautyPoint? = null, val rightKnee: BeautyPoint? = null,
    val leftAnkle: BeautyPoint? = null, val rightAnkle: BeautyPoint? = null,
)

/** Localized inverse mappings anchored to measured pose points, never to the screen center. */
internal fun beautyBodyWarps(settings: CameraBeautySettings, body: BeautyBodyGeometry?): List<BeautyWarp> {
    if (!settings.needsBody || body == null) return emptyList()
    fun middle(a: BeautyPoint, b: BeautyPoint) = BeautyPoint((a.x + b.x) / 2, (a.y + b.y) / 2)
    val shoulders = middle(body.leftShoulder, body.rightShoulder)
    val hips = middle(body.leftHip, body.rightHip)
    val shoulderWidth = abs(body.leftShoulder.x - body.rightShoulder.x)
    val hipWidth = abs(body.leftHip.x - body.rightHip.x)
    val torso = shoulders.y - hips.y
    // Sideways, horizontal or cropped detections must not squeeze arbitrary parts of the scene.
    if (torso !in .09f.. .8f || shoulderWidth < .07f || hipWidth < .05f ||
        abs(body.leftShoulder.y - body.rightShoulder.y) > torso * .5f ||
        abs(shoulders.x - hips.x) > torso * .7f) return emptyList()
    val result = mutableListOf<BeautyWarp>()
    fun squeeze(key: String, center: BeautyPoint, rx: Float, ry: Float, strength: Float) {
        val amount = settings.amount(key)
        if (amount > 0f) result += BeautyWarp(center, rx, ry, scaleX = strength * amount)
    }
    squeeze("bodyWaist", BeautyPoint(shoulders.x * .35f + hips.x * .65f, shoulders.y * .35f + hips.y * .65f),
        maxOf(shoulderWidth, hipWidth) * .72f, torso * .42f, .18f)
    squeeze("bodyShoulders", shoulders, shoulderWidth * .78f, torso * .32f, .12f)
    squeeze("bodyHips", hips, hipWidth * .8f, torso * .35f, .16f)
    for ((hip, knee, ankle) in listOf(Triple(body.leftHip, body.leftKnee, body.leftAnkle), Triple(body.rightHip, body.rightKnee, body.rightAnkle))) {
        if (knee == null || knee.y >= hip.y || hypot(knee.x - hip.x, knee.y - hip.y) < .08f) continue
        squeeze("bodyLegs", middle(hip, knee), hipWidth * .34f, abs(hip.y - knee.y) * .65f, .14f)
        if (ankle != null && ankle.y < knee.y && hypot(ankle.x - knee.x, ankle.y - knee.y) > .06f)
            squeeze("bodyLegs", middle(knee, ankle), hipWidth * .28f, abs(knee.y - ankle.y) * .65f, .12f)
    }
    return result
}
