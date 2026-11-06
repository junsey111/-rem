package cn.liveroom.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Creator level is only returned by `/revenue/level` for the signed-in account.
 * Another host's level, fans, earnings, and bio are not on any current route.
 */
internal fun hostLevelLabel(hostId: Long, viewerId: Long, viewerLevel: UserLevel?): String = when {
    hostId != viewerId -> "主播等级暂未公开"
    viewerLevel == null -> "等级信息暂不可用"
    else -> "主播等级 Lv.${viewerLevel.creatorLevel}"
}

@Composable
fun HostIntroductionPage(
    name: String,
    creatorId: Long,
    coverPath: String?,
    roomTitle: String,
    levelLabel: String,
    onDismiss: () -> Unit,
    onEnterRoom: (() -> Unit)?,
) {
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "关闭主播介绍") }
                Text("主播介绍", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HostCover(coverPath)
                Text(name.ifBlank { "未设置昵称" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("账号 ID $creatorId", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(levelLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("正在直播：$roomTitle", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "简介、粉丝和收益没有公开数据，这里不会补写。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onEnterRoom != null) {
                    Button(onClick = onEnterRoom, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
                        Text("进入直播间")
                    }
                }
            }
        }
    }
}

@Composable
private fun HostCover(coverPath: String?) {
    var coverLoaded by remember(coverPath) { mutableStateOf<Boolean?>(null) }
    Box(
        Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF252A32)),
        contentAlignment = Alignment.Center,
    ) {
        if (coverPath != null) LiveCoverImage(coverPath) { coverLoaded = it }
        val coverNote = when {
            coverPath == null -> "暂无封面"
            coverLoaded == false -> "封面暂时无法显示"
            coverLoaded == null -> "正在读取封面"
            else -> null
        }
        if (coverNote != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = Color(0xFFCCD2DC), modifier = Modifier.size(32.dp))
                Text(coverNote, color = Color(0xFFCCD2DC), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Shown when an already visible support chat cannot be opened. A missing support account stays hidden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerServiceUnavailableSheet(message: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("客服聊天", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) { Text("知道了") }
        }
    }
}
