package cn.liveroom.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DirectChatLayout(
    name: String, avatar: String?, online: Boolean, demo: Boolean = false,
    subtitle: String? = null, draft: String, sending: Boolean = false, enabled: Boolean = true,
    onDraft: (String) -> Unit, onBack: () -> Unit, onSend: () -> Unit,
    onVoice: () -> Unit, onVideo: () -> Unit, onGift: () -> Unit, onEmoji: () -> Unit, onMore: () -> Unit,
    onOpenProfile: () -> Unit,
    panel: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit,
) {
    val purple = Color(0xFF7044D8)
    val inputStyle = TextStyle(color = Color(0xFF42384F), fontSize = 15.sp, lineHeight = 22.sp,
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false))
    Column(Modifier.fillMaxSize().background(Color.White).imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color(0xFF352C4B)) }
            ChatOnlineAvatar(name, avatar, online, Modifier.clickable(onClickLabel = "查看个人主页", onClick = onOpenProfile), demo = demo)
            Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, modifier = Modifier.weight(1f, fill = false), color = Color(0xFF30273E), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (online) ChatOnlineLabel()
                }
                subtitle?.let { Text(it, color = Color(0xFF9B86B6), fontSize = 11.sp) }
            }
        }
        if (demo) Text("演示会话 · 消息、通话和送礼为本机模拟", Modifier.fillMaxWidth().background(Color(0xFFF5F0FD)).padding(14.dp, 7.dp), color = Color(0xFF9B86B6), fontSize = 11.sp)
        Column(Modifier.weight(1f).fillMaxWidth()) { content() }
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(draft, { onDraft(it.take(500)) }, Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFF5F3F8)).padding(horizontal = 14.dp, vertical = 11.dp), enabled = enabled,
                textStyle = inputStyle, maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (enabled && !sending && draft.isNotBlank()) onSend() }),
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (draft.isEmpty()) Text("发送消息…", style = inputStyle.copy(color = Color(0xFFB0A3C0))); inner() } })
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSend, enabled = enabled && !sending && draft.isNotBlank(), shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(37.dp), colors = ButtonDefaults.buttonColors(containerColor = purple)) {
                Text(if (sending) "发送中" else "发送", fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            QumiSecretaryTool(Icons.Rounded.PhoneInTalk, "语音通话", Color(0xFF6347E9), Color(0xFFEFEAFF), enabled && !sending, onVoice)
            QumiSecretaryTool(Icons.Rounded.Videocam, "视频通话", Color(0xFF3B6BE8), Color(0xFFE9F0FF), enabled && !sending, onVideo)
            Image(painterResource(R.drawable.qumi_chat_gift), "送礼", contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(46.dp).clickable(enabled = enabled && !sending, onClick = onGift))
            QumiSecretaryTool(Icons.Rounded.Mood, "表情", Color(0xFF6F628D), Color(0xFFF4F2F9), onClick = onEmoji)
            QumiSecretaryTool(Icons.Rounded.AddCircleOutline, "图片和视频", Color(0xFF6F628D), Color(0xFFF4F2F9), onClick = onMore)
        }
        panel()
    }
}

@Composable
internal fun DirectChatMediaActions(onImage: () -> Unit, onVideo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        listOf(Triple(Icons.Rounded.Image, "图片", Color(0xFF35C866)) to onImage,
            Triple(Icons.Rounded.Videocam, "视频", Color(0xFF6374EF)) to onVideo).forEach { (item, action) ->
            Column(Modifier.width(56.dp).clickable(onClick = action), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF5F4F8)),
                    contentAlignment = Alignment.Center) {
                    Icon(item.first, "发送${item.second}", Modifier.size(27.dp), tint = item.third)
                }
                Text(item.second, fontSize = 12.sp, color = Color(0xFF96919F))
            }
        }
    }
}
