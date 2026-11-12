package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun LiveRoomAnnouncementDialog(hostName: String, announcement: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(24.dp)
            Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().heightIn(max = maxHeight)
                .shadow(16.dp, shape).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFFF1E9FF), Color(0xFFFCFAFF), Color.White)))
                .border(1.dp, Color(0xFFE7DAFA), shape).padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8861E9), LiveStyle.accent))),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Campaign, null, Modifier.size(25.dp), tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                        Text("房间公告", color = LiveStyle.ink, fontSize = 19.sp, lineHeight = 25.sp,
                            fontWeight = FontWeight.Bold)
                        Text("${hostName}的直播间", color = LiveStyle.muted, fontSize = 12.sp,
                            lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "关闭房间公告", Modifier.size(19.dp), tint = Color(0xFF8C7C9F))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(announcement.trim().ifBlank { "欢迎来到直播间，一起聊聊天吧。" },
                        color = LiveStyle.ink, fontSize = 15.sp, lineHeight = 24.sp)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(LiveStyle.accentSoft.copy(alpha = .7f)).padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("温馨提示", color = LiveStyle.accent, fontSize = 12.sp,
                            lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(LIVE_ENTRY_NOTICE, color = Color(0xFF81738F), fontSize = 12.sp, lineHeight = 20.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LiveStyle.accent, contentColor = Color.White)) {
                    Text("知道了", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
