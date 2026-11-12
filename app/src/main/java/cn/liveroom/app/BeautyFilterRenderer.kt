package cn.liveroom.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES20.*
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Shared GPUImage and landmark-warp pipeline for pre-live preview and encoded RTMP frames. */
internal class BeautyFilterRenderer(private val tracker: BeautyFaceTracker) : BaseFilterRender() {
    private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
    }
    private var program = 0
    private var captureProgram = 0
    private var frameBuffer = IntArray(1)
    private var captureTexture = IntArray(1)
    private var captureWidth = 256
    private var captureHeight = 256
    private var readBuffer: ByteBuffer? = null
    private val locations = mutableMapOf<String, Int>()
    private var positionAttribute = 0
    private var uvAttribute = 0
    private var gpuImage: BeautyGpuImagePass? = null

    override fun initGlFilter(context: Context) {
        val vertex = "attribute vec2 aPosition; attribute vec2 aUv; varying vec2 vUv; void main(){ gl_Position=vec4(aPosition,0.0,1.0); vUv=aUv; }"
        program = GlUtil.createProgram(vertex, context.resources.openRawResource(R.raw.camera_beauty_fragment).bufferedReader().use { it.readText() })
        captureProgram = GlUtil.createProgram(vertex, "precision mediump float; varying vec2 vUv; uniform sampler2D uImage; void main(){ gl_FragColor=texture2D(uImage,vUv); }")
        positionAttribute = glGetAttribLocation(program, "aPosition")
        uvAttribute = glGetAttribLocation(program, "aUv")
        captureHeight = (captureWidth.toFloat() * height / width).toInt().coerceIn(128, 480)
        glGenTextures(1, captureTexture, 0)
        glBindTexture(GL_TEXTURE_2D, captureTexture[0])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, captureWidth, captureHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
        glGenFramebuffers(1, frameBuffer, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer[0])
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, captureTexture[0], 0)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        readBuffer = ByteBuffer.allocateDirect(captureWidth * captureHeight * 4).order(ByteOrder.nativeOrder())
        gpuImage = BeautyGpuImagePass(width, height)
    }

    private fun location(name: String) = locations.getOrPut(name) { glGetUniformLocation(program, name) }
    private fun one(name: String, value: Float) = glUniform1f(location(name), value)
    private fun four(name: String, a: Float, b: Float, c: Float, d: Float) = glUniform4f(location(name), a, b, c, d)
    private fun bindVertices(position: Int, uv: Int) {
        vertices.position(0)
        glVertexAttribPointer(position, 2, GL_FLOAT, false, 16, vertices)
        glEnableVertexAttribArray(position)
        vertices.position(2)
        glVertexAttribPointer(uv, 2, GL_FLOAT, false, 16, vertices)
        glEnableVertexAttribArray(uv)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, previousTexId)
    }

    private fun captureFace() {
        if (!tracker.wantsFrame()) return
        val current = IntArray(1)
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, current, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer[0])
        glViewport(0, 0, captureWidth, captureHeight)
        glUseProgram(captureProgram)
        val p = glGetAttribLocation(captureProgram, "aPosition")
        val uv = glGetAttribLocation(captureProgram, "aUv")
        bindVertices(p, uv)
        glUniform1i(glGetUniformLocation(captureProgram, "uImage"), 0)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        val buffer = readBuffer ?: return
        buffer.rewind()
        glReadPixels(0, 0, captureWidth, captureHeight, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
        buffer.rewind()
        val bottomUp = Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888)
        bottomUp.copyPixelsFromBuffer(buffer)
        val upright = Bitmap.createBitmap(bottomUp, 0, 0, captureWidth, captureHeight, Matrix().apply { postScale(1f, -1f) }, false)
        if (upright !== bottomUp) bottomUp.recycle()
        tracker.submit(upright)
        glDisableVertexAttribArray(p)
        glDisableVertexAttribArray(uv)
        glBindFramebuffer(GL_FRAMEBUFFER, current[0])
        glViewport(0, 0, width, height)
    }

    override fun drawFilter() {
        captureFace()
        val settings = tracker.settings
        val filteredTexture = if (settings.enabled && settings.filterStrength > 0f)
            gpuImage?.draw(previousTexId, settings.filter) ?: previousTexId else previousTexId
        glUseProgram(program)
        bindVertices(positionAttribute, uvAttribute)
        glUniform1i(location("uImage"), 0)
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, filteredTexture)
        glUniform1i(location("uFilteredImage"), 1)
        glActiveTexture(GL_TEXTURE0)
        glUniform2f(location("uPixel"), 1f / width, 1f / height)
        one("uEnabled", if (settings.hasEffects) 1f else 0f)
        four("uSkin", settings.amount("smooth"), settings.amount("blemish"), settings.amount("whiten"), settings.amount("rosy"))
        four("uDetail", settings.amount("clarity"), settings.amount("sharpen"), settings.brightness.coerceAtMost(0f), settings.saturation)
        four("uLocal", settings.amount("brightEyes"), settings.amount("whiteTeeth"), settings.amount("darkCircles"), settings.amount("smileLines"))
        one("uQuality", if (settings.highPerformance) 0f else 1f)
        val face = tracker.face()
        one("uHasFace", if (face == null) 0f else 1f)
        if (face != null) {
            four("uFace", face.center.x, face.center.y, face.width, face.height)
            four("uEyes", face.leftEye.x, face.leftEye.y, face.rightEye.x, face.rightEye.y)
            four("uMouth", face.mouth.x, face.mouth.y, 0f, 0f)
        }
        val warps = (beautyWarps(settings, face) + beautyBodyWarps(settings, tracker.body())).take(40)
        glUniform1i(location("uWarpCount"), warps.size)
        if (warps.isNotEmpty()) {
            val regions = FloatArray(warps.size * 4)
            val values = FloatArray(warps.size * 4)
            warps.forEachIndexed { index, warp ->
                val i = index * 4
                regions[i] = warp.center.x; regions[i + 1] = warp.center.y
                regions[i + 2] = warp.radiusX; regions[i + 3] = warp.radiusY
                values[i] = warp.moveX; values[i + 1] = warp.moveY
                values[i + 2] = warp.scaleX; values[i + 3] = warp.scaleY
            }
            glUniform4fv(location("uRegion[0]"), warps.size, regions, 0)
            glUniform4fv(location("uWarp[0]"), warps.size, values, 0)
        }
        one("uFilterStrength", if (settings.filter == "original") 0f else settings.filterStrength.coerceIn(0f, 1f))
    }

    override fun disableResources() {
        glDisableVertexAttribArray(positionAttribute)
        glDisableVertexAttribArray(uvAttribute)
    }
    override fun release() {
        gpuImage?.release(); gpuImage = null
        glDeleteProgram(program); glDeleteProgram(captureProgram)
        glDeleteFramebuffers(1, frameBuffer, 0); glDeleteTextures(1, captureTexture, 0)
        readBuffer = null
    }
}
