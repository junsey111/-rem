package cn.liveroom.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val NotificationInk = LiveStyle.ink
private val NotificationMuted = LiveStyle.muted

@Composable
internal fun QumiNotificationSettingsScreen(token: String, onBack: () -> Unit) {
    var preferences by remember { mutableStateOf<NotificationPreferences?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val api = remember { LivePlatformApi() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(token, reload) {
        loading = true
        error = null
        runCatching { api.notificationPreferences(token) }
            .onSuccess { preferences = it }
            .onFailure { error = it.message ?: "通知设置加载失败" }
        loading = false
    }

    fun update(key: String, value: Boolean) {
        if (saving != null) return
        saving = key
        error = null
        scope.launch {
            runCatching { api.saveNotificationPreference(token, key, value) }
                .onSuccess { preferences = it }
                .onFailure { error = it.message ?: "保存失败，请重试" }
            saving = null
        }
    }

    Column(Modifier.fillMaxSize().background(LiveStyle.canvas)) {
        Row(Modifier.fillMaxWidth().height(56.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = NotificationInk) }
            Text("通知设置", Modifier.weight(1f), color = NotificationInk, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(48.dp))
        }
        if (loading && preferences == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (error != null && preferences == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = NotificationMuted)
                    TextButton(onClick = { reload++ }) { Text("重试") }
                }
            }
        } else {
            val value = preferences ?: NotificationPreferences(true, true, true, false)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Surface(color = Color.White, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("消息提醒", color = NotificationInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("选择你希望接收的通知类型", color = NotificationMuted, fontSize = 13.sp)
                        }
                    }
                }
                item { PreferenceRow("messages", "新私信", "收到站内私信时提醒", value.messages, saving) { update("messages", it) } }
                item { PreferenceRow("visitors", "主页访客", "有人访问你的个人主页时提醒", value.visitors, saving) { update("visitors", it) } }
                item { PreferenceRow("announcements", "平台公告", "平台发布重要公告时提醒", value.announcements, saving) { update("announcements", it) } }
                item { PreferenceRow("marketing", "活动通知", "接收活动与服务更新信息", value.marketing, saving) { update("marketing", it) } }
                if (error != null) item { Text(error!!, Modifier.padding(horizontal = 4.dp), color = Color(0xFFB4233B), fontSize = 13.sp) }
                item {
                    Surface(color = Color.White, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = LiveStyle.live)
                                Text("设备通知权限", color = NotificationInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("推送送达还需要平台推送服务和设备通知权限。", color = NotificationMuted, fontSize = 13.sp)
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            }) { Text("打开系统通知设置") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceRow(key: String, title: String, description: String, enabled: Boolean, saving: String?, onChange: (Boolean) -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = saving == null) { onChange(!enabled) }.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = NotificationInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(description, color = NotificationMuted, fontSize = 12.sp)
            }
            if (saving == key) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Switch(checked = enabled, onCheckedChange = onChange, colors = SwitchDefaults.colors(
                checkedTrackColor = LiveStyle.teal,
                uncheckedTrackColor = LiveStyle.raised,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = LiveStyle.line,
            ))
        }
    }
}
