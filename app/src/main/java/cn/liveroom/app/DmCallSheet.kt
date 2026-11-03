package cn.liveroom.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Composable
fun DmCallActions(
    canStart: Boolean,
    incoming: DmCallInvite?,
    onVideo: () -> Unit,
    onVoice: () -> Unit,
    onJoin: (String) -> Unit,
    onAccept: (DmCallInvite) -> Unit,
) {
    var callId by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onVoice, enabled = canStart, modifier = Modifier.weight(1f)) { Text("语音通话") }
            OutlinedButton(onClick = onVideo, enabled = canStart, modifier = Modifier.weight(1f)) { Text("视频通话") }
        }
        incoming?.let { invite ->
            Button(onClick = { onAccept(invite) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (invite.mode == "video") "接听视频" else "接听语音")
            }
        }
        OutlinedTextField(
            value = callId,
            onValueChange = { callId = it.trim().lowercase() },
            label = { Text("通话号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { onJoin(callId) }, enabled = callId.matches(Regex("[0-9a-f]{32}"))) { Text("用通话号加入") }
    }
}

@Composable
fun DmCallSheet(
    session: UserSession,
    api: LivePlatformApi,
    peerId: String,
    peerName: String?,
    requestedMode: String,
    callId: String?,
    onSignal: suspend (DmCallCredentials) -> Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val gateway = remember { DmCallGateway(context) }
    var creds by remember { mutableStateOf<DmCallCredentials?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var signalNote by remember { mutableStateOf<String?>(null) }
    var remoteCount by remember { mutableIntStateOf(0) }
    var sawPeer by remember { mutableStateOf(false) }
    val connection by gateway.connection.collectAsState()
    val permissionWaiter = remember { mutableStateOf<kotlinx.coroutines.CancellableContinuation<Boolean>?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val waiter = permissionWaiter.value
        permissionWaiter.value = null
        if (waiter != null && waiter.isActive) waiter.resume(grants.values.all { it })
    }
    LaunchedEffect(peerId, requestedMode, callId) {
        var issued: DmCallCredentials? = null
        try {
            val created = if (callId.isNullOrBlank()) {
                val id = peerId.toLongOrNull() ?: kotlin.error("请先选择对方账号")
                api.startDmCall(session.token, id, requestedMode)
            } else {
                api.joinDmCall(session.token, callId)
            }
            issued = created
            creds = created
            if (!ensureCallPermissions(created.mode, context, permissionWaiter, permissions)) {
                error = "通话需要麦克风权限，视频还需要摄像头权限"
                return@LaunchedEffect
            }
            if (callId.isNullOrBlank()) {
                launch {
                    val sent = runCatching { onSignal(created) }.getOrDefault(false)
                    signalNote = if (sent) "已通过私信发送通话号" else "私信未发出，请把通话号告诉对方"
                }
            }
            gateway.connect(created.url, created.token, created.mode == "video")
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "通话失败"
        } finally {
            withContext(NonCancellable) {
                issued?.let { runCatching { api.endDmCall(session.token, it.callId) } }
                gateway.hangUp()
            }
        }
    }
    LaunchedEffect(gateway) {
        while (true) {
            val count = runCatching { gateway.remoteParticipantCount }.getOrDefault(0)
            if (count > 0) sawPeer = true
            remoteCount = count
            delay(500)
        }
    }
    val current = creds
    val mode = current?.mode ?: requestedMode
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (mode == "video" && current != null && error == null) {
                val remoteId = if (current.callerId == session.id.toString()) current.peerId else current.callerId
                AndroidView(
                    factory = { gateway.createVideoRenderer(it, remoteId) },
                    update = { gateway.attachVideoRenderer(it) },
                    onRelease = { gateway.releaseVideoRenderer(it); it.release() },
                    modifier = Modifier.fillMaxSize(),
                )
                AndroidView(
                    factory = { gateway.createVideoRenderer(it, session.id.toString()) },
                    update = { gateway.attachVideoRenderer(it) },
                    onRelease = { gateway.releaseVideoRenderer(it); it.release() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(120.dp, 160.dp),
                )
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(callTitle(mode), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(peerName?.takeIf { it.isNotBlank() } ?: peerId.ifBlank { "对方" }, color = Color.White)
                Text(callStatus(connection, remoteCount, sawPeer && remoteCount == 0, error), color = Color(0xFFDDDEE2))
                signalNote?.let { Text(it, color = Color(0xFFDDDEE2), style = MaterialTheme.typography.bodySmall) }
                current?.let { Text("通话号 ${it.callId}", color = Color(0xFFDDDEE2), style = MaterialTheme.typography.bodySmall) }
                if (error == null && connection == MediaConnectionState.IDLE) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF343D), contentColor = Color.White),
                ) { Text(if (error == null) "挂断" else "关闭") }
            }
        }
    }
}

private suspend fun ensureCallPermissions(
    mode: String,
    context: android.content.Context,
    waiter: androidx.compose.runtime.MutableState<kotlinx.coroutines.CancellableContinuation<Boolean>?>,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
): Boolean {
    val needed = if (mode == "video") {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }
    val missing = needed.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    if (missing.isEmpty()) return true
    return suspendCancellableCoroutine { continuation ->
        waiter.value = continuation
        continuation.invokeOnCancellation { if (waiter.value === continuation) waiter.value = null }
        launcher.launch(missing.toTypedArray())
    }
}

private fun callTitle(mode: String) = when (mode) {
    "video" -> "视频通话"
    "voice" -> "语音通话"
    else -> "通话"
}

private fun callStatus(connection: MediaConnectionState, remoteCount: Int, peerLeft: Boolean, error: String?): String {
    if (error != null) return error
    return when (connection) {
        MediaConnectionState.RECONNECTING -> "连接中断，正在恢复"
        MediaConnectionState.DISCONNECTED -> "通话已断开"
        MediaConnectionState.CONNECTED -> when {
            remoteCount > 0 -> "通话中"
            peerLeft -> "对方已离开"
            else -> "已连接，等待对方加入"
        }
        MediaConnectionState.IDLE -> "正在连接"
    }
}
