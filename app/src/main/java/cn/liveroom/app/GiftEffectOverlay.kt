package cn.liveroom.app

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yy.yyeva.view.EvaAnimViewV3

/**
 * Fullscreen mixed-MP4 layer. The view stays non-interactive; [GiftStageLayer]
 * puts it in a window above the camera surface so the close control still works.
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

/**
 * Draws the fullscreen effect and the lane banner in a window above the live
 * SurfaceView, without taking touches away from chat or the close control.
 * Host preview uses a GL surface that punches through ordinary Compose siblings.
 */
@Composable
fun GiftStageLayer(
    session: GiftEffectSession,
    gifts: List<Gift>,
    selfId: Long,
    selfAvatar: String?,
    resolveAvatar: suspend (Long) -> String?,
) {
    val banner by session.banner.collectAsState()
    Popup(
        alignment = Alignment.TopStart,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        val view = LocalView.current
        SideEffect { markGiftPopupPassThrough(view) }
        Box(Modifier.fillMaxSize()) {
            GiftEffectOverlay(session, Modifier.fillMaxSize())
            val item = banner
            val gift = item?.let { play -> gifts.firstOrNull { it.id == play.giftId } }
            if (item != null && gift == null) {
                LaunchedEffect(item.requestId) { session.finishBanner(item.requestId) }
            }
            if (item != null && gift != null) {
                var lookedUp by remember(item.requestId) { mutableStateOf<String?>(null) }
                LaunchedEffect(item.requestId) {
                    if (item.senderId == selfId) return@LaunchedEffect
                    lookedUp = runCatching { resolveAvatar(item.senderId) }.getOrNull()
                }
                key(item.requestId) {
                    QumiGiftCelebration(
                        gift = gift,
                        nonce = item.requestId.hashCode(),
                        demo = false,
                        senderName = item.senderName,
                        senderAvatar = if (item.senderId == selfId) selfAvatar else lookedUp,
                        quantity = item.quantity,
                        onFinished = { session.finishBanner(item.requestId) },
                    )
                }
            }
        }
    }
}

private fun markGiftPopupPassThrough(view: View) {
    view.post {
        var current: View? = view
        while (current != null) {
            val params = current.layoutParams
            if (params is WindowManager.LayoutParams) {
                val flags = params.flags or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                if (flags != params.flags) {
                    params.flags = flags
                    val windowManager = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    runCatching { windowManager.updateViewLayout(current, params) }
                }
                return@post
            }
            current = current.parent as? View
        }
    }
}
