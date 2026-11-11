package cn.liveroom.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image as ImageIcon
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val ComposerPurple = Color(0xFF7044D8)
private data class SelectedMomentMedia(val uri: Uri, val kind: String)

@Composable
internal fun QumiMomentComposer(token: String, onBack: () -> Unit, onPublished: (MomentPost) -> Unit,
    videoOnly: Boolean = false) {
    val context = LocalContext.current
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<SelectedMomentMedia?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf<MomentPost?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { selected = SelectedMomentMedia(uri, "image"); error = null }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { selected = SelectedMomentMedia(uri, "video"); error = null }
    }
    val preview by produceState<Bitmap?>(null, selected) {
        value = selected?.let { media -> withContext(Dispatchers.IO) { previewMomentMedia(context, media) } }
    }
    BackHandler(enabled = !publishing) { onBack() }

    submitted?.let { post ->
        AlertDialog(onDismissRequest = { onPublished(post) }, containerColor = LiveStyle.dialogSurface,
            title = { Text("已提交，等待审核", color = LiveStyle.ink) },
            text = { Text("审核通过后会对其他用户展示，可在“我的发布”中查看审核状态。", color = LiveStyle.muted) },
            confirmButton = { TextButton(onClick = { onPublished(post) }) { Text("知道了", color = ComposerPurple) } })
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF2F3F5))) {
        Box(Modifier.fillMaxWidth().height(62.dp).background(Color.White)) {
            IconButton(onClick = onBack, enabled = !publishing, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color(0xFF151515), modifier = Modifier.size(27.dp))
            }
            Text(if (videoOnly) "发布视频" else "动态发布", modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF121212), fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Button(
                onClick = {
                    publishing = true
                    error = null
                    val media = selected
                    scope.launch {
                        try {
                            val upload = media?.let { MomentUpload(it.kind, withContext(Dispatchers.IO) { readMomentMedia(context, it) }) }
                            val post = api.publishMoment(token, content, upload)
                            if (post.reviewStatus == "PENDING") submitted = post else onPublished(post)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: "发布失败，请重试"
                        } finally {
                            publishing = false
                        }
                    }
                },
                enabled = !publishing && if (videoOnly) selected?.kind == "video" else (content.isNotBlank() || selected != null),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).height(35.dp),
                shape = RoundedCornerShape(7.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ComposerPurple),
            ) { Text(if (publishing) "提交中" else "提交审核", fontSize = 15.sp) }
        }
        if (publishing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ComposerPurple)
        Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp).height(170.dp)
            .clip(RoundedCornerShape(5.dp)).background(Color.White).padding(18.dp)) {
            BasicTextField(
                value = content,
                onValueChange = { content = it.take(500); error = null },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(color = Color(0xFF222222), fontSize = 16.sp, lineHeight = 24.sp),
                cursorBrush = SolidColor(ComposerPurple),
                decorationBox = { inner ->
                    Box {
                        if (content.isEmpty()) Text(if (videoOnly) "介绍一下你的视频～" else "分享生活中有趣的新鲜事儿～",
                            color = Color(0xFF929292), fontSize = 15.sp)
                        inner()
                    }
                },
            )
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            selected?.let { media ->
                Box(Modifier.size(82.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE4E1EC))) {
                    if (preview != null) Image(preview!!.asImageBitmap(), contentDescription = "已选媒体", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(if (media.kind == "video") Icons.Rounded.Videocam else Icons.Rounded.ImageIcon,
                        contentDescription = null, tint = ComposerPurple, modifier = Modifier.align(Alignment.Center).size(34.dp))
                    if (media.kind == "video") Text("视频", modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = .6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 11.sp)
                    IconButton(onClick = { selected = null }, enabled = !publishing,
                        modifier = Modifier.align(Alignment.TopEnd).size(25.dp).background(Color.Black.copy(alpha = .55f))) {
                        Icon(Icons.Rounded.Close, contentDescription = "移除附件", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Box {
                Box(Modifier.size(82.dp).clip(RoundedCornerShape(5.dp)).background(Color.White)
                    .clickable(enabled = !publishing) {
                        if (videoOnly) videoPicker.launch("video/*") else pickerOpen = true
                    }, contentAlignment = Alignment.Center) {
                    Icon(if (videoOnly) Icons.Rounded.Videocam else Icons.Rounded.Add,
                        contentDescription = if (videoOnly) "添加视频" else "添加图片或视频",
                        tint = Color(0xFF999999), modifier = Modifier.size(38.dp))
                }
                DropdownMenu(expanded = pickerOpen && !videoOnly, onDismissRequest = { pickerOpen = false }) {
                    DropdownMenuItem(text = { Text("图片") }, leadingIcon = { Icon(Icons.Rounded.ImageIcon, null) },
                        onClick = { pickerOpen = false; imagePicker.launch("image/*") })
                    DropdownMenuItem(text = { Text("视频") }, leadingIcon = { Icon(Icons.Rounded.Videocam, null) },
                        onClick = { pickerOpen = false; videoPicker.launch("video/*") })
                }
            }
        }
        error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = Color(0xFFB4233B), fontSize = 13.sp) }
        Text("内容审核通过后展示", Modifier.padding(horizontal = 16.dp, vertical = 14.dp), color = Color(0xFF9992A8), fontSize = 12.sp)
    }
}

private fun readMomentMedia(context: Context, media: SelectedMomentMedia): ByteArray {
    val limit = if (media.kind == "image") 5 * 1024 * 1024 else 20 * 1024 * 1024
    val input = context.contentResolver.openInputStream(media.uri) ?: error("无法读取媒体文件")
    return input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() <= limit) {
            val count = stream.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        require(output.size() in 1..limit) { if (media.kind == "image") "图片不能超过 5MB" else "视频不能超过 20MB" }
        output.toByteArray()
    }
}

private fun previewMomentMedia(context: Context, media: SelectedMomentMedia): Bitmap? = runCatching {
    if (media.kind == "image") {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        context.contentResolver.openInputStream(media.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } else {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, media.uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally { retriever.release() }
    }
}.getOrNull()
