package cn.liveroom.app

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Bundled MediaPipe models run on a worker; camera frames stay on this device. */
internal class BeautyFaceTracker(context: Context, initial: CameraBeautySettings) {
    private val appContext = context.applicationContext
    @Volatile var settings = initial
    @Volatile private var geometry: BeautyFaceGeometry? = null
    @Volatile private var bodyGeometry: BeautyBodyGeometry? = null
    @Volatile private var faceAt = 0L
    @Volatile private var bodyAt = 0L
    @Volatile var closed = false
        private set
    @Volatile var trackingError: String? = null
        private set
    private val busy = AtomicBoolean(false)
    private var requestedAt = 0L
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "BeautyLandmarks") }
    private var faceDetector: FaceLandmarker? = null
    private var poseDetector: PoseLandmarker? = null

    fun face(): BeautyFaceGeometry? = geometry.takeIf { !closed && settings.needsFace && SystemClock.uptimeMillis() - faceAt < 800L }
    fun body(): BeautyBodyGeometry? = bodyGeometry.takeIf { !closed && settings.needsBody && SystemClock.uptimeMillis() - bodyAt < 800L }

    @Synchronized fun wantsFrame(): Boolean {
        if (closed || !(settings.needsFace || settings.needsBody) || busy.get()) return false
        val now = SystemClock.uptimeMillis()
        if (now - requestedAt < if (settings.highPerformance) 240L else 120L) return false
        requestedAt = now
        return true
    }

    fun submit(bitmap: Bitmap) {
        if (closed || !busy.compareAndSet(false, true)) { bitmap.recycle(); return }
        val capturedAt = SystemClock.uptimeMillis()
        val requested = settings
        try {
            worker.execute {
                try {
                    if (closed) return@execute
                    val image = BitmapImageBuilder(bitmap).build()
                    try {
                        if (requested.needsFace) {
                            val detector = faceDetector ?: FaceLandmarker.createFromOptions(appContext,
                                FaceLandmarker.FaceLandmarkerOptions.builder()
                                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("beauty/face_landmarker.task").setDelegate(Delegate.CPU).build())
                                    .setRunningMode(RunningMode.VIDEO).setNumFaces(1)
                                    .setMinFaceDetectionConfidence(.55f).setMinFacePresenceConfidence(.55f)
                                    .setMinTrackingConfidence(.55f).build()).also { faceDetector = it }
                            val points = detector.detectForVideo(image, capturedAt).faceLandmarks().firstOrNull()
                            geometry = points?.let(::measuredFace)
                            faceAt = capturedAt
                        } else geometry = null
                        if (requested.needsBody) {
                            val detector = poseDetector ?: PoseLandmarker.createFromOptions(appContext,
                                PoseLandmarker.PoseLandmarkerOptions.builder()
                                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("beauty/pose_landmarker_lite.task").setDelegate(Delegate.CPU).build())
                                    .setRunningMode(RunningMode.VIDEO).setNumPoses(1)
                                    .setMinPoseDetectionConfidence(.6f).setMinPosePresenceConfidence(.6f)
                                    .setMinTrackingConfidence(.6f).build()).also { poseDetector = it }
                            bodyGeometry = detector.detectForVideo(image, capturedAt).landmarks().firstOrNull()?.let(::measuredBody)
                            bodyAt = capturedAt
                        } else bodyGeometry = null
                        trackingError = null
                    } finally { image.close() }
                } catch (error: Exception) {
                    geometry = null; bodyGeometry = null
                    trackingError = "识别暂不可用，请退出后重试"
                    Log.w("LiveBeauty", "MediaPipe landmark detection failed", error)
                } finally { bitmap.recycle(); busy.set(false) }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { bitmap.recycle(); busy.set(false) }
    }

    @Synchronized fun close() {
        if (closed) return
        closed = true; geometry = null; bodyGeometry = null
        worker.execute { faceDetector?.close(); poseDetector?.close(); faceDetector = null; poseDetector = null }
        worker.shutdown()
    }
}

private fun List<NormalizedLandmark>.point(index: Int): BeautyPoint? = getOrNull(index)?.let {
    if (!it.x().isFinite() || !it.y().isFinite() || it.x() !in 0f..1f || it.y() !in 0f..1f) null
    else BeautyPoint(it.x(), 1f - it.y())
}

private fun measuredFace(points: List<NormalizedLandmark>): BeautyFaceGeometry? {
    if (points.size < 468) return null
    val chin = points.point(152) ?: return null
    val forehead = points.point(10) ?: return null
    val leftSide = points.point(234) ?: return null
    val rightSide = points.point(454) ?: return null
    fun middle(a: Int, b: Int): BeautyPoint? {
        val first = points.point(a) ?: return null; val second = points.point(b) ?: return null
        return BeautyPoint((first.x + second.x) / 2, (first.y + second.y) / 2)
    }
    val eyes = listOf(middle(33, 133) ?: return null, middle(362, 263) ?: return null).sortedBy { it.x }
    val mouth = listOf(points.point(61) ?: return null, points.point(291) ?: return null).sortedBy { it.x }
    val nose = points.point(1) ?: return null
    val w = abs(rightSide.x - leftSide.x); val h = forehead.y - chin.y
    if (w < .04f || h < .06f || eyes[1].x - eyes[0].x < w * .18f || abs(eyes[0].y - eyes[1].y) > h * .2f) return null
    return BeautyFaceGeometry(BeautyPoint((leftSide.x + rightSide.x) / 2, (chin.y + forehead.y) / 2),
        w, h, eyes[0], eyes[1], nose, middle(61, 291) ?: return null, mouth[0], mouth[1], chin, forehead)
}

private fun measuredBody(points: List<NormalizedLandmark>): BeautyBodyGeometry? {
    fun visible(index: Int): BeautyPoint? {
        val landmark = points.getOrNull(index) ?: return null
        if (landmark.visibility().orElse(0f) < .65f || landmark.presence().orElse(0f) < .65f) return null
        return points.point(index)
    }
    return BeautyBodyGeometry(visible(11) ?: return null, visible(12) ?: return null,
        visible(23) ?: return null, visible(24) ?: return null,
        visible(25), visible(26), visible(27), visible(28))
}
