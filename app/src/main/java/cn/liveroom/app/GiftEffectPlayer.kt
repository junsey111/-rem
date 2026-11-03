package cn.liveroom.app

import android.util.Log
import android.view.View
import com.yy.yyeva.inter.IEvaAnimListener
import com.yy.yyeva.util.EvaConstant
import com.yy.yyeva.util.ScaleType
import com.yy.yyeva.view.EvaAnimViewV3
import java.io.File

/**
 * One TextureView YYEVA surface. Completion is success only when the player reports
 * onVideoComplete without onFailed. File errors in this library emit both, so failure wins.
 */
class YyevaGiftEffectPlayer {
    private var view: EvaAnimViewV3? = null
    private var playToken = 0
    private var failed = false
    private var scheduled = false
    private var callback: ((Boolean) -> Unit)? = null

    fun attach(surface: EvaAnimViewV3) {
        surface.setScaleType(ScaleType.CENTER_CROP)
        surface.setLoop(false)
        surface.setLoop(1)
        surface.setLastFrame(false)
        surface.isClickable = false
        surface.isFocusable = false
        view = surface
    }

    fun detach(surface: EvaAnimViewV3) {
        if (view === surface) {
            stop()
            view = null
        }
    }

    /** False when the TextureView is not attached yet. The callback then is not used. */
    fun play(file: File, videoMode: Int?, onFinished: (Boolean) -> Unit): Boolean {
        val surface = view ?: return false
        if (!surface.isAttachedToWindow) return false
        stop()
        failed = false
        scheduled = false
        callback = onFinished
        val token = playToken
        surface.visibility = View.VISIBLE
        surface.setLoop(false)
        surface.setLoop(1)
        surface.setLastFrame(false)
        surface.setNormalMp4(false)
        surface.setVideoMode(EvaConstant.VIDEO_MODE_NORMAL_MP4)
        videoMode?.let { surface.setVideoMode(it) }
        surface.setAnimListener(object : IEvaAnimListener {
            override fun onVideoStart(isRestart: Boolean) = Unit
            override fun onVideoRestart() = Unit
            override fun onVideoRender(frameIndex: Int, config: com.yy.yyeva.EvaAnimConfig?) = Unit
            override fun onVideoComplete(lastFrame: Boolean) = schedule(token)
            override fun onVideoDestroy() = Unit
            override fun onFailed(errorType: Int, errorMsg: String?) {
                Log.w(TAG, "YYEVA failed type=$errorType $errorMsg")
                failed = true
                schedule(token)
            }
        })
        if (surface.isRunning()) {
            Log.w(TAG, "YYEVA was still running; refusing to overlap a second play")
            dispatch(token)
            return true
        }
        surface.startPlay(file)
        return true
    }

    fun stop() {
        val pending = callback
        callback = null
        playToken++
        view?.stopPlay(null)
        pending?.invoke(false)
    }

    private fun schedule(token: Int) {
        if (token != playToken || scheduled) return
        scheduled = true
        val surface = view
        if (surface == null) {
            dispatch(token)
        } else {
            surface.post { dispatch(token) }
        }
    }

    private fun dispatch(token: Int) {
        if (token != playToken) return
        val pending = callback ?: return
        callback = null
        playToken++
        val success = !failed
        failed = false
        scheduled = false
        if (!success) Log.w(TAG, "fullscreen gift effect ended in failure")
        pending.invoke(success)
    }

    private companion object {
        const val TAG = "GiftEffect"
    }
}
