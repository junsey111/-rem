package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DirectFollowReminder(token: String, peerId: String) {
    var following by remember(peerId) { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(token, peerId) {
        try { following = LivePlatformApi().searchUsers(token, peerId).firstOrNull { it.id.toString() == peerId }?.isFollowing
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { }
    }
    if (following == false) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF7044D8)).padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("♡  关注TA，随时畅聊", Modifier.weight(1f), color = Color.White, fontSize = 12.sp)
            Surface(onClick = {
                if (!busy) scope.launch {
                    busy = true
                    try { LivePlatformApi().setFollowing(token, peerId.toLong(), true); following = true
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { error = "关注失败，请重试"
                    } finally { busy = false }
                }
            }, shape = RoundedCornerShape(14.dp), color = Color.White) {
                Text(if (busy) "处理中" else "关注", Modifier.padding(horizontal = 13.dp, vertical = 4.dp), color = Color(0xFF7044D8), fontSize = 11.sp)
            }
        }
    }
    error?.let { AlertDialog(onDismissRequest = { error = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } }) }
}
