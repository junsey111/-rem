package cn.liveroom.app

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONTokener
import org.json.JSONObject

internal fun parseOnlineChatUsers(raw: String): Set<String> {
    val value = JSONTokener(raw).nextValue()
    val rows = when (value) {
        is JSONArray -> value
        is JSONObject -> value.optJSONArray("userStatusList") ?: value.optJSONArray("data") ?: JSONArray()
        else -> JSONArray()
    }
    return buildSet {
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val id = row.optString("userID")
            if (id.matches(Regex("[1-9][0-9]{0,19}")) && row.optInt("status", 0) == 1) add(id)
        }
    }
}

@Composable
internal fun rememberChatForeground(): Boolean {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<ComponentActivity>().firstOrNull()
    }
    val lifecycle = activity?.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    return foreground
}

@Composable
internal fun rememberChatOnlineUsers(gateway: OpenImGateway, ids: List<String>, enabled: Boolean): Set<String> {
    val foreground = rememberChatForeground()
    val targets = ids.filter { it.matches(Regex("[1-9][0-9]{0,19}")) }.distinct().sorted()
    var online by remember(gateway, targets, enabled, foreground) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(gateway, targets, enabled, foreground) {
        if (!enabled || !foreground || targets.isEmpty()) return@LaunchedEffect
        while (true) {
            try {
                val result = mutableSetOf<String>()
                for (batch in targets.chunked(100)) result += withTimeout(8_000) { gateway.onlineUsers(batch) }
                online = result.intersect(targets.toSet())
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) { online = emptySet()
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { online = emptySet() }
            delay(15_000)
        }
    }
    return online
}

@Composable
internal fun ChatOnlineAvatar(name: String, avatar: String?, online: Boolean, modifier: Modifier = Modifier, demo: Boolean = false) {
    Box(modifier.size(44.dp)) {
        if (demo && avatar == null) Image(painterResource(R.drawable.qumi_demo_live_51287), contentDescription = "$name 的头像",
            contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(CircleShape))
        else QumiUserAvatar(avatar, name, Modifier.matchParentSize())
        if (online) Box(Modifier.align(Alignment.TopEnd).size(11.dp).clip(CircleShape)
            .background(Color(0xFF35C778)).border(2.dp, Color.White, CircleShape))
    }
}

@Composable
internal fun ChatOnlineLabel() {
    Text("在线", Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE8F8EE)).padding(horizontal = 5.dp, vertical = 1.dp),
        color = Color(0xFF2AB56B), fontSize = 10.sp, lineHeight = 12.sp)
}
