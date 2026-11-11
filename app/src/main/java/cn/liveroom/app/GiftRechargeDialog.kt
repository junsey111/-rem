package cn.liveroom.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal fun isInsufficientGiftBalance(error: Exception): Boolean =
    (error as? PlatformHttpException)?.errorCode == "INSUFFICIENT_BALANCE" ||
        error.message?.let { it.contains("余额不足") || it.contains("INSUFFICIENT_BALANCE") || it.contains("INSUFFICIENT_FUNDS") } == true

@Composable
internal fun GiftRechargeDialog(onDismiss: () -> Unit, onRecharge: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFFFCFAFF),
        title = { Text("余额不足，请充值", color = Color(0xFF352742)) },
        text = { Text("当前金币余额不足，联系客服充值后即可继续赠送。", color = Color(0xFF92839E)) },
        confirmButton = { Button(onClick = onRecharge, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7044D8))) { Text("去充值") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF92839E)) } })
}
