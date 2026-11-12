package cn.liveroom.app

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20.*
import android.opengl.GLUtils
import android.os.Bundle
import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Runs GPU/face-model checks on a device without opening a room or requiring a login/camera. */
class BeautyDeviceCheck : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val status = Bundle().apply { putString("class", javaClass.name); putString("test", "gpuBeautyAndFaceTracking"); putInt("numtests", 1); putInt("current", 1) }
        sendStatus(1, status)
        try {
            verify()
            sendStatus(0, status)
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "GPUImage filters, MediaPipe face/pose, body warps, original restoration, saved settings and call receipts: OK\n") })
        } catch (error: Throwable) {
            status.putString("stack", error.stackTraceToString())
            sendStatus(-2, status)
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", error.stackTraceToString()) })
        }
    }

    private fun verify() {
        verifyStoredPreferences()
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
        val configs = arrayOfNulls<EGLConfig>(1)
        val attributes = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, IntArray(1), 0))
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 256, EGL14.EGL_HEIGHT, 384, EGL14.EGL_NONE),0)
        check(EGL14.eglMakeCurrent(display,surface,surface,context))
        val tracker = BeautyFaceTracker(targetContext, CameraBeautySettings())
        val renderer = BeautyFilterRenderer(tracker)
        val texture = IntArray(1)
        val image = BitmapFactory.decodeResource(targetContext.resources, R.drawable.live_placeholder_1)
        val width = 256
        val height = (width.toFloat() * image.height / image.width).toInt()
        try {
            val small = Bitmap.createScaledBitmap(image,width,height,true)
            val flipped = Bitmap.createBitmap(small,0,0,width,height,Matrix().apply { postScale(1f,-1f) },false)
            glGenTextures(1,texture,0)
            glBindTexture(GL_TEXTURE_2D,texture[0])
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GL_TEXTURE_2D,0,flipped,0)
            renderer.initGl(width,height,targetContext,width,height)
            renderer.initFBOLink()
            renderer.setPreviousTexId(texture[0])
            fun draw(settings: CameraBeautySettings): ByteArray {
                tracker.settings = settings
                renderer.draw()
                glBindFramebuffer(GL_FRAMEBUFFER,renderer.renderHandler.fboId[0])
                val bytes = ByteBuffer.allocateDirect(width*height*4).order(ByteOrder.nativeOrder())
                glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,bytes)
                glBindFramebuffer(GL_FRAMEBUFFER,0)
                check(glGetError()==GL_NO_ERROR) { "Beauty GPU error" }
                return ByteArray(bytes.capacity()).also { bytes.rewind(); bytes.get(it) }
            }
            fun difference(a:ByteArray,b:ByteArray):Double = a.indices.sumOf { kotlin.math.abs((a[it].toInt() and 255)-(b[it].toInt() and 255)).toDouble() } / a.size
            val original = draw(CameraBeautySettings())
            val weak = draw(CameraBeautySettings(filter="cool",filterStrength=.2f))
            val strong = draw(CameraBeautySettings(filter="cool",filterStrength=1f))
            check(difference(original,strong)>difference(original,weak)+.5) { "Filter intensity has no effect" }
            val disabled=draw(CameraBeautySettings(enabled=false,filter="pink",amounts=mapOf("smooth" to 1f,"slim" to 1f)))
            check(difference(original,disabled)<.1) { "Original restoration differs" }
            for (option in beautyFilterOptions.filter { it.key!="original" }) {
                check(difference(original,draw(CameraBeautySettings(filter=option.key,filterStrength=1f)))>.1) { "No effect: ${option.key}" }
            }
            val shape = CameraBeautySettings(amounts=mapOf("slim" to 1f,"bigEyes" to 1f))
            val deadline=SystemClock.elapsedRealtime()+12_000
            while(tracker.face()==null && SystemClock.elapsedRealtime()<deadline) { draw(shape); Thread.sleep(150) }
            check(tracker.face()!=null) { "Bundled face model did not detect fixture portrait" }
            val shaped=draw(shape)
            check(difference(original,shaped)>.1) { "Face landmarks did not change output" }
            val folder=File(targetContext.cacheDir,"beauty-device-check").apply { mkdirs() }
            fun save(name:String,data:ByteArray) {
                val bottom=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                bottom.copyPixelsFromBuffer(ByteBuffer.wrap(data))
                val upright=Bitmap.createBitmap(bottom,0,0,width,height,Matrix().apply { postScale(1f,-1f) },false)
                File(folder,name).outputStream().use { upright.compress(Bitmap.CompressFormat.PNG,100,it) }
                upright.recycle(); if(!bottom.isRecycled)bottom.recycle()
            }
            save("original.png",original);save("filter.png",strong);save("shape.png",shaped)
            verifyPose()
            if(flipped!==small)flipped.recycle(); if(small!==image)small.recycle()
        } finally {
            image.recycle(); renderer.release(); tracker.close()
            glDeleteTextures(1,texture,0)
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,context); EGL14.eglTerminate(display)
        }
    }

    private fun verifyPose() {
        val source = context.assets.open("beauty_pose_fixture.jpg").use { BitmapFactory.decodeStream(it) }
        val image = Bitmap.createScaledBitmap(source, 384, (384f * source.height / source.width).toInt(), true)
        val flipped = Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postScale(1f, -1f) }, false)
        val tracker = BeautyFaceTracker(targetContext, CameraBeautySettings())
        val renderer = BeautyFilterRenderer(tracker)
        val texture = IntArray(1)
        try {
            glGenTextures(1, texture, 0); glBindTexture(GL_TEXTURE_2D, texture[0])
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GL_TEXTURE_2D, 0, flipped, 0)
            renderer.initGl(image.width, image.height, targetContext, image.width, image.height); renderer.initFBOLink(); renderer.setPreviousTexId(texture[0])
            fun draw(settings: CameraBeautySettings): ByteArray {
                tracker.settings = settings; renderer.draw()
                glBindFramebuffer(GL_FRAMEBUFFER, renderer.renderHandler.fboId[0])
                val buffer = ByteBuffer.allocateDirect(image.width * image.height * 4)
                glReadPixels(0, 0, image.width, image.height, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
                glBindFramebuffer(GL_FRAMEBUFFER, 0)
                check(glGetError() == GL_NO_ERROR)
                return ByteArray(buffer.capacity()).also { buffer.rewind(); buffer.get(it) }
            }
            val original = draw(CameraBeautySettings())
            val settings = CameraBeautySettings(amounts = beautyBodyOptions.associate { it.key to 1f })
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (tracker.body() == null && SystemClock.elapsedRealtime() < deadline) { draw(settings); Thread.sleep(200) }
            check(tracker.body() != null) { "Bundled MediaPipe pose model did not locate fixture: ${tracker.trackingError}" }
            check(beautyBodyWarps(settings, tracker.body()).isNotEmpty()) { "Measured pose did not generate body warps" }
            val shaped = draw(settings)
            check(original.indices.count { original[it] != shaped[it] } > original.size / 500) { "Body effects did not change output pixels" }
            val disabled = draw(settings.copy(enabled = false))
            check(original.contentEquals(disabled)) { "Disabling body shaping did not restore original" }
            val folder = File(targetContext.cacheDir, "beauty-device-check").apply { mkdirs() }
            for ((name, pixels) in listOf("body-original.png" to original, "body-shaped.png" to shaped)) {
                val bottom = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                bottom.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
                val upright = Bitmap.createBitmap(bottom, 0, 0, bottom.width, bottom.height, Matrix().apply { postScale(1f, -1f) }, false)
                File(folder, name).outputStream().use { upright.compress(Bitmap.CompressFormat.PNG, 100, it) }
                upright.recycle(); bottom.recycle()
            }
        } finally { renderer.release(); tracker.close(); glDeleteTextures(1, texture, 0); flipped.recycle(); image.recycle(); source.recycle() }
    }

    private fun verifyStoredPreferences() {
        val testUser = -7314783L
        val testPrefs = targetContext.getSharedPreferences("camera_beauty_$testUser", android.content.Context.MODE_PRIVATE)
        try {
            val settings = CameraBeautySettings(filter = "sepia", filterStrength = .8f,
                amounts = mapOf("slim" to .25f, "bodyWaist" to .65f, "bodyLegs" to .4f))
            CameraBeautyPreferences.save(targetContext, testUser, settings)
            check(CameraBeautyPreferences.load(targetContext, testUser) == settings) { "Body/filter parameters did not survive reopen" }
            CameraBeautyPreferences.save(targetContext, testUser, settings.reset(BeautySection.BODY))
            val restored = CameraBeautyPreferences.load(targetContext, testUser)
            check(!restored.needsBody && restored.amount("slim") == .25f && restored.filter == "sepia") { "Body reset changed another category" }
        } finally { testPrefs.edit().clear().commit() }
        val receiptUser = "beauty_device_check"
        val preferences = dmReceiptPreferences(targetContext, receiptUser)
        try {
            val ended = org.json.JSONObject().put("status", "ended").put("connected", true).put("duration_seconds", 15)
            cacheDmCallReceipt(targetContext, receiptUser, "check-video", "video", ended)
            check(parseCallRecord(preferences.getString("check-video", "").orEmpty())?.label(true) == "通话时长 00:15")
            val rejected = org.json.JSONObject().put("status", "rejected").put("connected", false).put("duration_seconds", 0)
            cacheDmCallReceipt(targetContext, receiptUser, "check-voice", "voice", rejected)
            val restored = parseCallRecord(dmReceiptPreferences(targetContext, receiptUser).getString("check-voice", "").orEmpty())
            check(restored?.mode == "voice" && restored.label(false) == "已拒绝") { "Call status did not survive reopen" }
        } finally { preferences.edit().clear().commit() }
    }
}
