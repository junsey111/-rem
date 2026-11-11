package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal fun dmCallStatusText(status: String, connected: Boolean, duration: Long): String = when (status) {
    "ringing" -> "等待接通"
    "connected" -> "通话中 ${duration / 60}:${(duration % 60).toString().padStart(2, '0')}"
    "rejected" -> "已拒绝 · 未接通"
    "cancelled" -> "已取消 · 未接通"
    "no_answer" -> "无人接听 · 未接通"
    else -> if (connected) "通话时长 ${duration / 60}:${(duration % 60).toString().padStart(2, '0')}" else "未接通"
}

@Composable
internal fun DmCallReceipt(token: String, invite: DmCallInvite) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dm_call_receipts", android.content.Context.MODE_PRIVATE) }
    var label by remember(invite.callId) { mutableStateOf(prefs.getString(invite.callId, null) ?: "通话记录") }
    LaunchedEffect(invite.callId) {
        repeat(22) {
            try {
                val row = LivePlatformApi().dmCallStatus(token, invite.callId)
                label = dmCallStatusText(row.optString("status"), row.optBoolean("connected"), row.optLong("duration_seconds"))
                if (row.optString("status") !in listOf("ringing", "connected")) {
                    prefs.edit().putString(invite.callId, label).apply(); return@LaunchedEffect
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { return@LaunchedEffect }
            delay(3_000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (invite.mode == "video") Icons.Rounded.Videocam else Icons.Rounded.PhoneInTalk, null, modifier = Modifier.size(25.dp))
        Column {
            Text(if (invite.mode == "video") "视频通话" else "语音通话", fontWeight = FontWeight.Medium)
            Text(label, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun DmIncomingCallPrompt(name: String, avatar: String?, mode: String, busy: Boolean, onAccept: () -> Unit, onReject: () -> Unit) {
    Dialog(onDismissRequest = onReject, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF5D378F), Color(0xFF171024)))).navigationBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            ChatOnlineAvatar(name, avatar, false, Modifier.size(110.dp))
            Text(name, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp))
            Text("邀请你进行${if (mode == "video") "视频" else "语音"}通话…", color = Color(0xFFD9CDE9), fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalArrangement = Arrangement.SpaceAround) {
                CallRoundAction("拒绝", Color(0xFFE34D6C), true, onReject)
                CallRoundAction(if (busy) "处理中" else "接听", Color(0xFF39BE85), false, onAccept, enabled = !busy, video = mode == "video")
            }
        }
    }
}

@Composable
internal fun CallRoundAction(label: String, background: Color, hangup: Boolean, onClick: () -> Unit, enabled: Boolean = true, video: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledIconButton(onClick = onClick, enabled = enabled, shape = CircleShape, modifier = Modifier.size(66.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = background, contentColor = Color.White)) {
            Icon(if (hangup) Icons.Rounded.CallEnd else if (video) Icons.Rounded.Videocam else Icons.Rounded.PhoneInTalk, label, modifier = Modifier.size(30.dp))
        }
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}
