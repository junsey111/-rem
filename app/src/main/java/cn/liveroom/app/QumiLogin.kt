package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val QumiPurple = Color(0xFF7044D8)
private val QumiMuted = LiveStyle.muted

@Composable
internal fun QumiLoginFrame(
    registering: Boolean,
    codeLogin: Boolean,
    account: String,
    username: String,
    password: String,
    email: String,
    emailCode: String,
    codeBusy: Boolean,
    codeCooldown: Int,
    error: String?,
    busy: Boolean,
    onAccount: (String) -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onEmail: (String) -> Unit,
    onEmailCode: (String) -> Unit,
    onToggleLoginMode: () -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit,
    onToggleRegister: () -> Unit,
) {
    var agreed by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showAgreementReminder by remember { mutableStateOf(false) }
    val enabled = !busy && when {
        registering -> username.isNotBlank() && password.isNotBlank() && email.contains('@') && emailCode.length == 6
        codeLogin -> email.contains('@') && emailCode.length == 6
        else -> account.isNotBlank() && password.isNotBlank()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(LiveStyle.stage)) {
        val pageHeight = maxHeight
        Image(painterResource(R.drawable.qumi_login_background), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color(0xFF170B2C).copy(alpha = .62f),
            .38f to Color(0xFF170B2C).copy(alpha = .38f),
            1f to LiveStyle.stage.copy(alpha = .94f),
        )))
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(pageHeight * if (registering) .06f else .10f))
            Text("现场", color = Color.White, fontSize = 38.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("遇见此刻", color = Color.White.copy(alpha = .83f), fontSize = 16.sp)
            Spacer(Modifier.height(pageHeight * if (registering) .08f else .17f))
            Text(if (registering) "创建账号" else "欢迎回来", color = Color.White,
                fontSize = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            if (!registering) {
                Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(8.dp))
                    .background(LiveStyle.stageSurface.copy(alpha = .82f)).padding(3.dp)) {
                    listOf(false to "账号密码", true to "邮箱验证码").forEach { (mode, title) ->
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                            .background(if (codeLogin == mode) QumiPurple else Color.Transparent)
                            .clickable(enabled = codeLogin != mode) { onToggleLoginMode() },
                            contentAlignment = Alignment.Center) {
                            Text(title, color = if (codeLogin == mode) Color.White else Color.White.copy(alpha = .72f), fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            if (registering) {
                QumiInputPill("用户名", username, "输入用户名", onUsername)
                Spacer(Modifier.height(16.dp))
                QumiInputPill("邮箱", email, "输入邮箱地址", onEmail,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            } else if (codeLogin) {
                QumiInputPill("邮箱", email, "输入已绑定的邮箱", onEmail,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            } else {
                QumiInputPill(
                    label = "账号",
                    value = account,
                    placeholder = "输入用户名",
                    onValueChange = onAccount,
                )
            }
            Spacer(Modifier.height(16.dp))
            if (codeLogin || registering) {
                Row(Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(8.dp))
                    .background(LiveStyle.stageSurface.copy(alpha = .86f)).padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("验证码", color = Color.White.copy(alpha = .68f), fontSize = 14.sp, modifier = Modifier.width(72.dp))
                    BasicTextField(emailCode, onEmailCode, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                        cursorBrush = Brush.verticalGradient(listOf(Color.White, Color.White)),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (emailCode.isEmpty()) Text("6位数字", color = Color.White.copy(alpha = .5f), fontSize = 15.sp)
                                inner()
                            }
                        })
                    TextButton(onClick = onSendCode, enabled = !codeBusy && codeCooldown == 0 && email.contains('@')) {
                        Text(if (codeBusy) "发送中" else if (codeCooldown > 0) "${codeCooldown}s" else "获取验证码",
                            color = if (codeBusy || codeCooldown > 0) Color.White.copy(alpha = .5f) else Color(0xFFD1B9FF),
                            fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
            if (registering) Spacer(Modifier.height(16.dp))
            if (registering || !codeLogin) {
                QumiInputPill(
                    label = "密码",
                    value = password,
                    placeholder = "输入密码",
                    onValueChange = onPassword,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }

            if (error != null) {
                Text(error, color = Color(0xFFFFC5CB), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }

            Button(
                onClick = {
                    if (agreed) onSubmit() else showAgreementReminder = true
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = QumiPurple,
                    disabledContainerColor = QumiPurple.copy(alpha = .4f),
                ),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (registering) "注册并登录" else "登录", color = Color.White, fontSize = 17.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }

            TextButton(
                onClick = onToggleRegister,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) {
                Text(if (registering) "已有账号？登录" else "创建新账号", fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (agreed) QumiPurple else Color.Transparent)
                        .clickable { agreed = !agreed },
                    contentAlignment = Alignment.Center,
                ) {
                    if (agreed) Text("✓", color = Color.White, fontSize = 15.sp, lineHeight = 17.sp)
                    else Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .22f)))
                }
                Spacer(Modifier.width(9.dp))
                Text("已阅读并同意", color = Color.White.copy(alpha = .65f), fontSize = 13.sp)
                Text("用户协议", color = Color.White, fontSize = 13.sp, modifier = Modifier.clickable { showTerms = true })
                Text("和", color = Color.White.copy(alpha = .65f), fontSize = 13.sp)
                Text("隐私协议", color = Color.White, fontSize = 13.sp, modifier = Modifier.clickable { showPrivacy = true })
            }
        }
    }

    if (showAgreementReminder) {
        AlertDialog(
            onDismissRequest = { showAgreementReminder = false },
            title = { Text("请先同意协议") },
            text = { Text("请向下滑动，阅读并勾选页面底部的用户协议和隐私协议，再继续${if (registering) "注册" else "登录"}。") },
            confirmButton = {
                TextButton(onClick = { showAgreementReminder = false; showTerms = true }) { Text("查看用户协议") }
            },
            dismissButton = {
                TextButton(onClick = { showAgreementReminder = false }) { Text("知道了") }
            },
        )
    }

    if (showTerms || showPrivacy) {
        AlertDialog(
            onDismissRequest = { showTerms = false; showPrivacy = false },
            title = { Text(if (showTerms) "用户协议" else "隐私协议") },
            text = { Text("现场服务协议与隐私说明。登录或注册前请阅读并确认同意。") },
            confirmButton = {
                TextButton(onClick = { agreed = true; showTerms = false; showPrivacy = false }) { Text("同意") }
            },
            dismissButton = {
                TextButton(onClick = { showTerms = false; showPrivacy = false }) { Text("返回") }
            },
        )
    }
}

@Composable
private fun QumiInputPill(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(8.dp))
            .background(LiveStyle.stageSurface.copy(alpha = if (focused) .96f else .86f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White.copy(alpha = .68f), fontSize = 14.sp, modifier = Modifier.width(72.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
            cursorBrush = Brush.verticalGradient(listOf(Color.White, Color.White)),
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = .5f), fontSize = 16.sp, maxLines = 1)
                    inner()
                }
            },
        )
    }
}
