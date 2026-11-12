package cn.liveroom.app

import android.graphics.PointF
import android.opengl.GLES20.*
import jp.co.cyberagent.android.gpuimage.filter.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** GPUImage draws into the current streaming GL context; no bitmap readback or extra camera. */
internal class BeautyGpuImagePass(private val width: Int, private val height: Int) {
    private val frameBuffer = IntArray(1)
    private val texture = IntArray(1)
    private var selectedKey: String? = null
    private var filter: GPUImageFilter? = null
    private fun buffer(values: FloatArray) = ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
    private val cube = buffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val uv = buffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

    fun draw(input: Int, key: String): Int {
        if (key == "original") return input
        if (frameBuffer[0] == 0) {
            glGenTextures(1, texture, 0); glBindTexture(GL_TEXTURE_2D, texture[0])
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
            glGenFramebuffers(1, frameBuffer, 0)
        }
        if (selectedKey != key) {
            filter?.destroy()
            filter = when (key) {
                "natural" -> GPUImageRGBFilter(1.04f, 1.01f, .98f)
                "gray" -> GPUImageSaturationFilter(.25f)
                "bright" -> GPUImageGammaFilter(.82f)
                "pink" -> GPUImageRGBFilter(1.08f, .98f, 1.04f)
                "cool" -> GPUImageWhiteBalanceFilter(3800f, 0f)
                "warm" -> GPUImageWhiteBalanceFilter(6200f, 0f)
                "film" -> GPUImageContrastFilter(1.12f)
                "sepia" -> GPUImageSepiaToneFilter(1f)
                "mono" -> GPUImageGrayscaleFilter()
                "vignette" -> GPUImageVignetteFilter(PointF(.5f, .5f), floatArrayOf(0f, 0f, 0f), .3f, .85f)
                else -> GPUImageFilter()
            }.apply { ifNeedInit(); onOutputSizeChanged(width, height) }
            selectedKey = key
        }
        val previous = IntArray(1); val viewport = IntArray(4)
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, previous, 0); glGetIntegerv(GL_VIEWPORT, viewport, 0)
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer[0])
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture[0], 0)
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { "滤镜画面缓冲初始化失败" }
            glViewport(0, 0, width, height)
            cube.position(0); uv.position(0)
            filter?.onDraw(input, cube, uv)
            return texture[0]
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, previous[0]); glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        }
    }

    fun release() {
        filter?.destroy(); filter = null
        glDeleteFramebuffers(1, frameBuffer, 0); glDeleteTextures(1, texture, 0)
        frameBuffer[0] = 0; texture[0] = 0
    }
}
