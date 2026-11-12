package cn.liveroom.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
@Suppress("ProduceStateDoesNotAssignValue") // The producer below explicitly assigns value; Compose lint misses the Kotlin setter.
internal fun AnnouncementImage(path: String, cover: Boolean = false) {
    val bitmap by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                require(path.matches(Regex("/platform-content/images/[0-9a-f-]{36}")))
                val connection = URL(BuildConfig.BILLD_API_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5000; connection.readTimeout = 5000; connection.instanceFollowRedirects = false
                    check(connection.responseCode == 200)
                    val bytes = connection.inputStream.use { it.readBytesLimited(3 * 1024 * 1024 + 1) }
                    check(bytes.size <= 3 * 1024 * 1024)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
    val ratio = if (cover) 3.2f else bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1.7f
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(9.dp)).background(Color(0xFFF0EBFA))) {
        bitmap?.let { Image(it.asImageBitmap(), "公告图片", modifier = Modifier.fillMaxSize(), contentScale = if (cover) ContentScale.Crop else ContentScale.Fit) }
    }
}

@Composable
internal fun AnnouncementBlock(block: JSONObject) {
    if (block.optString("type") == "image") {
        AnnouncementImage(block.optString("url")); return
    }
    val heading = block.optString("type") == "heading"
    val value = buildAnnotatedString {
        if (block.optString("type") == "bullet") append("•  ")
        val runs = block.optJSONArray("runs")
        if (runs != null) for (index in 0 until runs.length()) {
            val run = runs.optJSONObject(index) ?: continue
            withStyle(SpanStyle(fontWeight = if (heading || run.optBoolean("bold")) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (run.optBoolean("italic")) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (run.optBoolean("underline")) TextDecoration.Underline else TextDecoration.None)) { append(run.optString("text")) }
        }
    }
    Text(value, Modifier.fillMaxWidth(), color = Color(0xFF302A3D),
        fontSize = if (heading) if (block.optInt("level") == 3) 19.sp else 22.sp else 16.sp,
        lineHeight = if (heading) 31.sp else 27.sp,
        textAlign = when (block.optString("align")) { "center" -> TextAlign.Center; "right" -> TextAlign.Right; else -> TextAlign.Left })
}
