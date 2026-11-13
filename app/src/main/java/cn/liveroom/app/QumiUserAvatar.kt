package cn.liveroom.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
internal fun QumiUserAvatar(
    token: String,
    path: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = "用户头像",
    fallback: @Composable () -> Unit,
) {
    var bitmap by remember(token, path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(token, path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                require(path?.matches(Regex("/user/avatars/[1-9][0-9]{0,9}/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.jpg", RegexOption.IGNORE_CASE)) == true)
                val connection = URL(BuildConfig.BILLD_API_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 10_000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    check(connection.responseCode == 200 && connection.contentType.orEmpty().startsWith("image/jpeg"))
                    val bytes = connection.inputStream.use { it.readBytesLimited(2 * 1024 * 1024 + 1) }
                    check(bytes.size in 1..(2 * 1024 * 1024))
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        fallback()
        bitmap?.let { Image(it.asImageBitmap(), contentDescription, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}
