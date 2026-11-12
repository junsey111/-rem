package cn.liveroom.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Kept above the conversation so opening and closing a profile preserves the chat. */
@Composable
internal fun DirectChatProfile(token: String, userId: Long, ownId: Long, name: String, onBack: () -> Unit,
    onMessage: ((String, String) -> Unit)? = null) {
    var blockConfirm by remember(userId) { mutableStateOf(false) }
    var busy by remember(userId) { mutableStateOf(false) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        QumiPublicProfileScreen(token, userId, ownId, name, onBack = onBack,
            onMessage = { id, username -> onBack(); onMessage?.invoke(id, username) }, onBlock = { blockConfirm = true })
        if (blockConfirm) AlertDialog(onDismissRequest = { if (!busy) blockConfirm = false },
            title = { Text("屏蔽 $name？") }, text = { Text(error ?: "该用户会加入黑名单，可在设置中解除屏蔽。") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true; error = null
                    try { LivePlatformApi().setBlocked(token, userId, true); blockConfirm = false; onBack() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "暂时无法屏蔽，请重试" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "处理中…" else "屏蔽") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { blockConfirm = false }) { Text("取消") } })
    }
}
