package cn.liveroom.app

import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal data class DemoActiveCall(val chat: DemoChatState, val mode: String, val price: Int?)
internal class DemoCallSession {
    var active by mutableStateOf<DemoActiveCall?>(null)
    fun start(chat: DemoChatState, mode: String, price: Int? = null) {
        if (active == null) active = DemoActiveCall(chat, mode, price)
    }
}

@Composable
internal fun DemoCallOverlay(controller: DemoCallSession, ownName: String, ownAvatar: String?) {
    val active = controller.active ?: return
    key(active) {
        val context = LocalContext.current
        var connected by remember { mutableStateOf(false) }
        var duration by remember { mutableIntStateOf(0) }
        var mic by remember { mutableStateOf(true) }
        var speaker by remember { mutableStateOf(true) }
        var camera by remember { mutableStateOf(true) }
        var frontCamera by remember { mutableStateOf(true) }
        var gift by remember { mutableStateOf<Gift?>(null) }
        var nonce by remember { mutableIntStateOf(0) }
        var gifts by remember { mutableStateOf(demoGiftCatalog) }
        LaunchedEffect(Unit) { runCatching { LivePlatformApi().giftCatalog() }.onSuccess { if (it.isNotEmpty()) gifts = it } }
        LaunchedEffect(active) {
            delay(2500); connected = true
            val started = SystemClock.elapsedRealtime()
            while (true) { duration = ((SystemClock.elapsedRealtime() - started) / 1000).toInt(); delay(500) }
        }
        DirectCallSurface(active.chat.name, active.chat.avatar, active.mode == "video", connected, duration,
            "正在呼叫，等待对方接听…", mic, speaker, camera, true,
            onMic = { mic = !mic }, onSpeaker = { speaker = !speaker }, onCamera = { camera = !camera },
            onFlip = { frontCamera = !frontCamera }, onHangup = {
                val record = CallRecord(active.mode, if (connected) "ended" else "cancelled", duration.toLong(), connected)
                active.chat.append(true, "[${if (active.mode == "video") "视频通话" else "语音通话"}] ${record.label(true)}", "call", record.encode())
                controller.active = null
            }, gifts = gifts, balance = null, giftBusy = false, demo = true,
            onGift = { active.chat.append(true, it.name, "gift", it.id); gift = it; nonce++ },
            celebration = gift, celebrationNonce = nonce, ownName = ownName, ownAvatar = ownAvatar,
            onCelebrationFinished = { gift = null }, remoteVideo = {
                DemoChatVideo(Uri.parse("android.resource://${context.packageName}/${when (active.chat.peer?.publicId) {
                    648036L -> R.raw.qumi_demo_live_48036
                    647971L -> R.raw.qumi_demo_live_47971
                    641181L -> R.raw.qumi_demo_live_41181
                    else -> R.raw.qumi_demo_live_51287
                }}"), controls = false)
            }, localVideo = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (camera) QumiUserAvatar(ownAvatar, ownName, Modifier.size(72.dp))
                    Text(if (!camera) "摄像头已关" else if (frontCamera) "我 · 前置演示" else "我 · 后置演示",
                        color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp))
                }
            })
    }
}
