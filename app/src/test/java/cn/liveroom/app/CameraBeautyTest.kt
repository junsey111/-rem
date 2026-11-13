package cn.liveroom.app

import org.junit.Assert.*
import org.junit.Test

class CameraBeautyTest {
    private val face = BeautyFaceGeometry(BeautyPoint(.5f, .5f), .4f, .6f,
        BeautyPoint(.43f, .58f), BeautyPoint(.57f, .58f), BeautyPoint(.5f, .48f),
        BeautyPoint(.5f, .4f), BeautyPoint(.45f, .4f), BeautyPoint(.55f, .4f))

    @Test fun disabledBeautyAndMissingFaceNeverWarpTheBackground() {
        val settings = CameraBeautySettings(amounts = mapOf("slim" to .8f, "bigEyes" to .7f))
        assertTrue(settings.needsFace)
        assertTrue(beautyWarps(settings, null).isEmpty())
        assertTrue(beautyWarps(settings.copy(enabled = false), face).isEmpty())
        assertFalse(settings.copy(enabled = false).hasEffects)
        assertFalse(CameraBeautySettings().hasEffects)
    }

    @Test fun faceEffectsUseMeasuredLandmarksAndStayWithinUniformCapacity() {
        val eye = beautyWarps(CameraBeautySettings(amounts = mapOf("bigEyes" to 1f)), face)
        assertEquals(setOf(face.leftEye, face.rightEye), eye.map { it.center }.toSet())
        assertTrue(eye.all { it.scaleX < 0f && it.scaleY < 0f })
        val all = beautyWarps(CameraBeautySettings(amounts = beautyShapeOptions.associate { it.key to 1f }), face)
        assertTrue(all.size <= 40)
        assertTrue(all.all { it.radiusX > 0f && it.radiusY > 0f && it.moveX.isFinite() && it.moveY.isFinite() })
    }

    @Test fun restoringOneSectionKeepsOtherSavedEffects() {
        val settings = CameraBeautySettings(smoothing = true, brightness = .1f, filter = "pink",
            amounts = mapOf("slim" to .3f, "smooth" to .4f))
        val skinReset = settings.reset(BeautySection.SKIN)
        assertEquals(0f, skinReset.amount("smooth"))
        assertEquals(.3f, skinReset.amount("slim"))
        assertEquals("pink", skinReset.filter)
        assertEquals(0f, settings.reset(BeautySection.SHAPE).amount("slim"))
        assertEquals("original", settings.reset(BeautySection.FILTER).filter)
        assertEquals(.4f, settings.reset(BeautySection.FILTER).amount("smooth"))
    }

    @Test fun legacySettingsMigrateAndInvalidNumbersCannotReachTheShader() {
        val settings = CameraBeautySettings(smoothing = true, brightness = .15f,
            amounts = mapOf("slim" to Float.NaN, "bigEyes" to 3f, "nose" to -2f))
        assertEquals(.4f, settings.amount("smooth"))
        assertEquals(.5f, settings.amount("whiten"))
        assertEquals(0f, settings.amount("slim"))
        assertEquals(1f, settings.amount("bigEyes"))
        assertEquals(0f, settings.amount("nose"))
    }

    @Test fun bodyEffectsNeedAValidUprightPoseAndResetIndependently() {
        val body = BeautyBodyGeometry(BeautyPoint(.3f, .85f), BeautyPoint(.7f, .85f),
            BeautyPoint(.36f, .5f), BeautyPoint(.64f, .5f),
            BeautyPoint(.36f, .28f), BeautyPoint(.64f, .28f), BeautyPoint(.36f, .05f), BeautyPoint(.64f, .05f))
        val settings = CameraBeautySettings(amounts = beautyBodyOptions.associate { it.key to 1f } + ("slim" to .4f))
        assertTrue(settings.needsBody)
        assertTrue(settings.hasEffects)
        assertEquals(7, beautyBodyWarps(settings, body).size)
        assertTrue(beautyBodyWarps(settings, null).isEmpty())
        assertTrue(beautyBodyWarps(settings.copy(enabled = false), body).isEmpty())
        assertTrue(beautyBodyWarps(settings, body.copy(leftHip = body.leftShoulder, rightHip = body.rightShoulder)).isEmpty())
        assertFalse(settings.reset(BeautySection.BODY).needsBody)
        assertEquals(.4f, settings.reset(BeautySection.BODY).amount("slim"))
        assertTrue(settings.reset(BeautySection.SHAPE).needsBody)
        assertEquals(3, beautyBodyWarps(settings, body.copy(leftKnee = null, rightKnee = null)).size)
    }
}
