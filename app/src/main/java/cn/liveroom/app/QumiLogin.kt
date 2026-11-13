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

internal val QumiPurple = Color(0xFF5D43F2)
private val QumiMuted = Color(0xFF968BA8)

@Composable
internal fun QumiLoginFrame(
    smsMode: Boolean,
    registering: Boolean,
    username: String,
    phone: String,
    code: String,
    accountId: String,
    password: String,
    error: String?,
    busy: Boolean,
    codeSending: Boolean,
    codeCooldown: Int,
    onPhone: (String) -> Unit,
    onCode: (String) -> Unit,
    onUsername: (String) -> Unit,
    onAccountId: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit,
    onToggleRegister: () -> Unit,
) {
    var agreed by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val enabled = !busy && !codeSending && agreed && if (smsMode) {
        phone.length == 11 && code.length == 6
    } else {
        (if (registering) username.isNotBlank() else accountId.isNotBlank()) && password.isNotBlank()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF171234))) {
        val pageHeight = maxHeight
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF364F87).copy(alpha = .90f), Color(0xFF364F87).copy(alpha = .48f), Color.Transparent),
                center = Offset(constraints.maxWidth * .18f, constraints.maxHeight * .76f),
                radius = constraints.maxHeight * .66f,
            ),
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF84516F).copy(alpha = .76f), Color(0xFF84516F).copy(alpha = .35f), Color.Transparent),
                center = Offset(constraints.maxWidth * .85f, constraints.maxHeight * .58f),
                radius = constraints.maxHeight * .55f,
            ),
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFFABA6B5).copy(alpha = .82f), Color(0xFFABA6B5).copy(alpha = .32f), Color.Transparent),
                center = Offset(constraints.maxWidth * .52f, constraints.maxHeight * .58f),
                radius = constraints.maxHeight * .45f,
            ),
        ))
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 44.dp),
        ) {
            Spacer(Modifier.height(pageHeight * .12f))
            Image(
                painter = painterResource(R.drawable.qumi_logo),
                contentDescription = "趣蜜",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(26.dp))
            Text("很高兴认识你", color = Color.White, fontSize = 44.sp, lineHeight = 54.sp)
            Text("Nice to meet you", color = Color(0xFF8C80A4), fontSize = 28.sp, lineHeight = 36.sp)
            Spacer(Modifier.height(pageHeight * .078f))

            if (smsMode) {
                QumiInputPill(
                    label = "手机号",
                    value = phone,
                    placeholder = "输入手机号",
                    onValueChange = onPhone,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(25.dp))
                QumiSmsCodePill(
                    code = code,
                    sending = codeSending,
                    cooldown = codeCooldown,
                    enabled = !busy && !codeSending && codeCooldown == 0 && phone.length == 11,
                    onCode = onCode,
                    onSendCode = onSendCode,
                )
            } else {
                if (registering) {
                    QumiInputPill("用户名", username, "输入用户名", onUsername)
                } else {
                QumiInputPill(
                    label = "账号ID",
                    value = accountId,
                    placeholder = "输入平台账号ID",
                    onValueChange = onAccountId,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                }
                Spacer(Modifier.height(25.dp))
                QumiInputPill(
                    label = "密码",
                    value = password,
                    placeholder = "输入密码",
                    onValueChange = onPassword,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }

            if (error != null) {
                Text(error, color = Color(0xFFFFD6DC), fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, start = 8.dp))
            }

            Button(
                onClick = onSubmit,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(top = if (error == null) 71.dp else 24.dp).height(83.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = QumiPurple,
                    disabledContainerColor = QumiPurple.copy(alpha = .5f),
                ),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (smsMode) "登录 / 注册" else if (registering) "注册并登录" else "登录", color = Color.White, fontSize = 28.sp)
            }

            TextButton(
                onClick = onToggleMode,
                enabled = !busy && !codeSending,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) {
                Text(if (smsMode) "使用账号密码登录" else "短信验证码登录", fontSize = 20.sp)
            }
            if (!smsMode) TextButton(
                onClick = onToggleRegister,
                enabled = !busy && !codeSending,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) {
                Text(if (registering) "已有账号？登录" else "创建账号", fontSize = 18.sp)
            }

            Spacer(Modifier.weight(1f, fill = false))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = pageHeight * .125f, bottom = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
                        .background(if (agreed) QumiPurple else Color.Transparent)
                        .clickable { agreed = !agreed },
                    contentAlignment = Alignment.Center,
                ) {
                    if (agreed) Text("✓", color = Color.White, fontSize = 16.sp, lineHeight = 18.sp)
                    else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .12f)))
                }
                Spacer(Modifier.width(14.dp))
                Text("我已经阅读并同意", color = Color(0xFFA59AB9), fontSize = 18.sp, maxLines = 1)
                Text("用户协议", color = Color(0xFFA59AB9), fontSize = 18.sp, modifier = Modifier.clickable { showTerms = true })
                Text("和", color = Color(0xFFA59AB9), fontSize = 18.sp)
                Text("隐私协议", color = Color(0xFFA59AB9), fontSize = 18.sp, modifier = Modifier.clickable { showPrivacy = true })
            }
        }
    }

    if (showTerms || showPrivacy) {
        QumiConfirmSheet(
            title = if (showTerms) "用户协议" else "隐私协议",
            message = "现场 Live 服务协议与隐私说明。登录或注册前请阅读并确认同意。",
            confirmLabel = "同意",
            dismissLabel = "返回",
            onDismiss = { showTerms = false; showPrivacy = false },
            onConfirm = { agreed = true; showTerms = false; showPrivacy = false },
        )
    }
}

@Composable
private fun QumiSmsCodePill(
    code: String,
    sending: Boolean,
    cooldown: Int,
    enabled: Boolean,
    onCode: (String) -> Unit,
    onSendCode: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(83.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = if (focused) .40f else .32f))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("验证码", color = Color(0xFFD5D0DE), fontSize = 17.sp, modifier = Modifier.width(76.dp), maxLines = 1)
        BasicTextField(
            value = code,
            onValueChange = onCode,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = Color.White, fontSize = 24.sp),
            cursorBrush = Brush.verticalGradient(listOf(Color.White, Color.White)),
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (code.isEmpty()) Text("输入验证码", color = Color.White.copy(alpha = .78f), fontSize = 18.sp, maxLines = 1)
                    inner()
                }
            },
        )
        TextButton(
            onClick = onSendCode,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 5.dp),
            modifier = Modifier.height(48.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = .55f),
            ),
        ) {
            Text(
                when {
                    sending -> "发送中"
                    cooldown > 0 -> "${cooldown}s"
                    else -> "获取验证码"
                },
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
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
        Modifier.fillMaxWidth().height(83.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = if (focused) .40f else .32f))
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFFD5D0DE), fontSize = 18.sp, modifier = Modifier.width(104.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            textStyle = TextStyle(color = Color.White, fontSize = 26.sp),
            cursorBrush = Brush.verticalGradient(listOf(Color.White, Color.White)),
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = .78f), fontSize = 24.sp, maxLines = 1)
                    inner()
                }
            },
        )
    }
}
