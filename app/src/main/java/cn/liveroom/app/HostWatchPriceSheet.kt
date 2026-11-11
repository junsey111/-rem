package cn.liveroom.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostWatchPriceSheet(session: UserSession, api: LivePlatformApi, liveId: Long, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var price by remember(liveId) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(liveId) {
        try { price = (api.watchAccess(session.token, liveId).unlockPriceCoins ?: 0).toString() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { price = "0"; message = "当前价格暂时无法读取，请核实后再保存" }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("付费观看直播", style = MaterialTheme.typography.titleLarge)
            Text("0 金币为免费直播。设置价格后，每位未解锁的观众可免费试看 60 秒，之后需要支付一次才能继续观看。")
            OutlinedTextField(value = price, onValueChange = { price = it.filter(Char::isDigit).take(7) },
                label = { Text("解锁价格（金币）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            message?.let { Text(it) }
            Button(onClick = {
                val coins = price.toIntOrNull()
                if (coins == null || coins !in 0..1_000_000) { message = "请输入 0 至 1000000 金币"; return@Button }
                busy = true
                message = null
                scope.launch {
                    try { api.setWatchPrice(session.token, liveId, coins); message = if (coins == 0) "已设为免费直播" else "已设置为 $coins 金币，观众可试看 60 秒" }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { message = error.message ?: "保存失败，请稍后重试" }
                    finally { busy = false }
                }
            }, enabled = !busy && price.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "保存中" else "保存观看价格")
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("关闭") }
        }
    }
}
