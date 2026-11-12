package cn.liveroom.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@Suppress("ProduceStateDoesNotAssignValue") // Image and video producers both explicitly assign value.
internal fun QumiMomentMedia(post: MomentPost, token: String, modifier: Modifier = Modifier) {
    val path = post.mediaPath ?: return
    if (post.mediaKind == "image") {
        var loading by remember(token, path) { mutableStateOf(true) }
        var previewing by remember(token, path) { mutableStateOf(false) }
        val bitmap by produceState<Bitmap?>(null, token, path) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = LivePlatformApi().momentImage(token, path)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
            loading = false
        }
        Box(modifier.aspectRatio(.7f).clip(RoundedCornerShape(8.dp)).background(Color(0xFFECECF1))
            .clickable(enabled = bitmap != null) { previewing = true }, contentAlignment = Alignment.Center) {
            when {
                bitmap != null -> Image(bitmap!!.asImageBitmap(), contentDescription = "动态图片", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                loading -> CircularProgressIndicator(Modifier.size(26.dp), color = Color(0xFF7044D8), strokeWidth = 2.dp)
                else -> Text("图片暂时无法显示", color = Color(0xFF777987), fontSize = 12.sp)
            }
        }
        if (previewing && bitmap != null) Dialog(
            onDismissRequest = { previewing = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Image(bitmap!!.asImageBitmap(), contentDescription = "图片预览",
                    modifier = Modifier.fillMaxSize().padding(vertical = 56.dp), contentScale = ContentScale.Fit)
                IconButton(onClick = { previewing = false },
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp, start = 10.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回动态", tint = Color.White)
                }
            }
        }
    } else if (post.mediaKind == "video") {
        var playing by remember(path) { mutableStateOf(false) }
        val poster by produceState<Bitmap?>(null, token, path) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource("${BuildConfig.BILLD_API_URL.trimEnd('/')}$path",
                            mapOf("Authorization" to "Bearer $token"))
                        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally { retriever.release() }
                }.getOrNull()
            }
        }
        Box(modifier.aspectRatio(.7f).clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF24242D)).clickable { playing = true }, contentAlignment = Alignment.Center) {
            poster?.let { Image(it.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Icon(Icons.Rounded.PlayCircle, contentDescription = "播放动态视频", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        if (playing) QumiMomentVideoViewer(path, token) { playing = false }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun QumiMomentVideoViewer(path: String, token: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(path, token) {
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            .setAllowCrossProtocolRedirects(false)
        ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http)).build().apply {
                setMediaItem(MediaItem.fromUri("${BuildConfig.BILLD_API_URL.trimEnd('/')}$path"))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(factory = { viewContext -> PlayerView(viewContext).apply { this.player = player; useController = true } },
                update = { it.player = player }, modifier = Modifier.fillMaxWidth().height(420.dp))
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭视频", tint = Color.White)
            }
        }
    }
}
