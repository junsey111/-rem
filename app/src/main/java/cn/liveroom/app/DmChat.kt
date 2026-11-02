package cn.liveroom.app

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

@Composable
fun DmMessageBubble(
    message: OpenImMessage,
    playable: Boolean?,
    busy: Boolean,
    onUnlock: (String) -> Unit,
    onPlay: (String) -> Unit,
    onVerifyGift: (ChatBody.Gift) -> Unit,
) {
    when (val body = chatBody(message)) {
        is ChatBody.Text -> Text(body.text)
        is ChatBody.Gift -> Column {
            Text("礼物 ${body.giftName} × ${body.quantity}")
            Text("${body.chargedCoins} 金币", color = MaterialTheme.colorScheme.primary)
            Text("回执 ${body.giftOrderId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("扣费以结算记录为准", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onVerifyGift(body) }, enabled = !busy) { Text("核对回执") }
        }
        is ChatBody.Video -> {
            val canPlay = body.priceCoins == 0L || playable == true
            Column {
                Text(if (body.paywalled) "付费视频" else "视频")
                Text(body.title)
                Text(
                    "${body.durationSec} 秒" + if (body.priceCoins > 0) " · ${body.priceCoins} 金币" else " · 免费",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canPlay) TextButton(onClick = { onPlay(body.videoId) }, enabled = !busy) { Text(if (busy) "请稍候…" else "播放") }
                else TextButton(onClick = { onUnlock(body.videoId) }, enabled = !busy) { Text(if (busy) "请稍候…" else "解锁观看") }
            }
        }
        is ChatBody.Other -> Text(body.label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmGiftSheet(
    gifts: List<Gift>,
    balance: Long?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (Gift) -> Unit,
) {
    var selected by remember { mutableStateOf(gifts.firstOrNull()?.id) }
    val gift = gifts.firstOrNull { it.id == selected }
    val enough = gift == null || balance == null || balance >= gift.coinPrice
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("私信送礼", style = MaterialTheme.typography.titleLarge)
            Text("扣费由结算服务确认。结算未接通时不会发送礼物消息，也不会在本机改余额。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (gifts.isEmpty()) Text("暂时没有可赠送的礼物")
            else LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(gifts, key = { it.id }) { item ->
                    Surface(onClick = { if (!busy) selected = item.id }, color = if (selected == item.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.name)
                            Text("${item.coinPrice} 金币", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(onClick = { gift?.let(onSend) }, enabled = !busy && gift != null && enough, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "正在确认结算…" else if (!enough) "余额不足" else "确认赠送")
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("返回聊天") }
        }
}

@Composable
fun DmVideoSheet(busy: Boolean, pendingRequest: Boolean, onDismiss: () -> Unit, onSend: (String, Int, ByteArray, Int) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("0") }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val coins = price.toIntOrNull()
        if (title.isBlank() || coins == null || coins < 0) {
            error = "请先填写标题，价格填 0 表示免费"
            return@rememberLauncherForActivityResult
        }
        try {
            val payload = readVideo(context, uri)
            onSend(title.trim(), coins, payload.first, payload.second)
        } catch (exception: Exception) {
            error = exception.message ?: "无法读取视频"
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (pendingRequest) "重试发送视频" else "发送视频", style = MaterialTheme.typography.titleLarge)
            Text("价格为 0 时对方可直接播放。大于 0 时对方解锁才扣费，扣费由结算服务确认。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = price, onValueChange = { price = it.filter(Char::isDigit).take(7) }, label = { Text("价格（金币，0 为免费）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = { picker.launch("video/*") }, enabled = !busy && title.isNotBlank() && price.toIntOrNull() != null, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "正在上传…" else "选择 MP4 或 WebM")
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("返回聊天") }
        }
}

private fun readVideo(context: android.content.Context, uri: Uri): Pair<ByteArray, Int> {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() <= 12 * 1024 * 1024) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
        }
        output.toByteArray()
    } ?: error("无法读取视频")
    require(bytes.size in 1..(12 * 1024 * 1024)) { "视频不能超过 12MB" }
    val retriever = MediaMetadataRetriever()
    val duration = try {
        retriever.setDataSource(context, uri)
        val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        (ms / 1000L).toInt()
    } finally { retriever.release() }
    require(duration in 1..600) { "视频时长需在 1 秒到 10 分钟之间" }
    return bytes to duration
}

@Composable
fun DmVideoPlayerDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("视频") },
        text = {
            AndroidView(
                factory = { viewContext -> PlayerView(viewContext).apply { this.player = player } },
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        },
    )
}
