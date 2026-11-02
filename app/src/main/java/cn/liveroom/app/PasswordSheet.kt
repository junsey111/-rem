package cn.liveroom.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordSheet(token: String, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val api = remember { LivePlatformApi() }
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var uncertain by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("修改密码", style = MaterialTheme.typography.titleLarge)
            Text("修改成功后需要重新登录。请记住新密码。", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value = old, onValueChange = { old = it.take(1024); error = null }, label = { Text("原密码") },
                enabled = !busy && !uncertain, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it.take(1024); error = null }, label = { Text("新密码") },
                enabled = !busy && !uncertain, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = repeated, onValueChange = { repeated = it.take(1024); error = null }, label = { Text("再次输入新密码") },
                enabled = !busy && !uncertain, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            if (repeated.isNotEmpty() && repeated != password) Text("两次新密码不一致", color = MaterialTheme.colorScheme.error)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (uncertain) {
                TextButton(onClick = onChanged, modifier = Modifier.fillMaxWidth()) { Text("返回登录并核实") }
            } else Button(onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        api.changePassword(token, old, password)
                        old = ""; password = ""; repeated = ""
                        onChanged()
                    } catch (e: CancellationException) { throw e
                    } catch (e: PlatformHttpException) {
                        if (e.statusCode == 400) error = e.message
                        else { uncertain = true; error = "结果未确认。请返回登录，先用新密码核实；若不成功再用原密码。" }
                    } catch (_: Exception) {
                        uncertain = true
                        error = "连接中断，修改结果未确认。请返回登录，先用新密码核实；若不成功再用原密码。"
                    } finally { busy = false }
                }
            }, enabled = !busy && old.isNotEmpty() && password.isNotEmpty() && password == repeated, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "正在修改…" else "确认修改并重新登录")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
