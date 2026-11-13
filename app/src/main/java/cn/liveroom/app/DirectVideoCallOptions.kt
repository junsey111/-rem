package cn.liveroom.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException

@Composable
internal fun DirectVideoCallOptions(token: String, onDismiss: () -> Unit, onStart: (Int?) -> Unit) {
    var approved by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var paid by remember { mutableStateOf(false) }
    var price by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(token) {
        try { approved = LivePlatformApi().creatorApplications(token).any { it.status == "APPROVED" } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "暂时无法确认主播认证状态，可先发起普通视频通话。" }
        finally { loading = false }
    }
    DirectVideoCallOptionsDialog(approved, loading, paid, price, error, onDismiss,
        onPaidChange = { paid = it }, onPriceChange = { price = it.filter(Char::isDigit).take(7) },
        onStart = { onStart(if (paid) price.toInt() else null) })
}

@Composable
internal fun DirectVideoCallOptionsDialog(approved: Boolean, loading: Boolean, paid: Boolean,
    price: String, error: String?, onDismiss: () -> Unit, onPaidChange: (Boolean) -> Unit,
    onPriceChange: (String) -> Unit, onStart: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 360.dp).fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = Color.White) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(LiveStyle.accentSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Videocam, null, Modifier.size(27.dp), tint = LiveStyle.accent)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("视频通话", color = LiveStyle.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("选择本次通话方式", color = LiveStyle.muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, "关闭视频通话选项", Modifier.size(20.dp), tint = LiveStyle.muted)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VideoCallChoice("免费视频", "轻松面对面", !paid, true, Modifier.weight(1f)) { onPaidChange(false) }
                        VideoCallChoice("付费视频", if (approved) "按次收取金币" else "主播认证后开放", paid, approved && !loading,
                            Modifier.weight(1f)) { onPaidChange(true) }
                    }
                    if (paid) OutlinedTextField(price, onPriceChange, modifier = Modifier.fillMaxWidth(),
                        label = { Text("本次价格（金币 / 次）") }, suffix = { Text("金币") },
                        singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { if (price.isNotEmpty() && price.toIntOrNull() !in 1..1_000_000) Text("请输入 1 至 1000000 金币") },
                        isError = price.isNotEmpty() && price.toIntOrNull() !in 1..1_000_000)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFFF7F5FB)).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Info, null, Modifier.size(17.dp), tint = LiveStyle.accent)
                        Text(if (paid) "对方同意并接通后收取一次，未接通不扣金币。"
                            else "发起普通视频通话。若对方已设置收费，会先向你确认价格。",
                            color = LiveStyle.muted, fontSize = 12.sp, lineHeight = 19.sp)
                    }
                    if (loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.5.dp, color = LiveStyle.accent)
                        Text("正在确认主播认证…", fontSize = 11.sp, color = LiveStyle.muted)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(.8f).height(46.dp), shape = CircleShape) { Text("取消", color = LiveStyle.muted) }
                        Button(onClick = onStart, enabled = !paid || (approved && price.toIntOrNull() in 1..1_000_000),
                            modifier = Modifier.weight(1.2f).height(46.dp), shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = LiveStyle.accent)) {
                            Icon(Icons.Rounded.Videocam, null, Modifier.size(19.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("发起通话", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCallChoice(title: String, subtitle: String, selected: Boolean, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) LiveStyle.accent else if (enabled) LiveStyle.muted else Color(0xFFAAA5B5)
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(if (selected) LiveStyle.accentSoft else Color(0xFFF6F6F9))
        .selectable(selected = selected, enabled = enabled, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (selected) Icons.Rounded.CheckCircle else if (enabled) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.Lock,
                null, Modifier.size(20.dp), tint = color)
        }
        Text(title, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = if (selected) LiveStyle.accent else Color(0xFFAAA5B5), fontSize = 10.sp, maxLines = 1)
    }
}
