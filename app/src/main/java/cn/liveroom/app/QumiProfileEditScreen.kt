package cn.liveroom.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun QumiProfileEditScreen(
    session: UserSession,
    profile: UserProfile?,
    busy: Boolean,
    onBack: () -> Unit,
    onCoverUpload: () -> Unit,
    onSave: (Int, String) -> Unit,
) {
    var age by remember(profile) { mutableStateOf(profile?.age?.toString().orEmpty()) }
    var gender by remember(profile) { mutableStateOf(profile?.gender ?: "UNSPECIFIED") }
    val focusManager = LocalFocusManager.current
    val validAge = age.toIntOrNull() in 1..120
    val canSave = !busy && validAge && gender in listOf("MALE", "FEMALE")
    val pageColor = Color(0xFFF6F7FB)

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(Modifier.fillMaxWidth().height(280.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFFE3DDFF), Color(0xFFD7E9FF))))
            .background(Brush.verticalGradient(listOf(Color.Transparent, pageColor))))
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = LiveStyle.ink)
                }
                Text("个人资料", Modifier.weight(1f), color = LiveStyle.ink,
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Surface(color = Color.White.copy(alpha = .86f), shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = Color.White,
                                border = BorderStroke(3.dp, Color.White), shadowElevation = 2.dp) {
                                QumiUserAvatar(null, session.username, Modifier.padding(3.dp).size(64.dp))
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("个人档案", color = LiveStyle.accent, fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium)
                                Text(session.username, color = LiveStyle.ink, fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("账号 ID  ${session.publicId}", color = LiveStyle.muted, fontSize = 12.sp)
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 18.dp, bottom = 12.dp), color = Color(0xFFEEEAF6))
                        Surface(onClick = onCoverUpload, enabled = !busy, color = Color.Transparent,
                            shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).background(LiveStyle.accentSoft, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Photo, null, tint = LiveStyle.accent, modifier = Modifier.size(21.dp))
                                }
                                Column(Modifier.weight(1f).padding(start = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("设置个人主页", color = LiveStyle.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("更换喜欢的主页背景", color = LiveStyle.muted, fontSize = 11.sp)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = LiveStyle.accent, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Surface(color = Color.White, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).background(LiveStyle.accentSoft, RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Badge, null, tint = LiveStyle.accent, modifier = Modifier.size(19.dp))
                            }
                            Text("基本资料", Modifier.padding(start = 10.dp), color = LiveStyle.ink,
                                fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("年龄", Modifier.padding(top = 22.dp, bottom = 9.dp), color = LiveStyle.ink,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        OutlinedTextField(value = age, onValueChange = { age = it.filter(Char::isDigit).take(3) },
                            enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp), placeholder = { Text("请输入年龄", fontSize = 14.sp) },
                            suffix = { Text("岁", color = LiveStyle.muted, fontSize = 13.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            isError = age.isNotBlank() && !validAge,
                            supportingText = if (age.isNotBlank() && !validAge) ({ Text("请输入 1 至 120 之间的年龄") }) else null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFFAF9FE), unfocusedContainerColor = Color(0xFFFAFAFD),
                                focusedBorderColor = LiveStyle.accent, unfocusedBorderColor = Color(0xFFECEAF3),
                                cursorColor = LiveStyle.accent))
                        Text("性别", Modifier.padding(top = 20.dp, bottom = 10.dp), color = LiveStyle.ink,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProfileGenderOption("♂", "男", Color(0xFF4197F3), gender == "MALE", !busy,
                                Modifier.weight(1f), onClick = { gender = "MALE" })
                            ProfileGenderOption("♀", "女", Color(0xFFF278AC), gender == "FEMALE", !busy,
                                Modifier.weight(1f), onClick = { gender = "FEMALE" })
                        }
                        Row(Modifier.padding(top = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Info, null, tint = Color(0xFFAAA5B8), modifier = Modifier.size(13.dp))
                            Text(if (profile?.source == "SELF_REPORTED") "资料由本人填写，未经身份核验" else "年龄与性别将展示在个人主页",
                                Modifier.padding(start = 5.dp), color = Color(0xFF9993A9), fontSize = 11.sp)
                        }
                    }
                }
                Button(onClick = {
                    focusManager.clearFocus()
                    age.toIntOrNull()?.let { onSave(it, gender) }
                }, enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp)
                    .background(Brush.horizontalGradient(if (canSave) listOf(Color(0xFF9569F3), LiveStyle.accent)
                        else listOf(Color(0xFFE4DEEF), Color(0xFFE4DEEF))), CircleShape),
                    shape = CircleShape, colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                        contentColor = Color.White, disabledContentColor = Color(0xFF9C90AE))) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF9C90AE), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Check, null, modifier = Modifier.size(19.dp))
                    Text(if (busy) "正在保存…" else "保存资料", Modifier.padding(start = 8.dp), fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ProfileGenderOption(symbol: String, label: String, color: Color, selected: Boolean,
    enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = if (selected) color.copy(alpha = .09f) else Color(0xFFFAFAFD),
        border = BorderStroke(1.dp, if (selected) color.copy(alpha = .5f) else Color(0xFFECEAF3))) {
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, color = color, fontSize = 23.sp, fontWeight = FontWeight.Medium)
            Text(label, Modifier.weight(1f).padding(start = 10.dp), color = if (selected) color else LiveStyle.muted,
                fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = color, modifier = Modifier.size(17.dp))
        }
    }
}
