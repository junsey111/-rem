package cn.liveroom.app

import android.content.Context
import android.graphics.Matrix
import android.opengl.GLES20.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import livekit.org.webrtc.*
import java.util.concurrent.atomic.AtomicBoolean

/** Applies the same upright GPU shader to LiveKit camera frames, including multi-host/PK. */
internal class BeautyVideoProcessor(private val context: Context, private val shared: EglBase.Context,
    settings: CameraBeautySettings) : VideoProcessor {
    private val thread = HandlerThread("LiveBeauty").apply { start() }
    private val handler = Handler(thread.looper)
    private val tracker = BeautyFaceTracker(context, settings)
    private val processing = AtomicBoolean(false)
    @Volatile private var sink: VideoSink? = null
    @Volatile private var closed = false
    private var egl: EglBase? = null
    private var input: GlTextureFrameBuffer? = null
    private var drawer: GlRectDrawer? = null
    private var frameDrawer: VideoFrameDrawer? = null
    private var converter: YuvConverter? = null
    private var filter: BeautyFilterRenderer? = null
    private var width = 0
    private var height = 0

    fun update(settings: CameraBeautySettings) { tracker.settings = settings }
    override fun setSink(sink: VideoSink?) { this.sink = sink }
    override fun onCapturerStarted(success: Boolean) = Unit
    override fun onCapturerStopped() = Unit

    override fun onFrameCaptured(frame: VideoFrame) {
        if (closed) return
        if (!tracker.settings.hasEffects) { sink?.onFrame(frame); return }
        if (!processing.compareAndSet(false, true)) return
        frame.retain()
        if (!handler.post {
            try {
                if (closed) return@post
                if (egl == null) {
                    egl = EglBase.create(shared, EglBase.CONFIG_PIXEL_BUFFER).apply { createDummyPbufferSurface(); makeCurrent() }
                    input = GlTextureFrameBuffer(GL_RGBA)
                    drawer = GlRectDrawer()
                    frameDrawer = VideoFrameDrawer()
                    converter = YuvConverter()
                }
                egl?.makeCurrent()
                val w = frame.rotatedWidth
                val h = frame.rotatedHeight
                if (w != width || h != height) {
                    releaseFilter()
                    width = w; height = h
                    input?.setSize(w, h)
                    filter = BeautyFilterRenderer(tracker).apply { initGl(w, h, context, w, h); initFBOLink() }
                }
                val target = input ?: return@post
                glBindFramebuffer(GL_FRAMEBUFFER, target.frameBufferId)
                glViewport(0, 0, w, h)
                frameDrawer?.drawFrame(frame, drawer, null, 0, 0, w, h)
                val beauty = filter ?: return@post
                beauty.setPreviousTexId(target.textureId)
                beauty.draw()
                // Materialize an owned buffer before the next frame reuses the GPU target.
                val texture = TextureBufferImpl(w, h, VideoFrame.TextureBuffer.Type.RGB, beauty.texId,
                    Matrix(), handler, converter, Runnable {})
                try {
                    val pixels = texture.toI420() ?: return@post
                    val output = VideoFrame(pixels, 0, frame.timestampNs)
                    try { sink?.onFrame(output) } finally { output.release() }
                } finally { texture.release() }
            } catch (error: Exception) {
                Log.w("LiveBeauty", "Beauty processing failed", error)
                sink?.onFrame(frame)
            } finally { frame.release(); processing.set(false) }
        }) { frame.release(); processing.set(false) }
    }

    private fun releaseFilter() {
        filter?.let {
            it.release()
            val target = it.renderHandler
            glDeleteFramebuffers(1, target.fboId, 0)
            glDeleteRenderbuffers(1, target.rboId, 0)
            glDeleteTextures(1, target.texId, 0)
        }
        filter = null
    }

    fun close(onClosed: () -> Unit = {}) {
        closed = true
        sink = null
        handler.post {
            egl?.makeCurrent()
            releaseFilter()
            input?.release(); frameDrawer?.release(); drawer?.release(); converter?.release()
            tracker.close()
            egl?.release()
            onClosed()
            thread.quitSafely()
        }
    }
}
