package cn.liveroom.app

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Publishes the host camera and microphone to SRS over RTMP.
 * Owns the camera until [stop]. LiveKit must not enable its camera while this is running.
 */
class SrsIngestPublisher(context: Context, private var beautySettings: CameraBeautySettings = CameraBeautySettings()) {
    private val appContext = context.applicationContext
    private var connectResult: CancellableContinuation<Unit>? = null
    private var preview: OpenGlView? = null
    private var started = false
    private var stopped = false
    private val beautyTracker = BeautyFaceTracker(appContext, beautySettings)

    internal val beautyTrackingError: String? get() = beautyTracker.trackingError

    var onDropped: ((String) -> Unit)? = null

    private val checker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) = Unit
        override fun onConnectionSuccess() {
            takeConnect()?.let { pending -> if (pending.isActive) pending.resume(Unit) }
        }
        override fun onConnectionFailed(reason: String) {
            val message = reason.ifBlank { "SRS 推流失败" }
            val pending = takeConnect()
            if (pending != null && pending.isActive) pending.resumeWithException(IllegalStateException(message))
            else onDropped?.invoke(message)
        }
        override fun onNewBitrate(bitrate: Long) = Unit
        override fun onDisconnect() = Unit
        override fun onAuthError() { onConnectionFailed("SRS 拒绝了推流") }
        override fun onAuthSuccess() = Unit
    }

    private val camera = RtmpCamera2(appContext, checker)

    suspend fun start(url: String) = withContext(Dispatchers.Main.immediate) {
        check(!started && !stopped) { "推流已经结束" }
        started = true
        camera.streamClient.setReTries(0)
        if (!camera.prepareVideo() || !camera.prepareAudio()) {
            throw IllegalStateException("无法准备摄像头或麦克风")
        }
        preview?.let { camera.replaceView(it) }
        applyBeautyFilters()
        runCatching { camera.startPreview(CameraHelper.Facing.FRONT) }
        suspendCancellableCoroutine { continuation ->
            synchronized(this@SrsIngestPublisher) { connectResult = continuation }
            try {
                camera.startStream(url)
            } catch (error: Throwable) {
                val pending = takeConnect()
                if (pending != null && pending.isActive) pending.resumeWithException(error)
            }
        }
    }

    fun updateBeauty(settings: CameraBeautySettings) {
        if (stopped || beautySettings == settings) return
        beautySettings = settings
        beautyTracker.settings = settings
    }

    private fun applyBeautyFilters() {
        val gl = camera.glInterface
        gl.clearFilters()
        gl.setFilter(BeautyFilterRenderer(beautyTracker))
    }

    /** Camera-only preview. This never opens an audio source, a stream, or a room. */
    fun startLocalPreview(view: OpenGlView) {
        check(!stopped) { "预览已关闭" }
        if (started) return
        preview = view
        camera.replaceView(view)
        check(camera.prepareVideo(1280, 720, 30, 1800 * 1024, 90)) { "摄像头暂时无法预览，请退出后重试" }
        started = true
        applyBeautyFilters()
        camera.startPreview(CameraHelper.Facing.FRONT)
    }

    fun flipCamera() { if (started && !stopped) camera.switchCamera() }

    fun attachPreview(view: OpenGlView) {
        if (preview === view) return
        preview = view
        if (stopped) return
        runCatching { camera.replaceView(view); if (started) applyBeautyFilters() }
    }

    fun detachPreview(view: OpenGlView) {
        if (preview !== view) return
        preview = null
        if (!started || stopped) return
        runCatching { camera.replaceView(appContext); applyBeautyFilters() }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        takeConnect()?.let { pending -> if (pending.isActive) pending.cancel() }
        runCatching { if (camera.isStreaming) camera.stopStream() }
        runCatching { camera.stopPreview() }
        runCatching { camera.stopCamera() }
        beautyTracker.close()
    }

    @Synchronized
    private fun takeConnect(): CancellableContinuation<Unit>? {
        val pending = connectResult
        connectResult = null
        return pending
    }
}

@Composable
internal fun SrsHostPreview(publisher: SrsIngestPublisher, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { viewContext ->
            OpenGlView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                keepScreenOn = true
            }
        },
        update = { view -> publisher.attachPreview(view) },
        onRelease = { view -> publisher.detachPreview(view) },
        modifier = modifier,
    )
}
