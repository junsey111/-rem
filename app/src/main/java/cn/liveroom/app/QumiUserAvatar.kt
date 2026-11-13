package cn.liveroom.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
@Suppress("ProduceStateDoesNotAssignValue") // The bitmap producer explicitly assigns value.
internal fun QumiUserAvatar(avatar: String?, name: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, avatar) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val resolved = if (avatar?.startsWith("/") == true && !avatar.startsWith("//")) BuildConfig.BILLD_API_URL.trimEnd('/') + avatar else avatar
                val address = resolved?.takeIf { it.length <= 500 && (it.startsWith("https://") || it.startsWith("http://")) }
                    ?: return@runCatching null
                val connection = URL(address).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    connection.instanceFollowRedirects = false
                    if (connection.responseCode != 200) return@runCatching null
                    val bytes = connection.inputStream.use { stream ->
                        val buffer = ByteArray(8192)
                        val output = java.io.ByteArrayOutputStream()
                        while (output.size() <= 2 * 1024 * 1024) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray().takeIf { it.size <= 2 * 1024 * 1024 }
                    } ?: return@runCatching null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
    Box(modifier.clip(CircleShape).background(Color(0xFFE9E2FF)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = "$name 的头像",
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Image(painterResource(R.drawable.qumi_mine_avatar), contentDescription = "$name 的头像",
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
