package cn.liveroom.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
internal fun DirectCallSurface(name: String, avatar: String?, video: Boolean, connected: Boolean,
    duration: Int, status: String, mic: Boolean, speaker: Boolean, camera: Boolean, controlsEnabled: Boolean,
    onMic: () -> Unit, onSpeaker: () -> Unit, onCamera: () -> Unit, onFlip: () -> Unit, onHangup: () -> Unit,
    gifts: List<Gift>, balance: Long?, giftBusy: Boolean, demo: Boolean, onGift: (Gift) -> Unit,
    celebration: Gift?, celebrationNonce: Int, ownName: String, ownAvatar: String?, onCelebrationFinished: () -> Unit,
    remoteVideo: @Composable () -> Unit, localVideo: @Composable () -> Unit) {
    var minimized by remember { mutableStateOf(false) }
    var swapped by remember { mutableStateOf(false) }
    var giftOpen by remember { mutableStateOf(false) }
    var selectedGift by remember { mutableStateOf<String?>(null) }
    val time = "${(duration / 60).toString().padStart(2, '0')}:${(duration % 60).toString().padStart(2, '0')}"
    if (minimized) {
        Popup(alignment = Alignment.TopEnd, offset = IntOffset(-20, 100), properties = PopupProperties(focusable = false)) {
            Box(Modifier.size(126.dp, 174.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF251D32))
                .clickable(onClickLabel = "回到通话") { minimized = false }) {
                if (video && connected) { if (swapped) localVideo() else remoteVideo() }
                else QumiUserAvatar(avatar, name, Modifier.size(64.dp).align(Alignment.Center))
                Text(if (connected) time else "呼叫中", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .5f)).padding(8.dp))
            }
        }
        return
    }
    Dialog(onDismissRequest = { if (giftOpen) giftOpen = false else if (connected) minimized = true else onHangup() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF38313F), Color(0xFF17131F))))) {
            if (video && connected) { if (swapped) localVideo() else remoteVideo() }
            if (!video || !connected) Column(Modifier.align(Alignment.Center).padding(bottom = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                QumiUserAvatar(avatar, name, Modifier.size(104.dp))
                Text(name, color = Color.White, fontSize = 25.sp, modifier = Modifier.padding(top = 16.dp))
                if (!connected) Text(status, color = Color(0xFFD9CEEA), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = { minimized = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .3f))) {
                    Icon(Icons.Rounded.PictureInPictureAlt, "缩小通话", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(if (connected) "已接通 $time" else status, color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = .3f)).padding(12.dp, 7.dp))
            }
            if (video && connected) Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 78.dp, end = 18.dp)
                .size(96.dp, 140.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2E263A))
                .clickable(onClickLabel = "切换大小画面") { swapped = !swapped }) {
                if (swapped) remoteVideo() else localVideo()
                Icon(Icons.Rounded.SwapHoriz, "切换大小画面", tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(18.dp))
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f))))
                .navigationBarsPadding().padding(horizontal = 28.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                if (video) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DirectCallAction(if (mic) Icons.Rounded.Mic else Icons.Rounded.MicOff, if (mic) "麦克风已开" else "麦克风已关", mic, controlsEnabled, onMic)
                    DirectCallAction(if (speaker) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, if (speaker) "扬声器已开" else "扬声器已关", speaker, controlsEnabled, onSpeaker)
                    DirectCallAction(if (camera) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, if (camera) "摄像头已开" else "摄像头已关", camera, controlsEnabled, onCamera)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (video) DirectCallAction(Icons.Rounded.CardGiftcard, "送礼", false, connected, { giftOpen = true })
                    else DirectCallAction(if (mic) Icons.Rounded.Mic else Icons.Rounded.MicOff, if (mic) "麦克风已开" else "麦克风已关", mic, controlsEnabled, onMic)
                    DirectCallAction(Icons.Rounded.CallEnd, if (connected) "挂断" else "取消", true, true, onHangup, hangup = true)
                    if (video) DirectCallAction(Icons.Rounded.Cameraswitch, "翻转摄像头", false, controlsEnabled && camera, onFlip)
                    else DirectCallAction(if (speaker) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, if (speaker) "扬声器已开" else "扬声器已关", speaker, controlsEnabled, onSpeaker)
                }
                if (demo) Text("演示通话 · 本机模拟 · 不扣费", color = Color(0xFFD9CDE8), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            if (giftOpen) {
                BackHandler { giftOpen = false }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .15f))
                        .clickable(onClickLabel = "收起礼物面板") { giftOpen = false })
                    Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 72.dp)
                        .clip(RoundedCornerShape(18.dp)).background(Color(0xFFFCFAFF))
                        .pointerInput(Unit) { detectTapGestures {} }) {
                        QumiGiftPicker(gifts, selectedGift, balance, giftBusy, demo, GiftPickerAppearance.DirectMessage,
                            onSelect = { selectedGift = it }, onDismiss = { giftOpen = false },
                            onSend = { gifts.firstOrNull { it.id == selectedGift }?.let { onGift(it); giftOpen = false } })
                    }
                }
            }
            celebration?.let { QumiGiftCelebration(it, celebrationNonce, demo, ownName, ownAvatar, bottomPadding = 330.dp, onFinished = onCelebrationFinished) }
        }
    }
}

@Composable
private fun DirectCallAction(icon: ImageVector, label: String, active: Boolean, enabled: Boolean,
    onClick: () -> Unit, hangup: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        FilledIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(60.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = when { hangup -> Color(0xFFFF4E59); active -> Color.White; else -> Color.White.copy(alpha = .23f) },
                contentColor = if (active && !hangup) Color(0xFF211D28) else Color.White)) { Icon(icon, label, Modifier.size(28.dp)) }
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else .45f), fontSize = 11.sp)
    }
}
