package cn.liveroom.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal enum class DisplayQrKind(val slug: String, val title: String) {
    SUPPORT("support", "联系客服"),
    RECHARGE("recharge", "充值二维码"),
}

@Composable
internal fun DisplayQrDialog(kind: DisplayQrKind, onDismiss: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(kind, retry) {
        loading = true
        bitmap = null
        error = null
        try {
            val result = withContext(Dispatchers.IO) {
                val connection = URL(BuildConfig.BILLD_API_URL.trimEnd('/') + "/qr/${kind.slug}")
                    .openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 12_000
                    connection.instanceFollowRedirects = false
                    when (connection.responseCode) {
                        200 -> connection.inputStream.use { BitmapFactory.decodeStream(it) }
                            ?: throw IllegalStateException("二维码图片无法读取")
                        404 -> throw IllegalStateException("${kind.title}尚未配置")
                        else -> throw IllegalStateException("二维码暂时无法加载")
                    }
                } finally {
                    connection.disconnect()
                }
            }
            bitmap = result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "二维码暂时无法加载"
        } finally {
            loading = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (kind == DisplayQrKind.SUPPORT) Icons.Rounded.SupportAgent else Icons.Rounded.QrCode2, contentDescription = null) },
        title = { Text(kind.title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    val displayed = bitmap
                    when {
                        loading -> CircularProgressIndicator()
                        displayed != null -> Image(
                            displayed.asImageBitmap(), contentDescription = kind.title,
                            modifier = Modifier.size(240.dp), contentScale = ContentScale.Fit,
                        )
                        else -> Text(error ?: "二维码暂时无法加载")
                    }
                }
                if (kind == DisplayQrKind.RECHARGE && bitmap != null)
                    Text("付款后请联系客服核对到账", modifier = Modifier.padding(top = 8.dp))
                if (error != null) TextButton(onClick = { retry++ }) { Text("重试") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
