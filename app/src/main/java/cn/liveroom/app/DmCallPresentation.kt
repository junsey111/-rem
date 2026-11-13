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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal fun dmCallStatusText(status: String, connected: Boolean, duration: Long): String = when (status) {
    "ringing" -> "等待接通"
    "connected" -> "通话中 ${formatCallDuration(duration)}"
    "rejected" -> "已拒绝 · 未接通"
    "cancelled" -> "已取消 · 未接通"
    "no_answer" -> "无人接听 · 未接通"
    else -> if (connected) "通话时长 ${formatCallDuration(duration)}" else "未接通"
}

@Composable
internal fun DmCallReceipt(token: String, invite: DmCallInvite, ownUserId: String) {
    val context = LocalContext.current
    val foreground = rememberChatForeground()
    val prefs = remember(ownUserId) { dmReceiptPreferences(context, ownUserId) }
    var record by remember(invite.callId, ownUserId) { mutableStateOf(parseCallRecord(prefs.getString(invite.callId, "").orEmpty())) }
    DisposableEffect(prefs, invite.callId) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { storage, key ->
            if (key == invite.callId) record = parseCallRecord(storage.getString(key, "").orEmpty())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(invite.callId, ownUserId, foreground) {
        if (!foreground) return@LaunchedEffect
        while (true) {
            if (record?.terminal == true) return@LaunchedEffect
            try {
                val row = LivePlatformApi().dmCallStatus(token, invite.callId)
                record = CallRecord(invite.mode, row.optString("status"), row.optLong("duration_seconds"), row.optBoolean("connected"))
                if (record?.terminal == true) {
                    cacheDmCallReceipt(context, ownUserId, invite.callId, invite.mode, row)
                    return@LaunchedEffect
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* Keep the cached state and retry after a temporary connection loss. */ }
            delay(3_000)
        }
    }
    CallRecordContent(invite.mode, record?.label(invite.fromUserId.toString() == ownUserId) ?: "正在读取通话记录",
        own = invite.fromUserId.toString() == ownUserId)
}

@Composable
internal fun CallRecordContent(mode: String, label: String, own: Boolean) {
    val icon = if (mode == "video") Icons.Rounded.Videocam else Icons.Rounded.PhoneInTalk
    val description = if (mode == "video") "视频通话" else "语音通话"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!own) Icon(icon, description, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        if (own) Icon(icon, description, modifier = Modifier.size(24.dp))
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
