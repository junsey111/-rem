package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Shared form for the captured profile entry and real platform-user reports. */
@Composable
internal fun QumiReportScreen(
    content: String,
    onContent: (String) -> Unit,
    busy: Boolean = false,
    error: String? = null,
    pending: Boolean = false,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onRetry: () -> Unit = {},
) {
    var category by rememberSaveable { mutableStateOf("其他") }
    var imageNotice by remember { mutableStateOf(false) }
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = android.graphics.Color.WHITE
            window.navigationBarColor = android.graphics.Color.WHITE
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }
    val purple = Color(0xFF5943ED)
    val pale = Color(0xFFF7F7F7)
    Box(Modifier.fillMaxSize().background(Color.White).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(92.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !busy, modifier = Modifier.padding(start = 19.dp).size(48.dp)) {
                    Icon(Icons.Rounded.ArrowBack, "返回", tint = Color(0xFF202020))
                }
                Text("举报", Modifier.weight(1f), fontSize = 32.sp, color = Color.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.width(67.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 48.dp).padding(top = 39.dp, bottom = 120.dp)) {
                Text("举报类型", fontSize = 28.sp, color = Color.Black)
                Spacer(Modifier.height(19.dp))
                listOf(listOf("其他", "传播谣言", "盗用版权"), listOf("政治暴恐", "诈骗", "恶意骚扰")).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { label ->
                            Box(Modifier.weight(1f).height(67.dp).background(if (category == label) purple else pale, CircleShape)
                                .selectable(selected = category == label, enabled = !busy && !pending, role = Role.RadioButton) { category = label }, contentAlignment = Alignment.Center) {
                                Text(label, fontSize = 27.sp, color = if (category == label) Color.White else Color(0xFFD0D0D0))
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                Spacer(Modifier.height(55.dp))
                Text("举报内容", fontSize = 28.sp, color = Color.Black)
                Spacer(Modifier.height(5.dp))
                BasicTextField(
                    value = content, onValueChange = { if (it.length <= 900) onContent(it) }, enabled = !busy && !pending,
                    textStyle = TextStyle(color = Color(0xFF333333), fontSize = 26.sp, lineHeight = 34.sp),
                    modifier = Modifier.fillMaxWidth().height(233.dp).background(pale, RoundedCornerShape(18.dp)).padding(horizontal = 30.dp, vertical = 24.dp),
                    decorationBox = { field ->
                        if (content.isEmpty()) Text("详细说明举报内容", color = Color(0xFFD0D0D0), fontSize = 26.sp)
                        field()
                    },
                )
                Spacer(Modifier.height(60.dp))
                Text("图片", fontSize = 28.sp, color = Color.Black)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(162.dp, 170.dp).background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                    .clickable(enabled = !busy && !pending) { imageNotice = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, "添加举报图片", tint = Color(0xFF9D9DA7), modifier = Modifier.size(32.dp))
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 33.dp).padding(bottom = 125.dp)) {
            error?.let { Text(it, Modifier.fillMaxWidth().background(Color.White).padding(10.dp), color = MaterialTheme.colorScheme.error, fontSize = 16.sp) }
            if (pending) Text("有申请待核实，将使用原单号确认结果。", color = Color(0xFF555555), fontSize = 16.sp)
            Button(
                enabled = !busy, onClick = { if (pending) onRetry() else onSubmit(category) },
                shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = purple),
                modifier = Modifier.fillMaxWidth().height(73.dp),
            ) { Text(if (busy) "提交中…" else if (pending) "核实原申请" else "提交", fontSize = 32.sp) }
        }
    }
    if (imageNotice) AlertDialog(
        onDismissRequest = { imageNotice = false }, title = { Text("图片证据") },
        text = { Text("举报图片上传暂未接入，请先在内容中描述问题。") },
        confirmButton = { TextButton(onClick = { imageNotice = false }) { Text("知道了") } },
    )
}

@Composable
internal fun QumiSystemReportDialog(onDismiss: () -> Unit) {
    var content by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        QumiReportScreen(content, { content = it }, error = error, onBack = onDismiss, onSubmit = {
            error = "小秘书为系统展示账号，没有可举报的平台用户 ID。"
        })
    }
}

@Composable
internal fun QumiProfileMoreDialog(onDismiss: () -> Unit, onReport: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setGravity(android.view.Gravity.BOTTOM)
                window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))) {
                Box(Modifier.fillMaxWidth().height(95.dp).clickable(onClick = onReport), contentAlignment = Alignment.Center) {
                    Text("举报", color = Color(0xFF333333), fontSize = 32.sp)
                }
                Spacer(Modifier.fillMaxWidth().height(13.dp).background(Color(0xFFF5F5F5)))
                Box(Modifier.fillMaxWidth().height(95.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("取消", color = Color(0xFF333333), fontSize = 32.sp)
                }
            }
    }
}
