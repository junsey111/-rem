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
fun DmCallPriceSheet(session: UserSession, api: LivePlatformApi, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var price by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var approved by remember { mutableStateOf(false) }
    LaunchedEffect(session.id) {
        try { approved = api.creatorApplications(session.token).any { it.status == "APPROVED" }; price = api.myDmCallPrice(session.token).toString() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { message = error.message ?: "暂时无法读取价格" }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("我的视频通话价格", style = MaterialTheme.typography.titleLarge)
            if (!approved) Text("仅认证通过的主播可设置付费视频通话，普通用户通话免费。", color = MaterialTheme.colorScheme.primary)
            Text("0 金币为免费。设置价格后，对方发起视频通话时需要先确认，每次接听只收取一次。语音通话仍免费。")
            OutlinedTextField(value = price, onValueChange = { price = it.filter(Char::isDigit).take(7) }, enabled = approved && !busy,
                label = { Text("每次接听价格（金币）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            message?.let { Text(it) }
            Button(onClick = {
                val coins = price.toIntOrNull()
                if (coins == null || coins !in 0..1_000_000) { message = "请输入 0 至 1000000 金币"; return@Button }
                busy = true
                message = null
                scope.launch {
                    try { api.setDmCallPrice(session.token, coins); message = if (coins == 0) "已设置为免费" else "已设置为每次 $coins 金币" }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { message = error.message ?: "保存失败" }
                    finally { busy = false }
                }
            }, enabled = approved && !busy && price.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "保存中" else "保存") }
        }
    }
}
