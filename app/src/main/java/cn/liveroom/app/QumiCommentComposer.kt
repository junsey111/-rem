package cn.liveroom.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.Image as ImageIcon
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal data class CommentImageDraft(val bytes: ByteArray, val preview: Bitmap)

private fun readCommentImage(context: Context, uri: Uri): CommentImageDraft {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            require(output.size() + size <= 5 * 1024 * 1024) { "请选择 5MB 以内的图片" }
            output.write(buffer, 0, size)
        }
        output.toByteArray()
    } ?: error("无法读取图片，请重新选择")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 20_000_000) {
        "图片无法读取或尺寸过大，请换一张图片"
    }
    val preview = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val scale = minOf(1f, 1440f / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1440) options.inSampleSize *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("无法读取图片")
    }
    val normalized = ByteArrayOutputStream().also { preview.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
    return CommentImageDraft(normalized, preview)
}

@Composable
internal fun QumiCommentComposer(
    value: String, onValueChange: (String) -> Unit,
    image: CommentImageDraft?, onImageChange: (CommentImageDraft?) -> Unit,
    busy: Boolean, onSend: () -> Unit,
    modifier: Modifier = Modifier, focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var focused by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(value) { if (field.text != value) field = TextFieldValue(value, TextRange(value.length)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            picking = true
            scope.launch {
                try {
                    val selected = withContext(Dispatchers.IO) { readCommentImage(context, uri) }
                    onImageChange(selected)
                    error = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "图片读取失败，请重试" }
                finally { picking = false }
            }
        }
    }
    BackHandler(emojiOpen) { emojiOpen = false }
    Column(modifier.fillMaxWidth()) {
        image?.let { selected ->
            Box(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp).size(64.dp)) {
                Image(selected.preview.asImageBitmap(), contentDescription = "待发送的图片",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Box(Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = .5f)).clickable(enabled = !busy) { onImageChange(null) },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = "移除图片", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).height(44.dp).clip(CircleShape).background(Color(0xFFF5F4F8))
                .border(1.dp, if (focused) LiveStyle.accent else Color(0xFFEAE6F1), CircleShape)
                .padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(value = field, enabled = !busy, onValueChange = { next ->
                    if (next.text.length <= 500) { field = next; onValueChange(next.text) }
                }, singleLine = true, modifier = Modifier.weight(1f).height(44.dp).focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused; if (it.isFocused) emojiOpen = false },
                    textStyle = TextStyle(color = LiveStyle.ink, fontSize = 14.sp), cursorBrush = SolidColor(LiveStyle.accent),
                    decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text("发条评论，一起讨论", color = LiveStyle.muted, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    } })
                IconButton(onClick = { keyboard?.hide(); emojiOpen = false; picker.launch("image/*") },
                    enabled = !busy && !picking, modifier = Modifier.size(36.dp)) {
                    if (picking) CircularProgressIndicator(Modifier.size(18.dp), color = LiveStyle.accent, strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.ImageIcon, contentDescription = "添加评论图片", tint = LiveStyle.ink, modifier = Modifier.size(23.dp))
                }
                IconButton(onClick = {
                    val open = !emojiOpen
                    focus.clearFocus(); keyboard?.hide(); emojiOpen = open
                }, enabled = !busy, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.SentimentSatisfiedAlt, contentDescription = "评论表情",
                        tint = if (emojiOpen) LiveStyle.accent else LiveStyle.ink, modifier = Modifier.size(24.dp))
                }
            }
            Button(onClick = { emojiOpen = false; keyboard?.hide(); onSend() },
                enabled = !busy && !picking && (value.isNotBlank() || image != null),
                modifier = Modifier.width(60.dp).height(44.dp), shape = CircleShape,
                contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = LiveStyle.accent)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("发送", fontSize = 13.sp)
            }
        }
        error?.let { Text(it, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp), color = LiveStyle.live, fontSize = 12.sp) }
        if (value.length >= 450) Text("${value.length}/500", Modifier.padding(start = 26.dp, bottom = 4.dp), color = LiveStyle.muted, fontSize = 10.sp)
        if (emojiOpen) QumiEmojiStrip(onChoose = { emoji ->
            val start = field.selection.min.coerceIn(0, value.length)
            val end = field.selection.max.coerceIn(start, value.length)
            val updated = value.replaceRange(start, end, emoji)
            if (updated.length <= 500) {
                field = TextFieldValue(updated, TextRange(start + emoji.length))
                onValueChange(updated)
            }
        })
    }
}

@Composable
@Suppress("ProduceStateDoesNotAssignValue") // The image producer assigns its decoded bitmap.
internal fun QumiCommentImage(token: String, path: String) {
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
    Box(Modifier.padding(vertical = 4.dp).width(128.dp)
        .aspectRatio(bitmap?.let { (it.width.toFloat() / it.height).coerceIn(.7f, 1.7f) } ?: 1f)
        .clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1EFF5))
        .clickable(enabled = bitmap != null) { previewing = true }, contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(bitmap!!.asImageBitmap(), contentDescription = "评论图片，点击预览",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            loading -> CircularProgressIndicator(Modifier.size(20.dp), color = LiveStyle.accent, strokeWidth = 2.dp)
            else -> Text("图片加载失败", color = LiveStyle.muted, fontSize = 12.sp)
        }
    }
    if (previewing && bitmap != null) Dialog(onDismissRequest = { previewing = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            Image(bitmap!!.asImageBitmap(), contentDescription = "评论图片预览", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            IconButton(onClick = { previewing = false }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回评论", tint = Color.White)
            }
        }
    }
}
