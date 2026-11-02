package cn.liveroom.app

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.yy.yyeva.view.EvaAnimViewV3

/**
 * Fullscreen mixed-MP4 layer. TextureView stays in the Compose hierarchy so the
 * safe-area close control, drawn above this view, can still leave the room.
 */
@Composable
fun GiftEffectOverlay(session: GiftEffectSession, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            EvaAnimViewV3(context).apply {
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setOnTouchListener { _, _ -> false }
                session.attach(this)
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) { session.onSurfaceReady() }
                    override fun onViewDetachedFromWindow(v: View) = Unit
                })
            }
        },
        update = { view ->
            session.attach(view)
            if (view.isAttachedToWindow) session.onSurfaceReady()
        },
        modifier = modifier,
        onRelease = { view ->
            view.setOnTouchListener(null)
            session.detach(view)
        },
    )
}
