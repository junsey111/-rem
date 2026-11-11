package cn.liveroom.app

import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        is ChatBody.Image -> DmImageMessage(body.url)
        is ChatBody.Gift -> Column {
            GiftArtwork(Gift(body.giftId, body.giftName, (body.chargedCoins / body.quantity.coerceAtLeast(1)).toInt(), 0), Modifier.size(90.dp), body.giftName)
            Text("礼物 ${body.giftName} × ${body.quantity}")
            Text("${body.chargedCoins} 金币")
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

@Composable
private fun DmImageMessage(url: String?) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var attempted by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val target = URL(url ?: error("图片地址缺失"))
                require(target.protocol == "https" || target.protocol == "http")
                val connection = target.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5000
                    connection.readTimeout = 8000
                    require(connection.responseCode == 200)
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= 4 * 1024 * 1024) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size <= 4 * 1024 * 1024)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample })
                } finally { connection.disconnect() }
            }.getOrNull()
        }
        attempted = true
    }
    when {
        bitmap != null -> Image(bitmap!!.asImageBitmap(), contentDescription = "聊天图片",
            contentScale = ContentScale.Crop, modifier = Modifier.size(200.dp, 160.dp))
        !attempted -> CircularProgressIndicator(Modifier.size(24.dp))
        else -> Text("图片暂无法预览")
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
            Text("选择一份心意送给对方", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (gifts.isEmpty()) Text("暂时没有可赠送的礼物")
            else LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(gifts, key = { it.id }) { item ->
                    Surface(onClick = { if (!busy) selected = item.id }, color = if (selected == item.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(5.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            GiftArtwork(item, Modifier.size(52.dp), item.name)
                            Text(item.name)
                            Text("${item.coinPrice} 金币", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(onClick = { gift?.let(onSend) }, enabled = !busy && gift != null, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "正在确认结算…" else if (!enough) "余额不足" else "确认赠送")
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("返回聊天") }
        }
}

@Composable
fun DmVideoSheet(busy: Boolean, pendingRequest: Boolean, paid: Boolean, onDismiss: () -> Unit, onSend: (String, Int, ByteArray, Int) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var price by remember(paid) { mutableStateOf(if (paid) "" else "0") }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val coins = price.toIntOrNull()
        if (title.isBlank() || coins == null || (if (paid) coins < 1 else coins != 0)) {
            error = if (paid) "请填写标题和大于 0 的金币价格" else "请填写视频标题"
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
            Text(if (pendingRequest) "重试发送视频" else if (paid) "发送付费视频" else "发送免费视频", style = MaterialTheme.typography.titleLarge)
            Text(if (paid) "对方解锁观看时才扣费，扣费由结算服务确认。" else "对方可直接观看，不收取金币。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (paid) OutlinedTextField(value = price, onValueChange = { price = it.filter(Char::isDigit).take(7) }, label = { Text("价格（金币）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = { picker.launch("video/*") }, enabled = !busy && title.isNotBlank() && price.toIntOrNull()?.let { if (paid) it > 0 else it == 0 } == true, modifier = Modifier.fillMaxWidth()) {
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
