package cn.liveroom.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val createRoomPurple = Color(0xFF7951ED)
private val createRoomBackgrounds = listOf(
    listOf(Color(0xFF5A2AA7), Color(0xFF372268)),
    listOf(Color(0xFF162841), Color(0xFF241A37)),
    listOf(Color(0xFF2855A6), Color(0xFF17193E)),
    listOf(Color(0xFF8B5396), Color(0xFF352052)),
)

internal fun roomBackgroundBrush(index: Int): Brush =
    Brush.verticalGradient(createRoomBackgrounds[index.coerceIn(createRoomBackgrounds.indices)])

@Composable
internal fun QumiCreateRoomScreen(token: String, username: String, api: LivePlatformApi,
    starting: Boolean, onBack: () -> Unit, onBeauty: () -> Unit,
    onCreate: (RoomCreateConfig) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var roomType by remember { mutableStateOf("SINGLE") }
    var title by remember(username) { mutableStateOf("${username} 的直播间") }
    var welcomeText by remember { mutableStateOf("欢迎来到直播间") }
    var paidWatching by remember { mutableStateOf(false) }
    var watchPrice by remember { mutableStateOf("10") }
    var backgroundId by remember { mutableIntStateOf(0) }
    var coverPath by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(token) { coverPath = runCatching { api.myLiveCover(token) }.getOrNull() }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            uploading = true
            uploadError = null
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(3 * 1024 * 1024 + 1) }
                        ?: error("无法读取图片")
                }
                coverPath = api.uploadCover(token, bytes)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { uploadError = failure.message ?: "封面上传失败" }
            finally { uploading = false }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        Color(0xFF171332), Color(0xFF2E235B), Color(0xFF49376F), Color(0xFF17132F),
    )))) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !starting) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White)
                }
                Text("创建房间", Modifier.weight(1f).padding(end = 48.dp),
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf("SINGLE" to "单人间", "MULTI" to "多人间", "VOICE" to "语音房").forEach { (type, label) ->
                        val selected = roomType == type
                        Box(Modifier.weight(1f).height(45.dp).clip(RoundedCornerShape(11.dp))
                            .background(if (selected) Brush.horizontalGradient(listOf(Color(0xFFA648ED), Color(0xFF7064F7)))
                                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = .14f), Color.White.copy(alpha = .14f))))
                            .clickable { roomType = type }, contentAlignment = Alignment.Center) {
                            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("房间名称", color = Color.White.copy(alpha = .66f), fontSize = 13.sp)
                    BasicTextField(title, onValueChange = { title = it.take(80) }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner -> Box {
                            if (title.isBlank()) Text("给房间起个名字", color = Color.White.copy(alpha = .45f), fontSize = 22.sp)
                            inner()
                        } })
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("房间封面", color = Color.White.copy(alpha = .66f), fontSize = 13.sp)
                    Box(Modifier.size(108.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = .16f))
                        .clickable(enabled = !uploading && !starting) { coverPicker.launch("image/*") },
                        contentAlignment = Alignment.Center) {
                        Image(androidx.compose.ui.res.painterResource(R.drawable.live_placeholder_1), null,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        coverPath?.let { LiveCoverImage(it) }
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Color.Black.copy(alpha = .55f)).padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center) {
                            Text(if (uploading) "上传中" else "更换封面", color = Color.White, fontSize = 12.sp)
                        }
                        if (uploading) CircularProgressIndicator(Modifier.size(30.dp), color = Color.White)
                    }
                    uploadError?.let { Text(it, color = Color(0xFFFFC7D0), fontSize = 12.sp) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("欢迎语", color = Color.White.copy(alpha = .66f), fontSize = 13.sp)
                    BasicTextField(welcomeText, onValueChange = { welcomeText = it.take(120) },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                            .background(Color.White.copy(alpha = .13f)).padding(horizontal = 14.dp, vertical = 16.dp),
                        decorationBox = { inner -> Box {
                            if (welcomeText.isBlank()) Text("进入直播间时显示的欢迎语", color = Color.White.copy(alpha = .45f), fontSize = 15.sp)
                            inner()
                        } })
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("房间背景", color = Color.White.copy(alpha = .66f), fontSize = 13.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        createRoomBackgrounds.forEachIndexed { index, colors ->
                            Box(Modifier.width(96.dp).height(122.dp).clip(RoundedCornerShape(12.dp))
                                .background(Brush.verticalGradient(colors))
                                .border(if (backgroundId == index) 2.dp else 0.dp,
                                    if (backgroundId == index) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { backgroundId = index }, contentAlignment = Alignment.BottomCenter) {
                                Text(if (backgroundId == index) "已使用" else "使用背景",
                                    Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(5.dp))
                                        .background(Color.Black.copy(alpha = .37f)).padding(horizontal = 7.dp, vertical = 3.dp),
                                    color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = .1f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("观看方式", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(false to "免费观看", true to "付费观看").forEach { (paid, label) ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                                .background(if (paidWatching == paid) createRoomPurple else Color.White.copy(alpha = .1f))
                                .clickable(enabled = !starting) { paidWatching = paid }.padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontSize = 14.sp) }
                        }
                    }
                    if (paidWatching) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("本场价格", color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
                            BasicTextField(watchPrice, { value -> if (value.length <= 7 && value.all(Char::isDigit)) watchPrice = value },
                                enabled = !starting, singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = .12f)).padding(12.dp))
                            Text("金币", color = Color.White, fontSize = 14.sp)
                        }
                        Text("观众可免费试看 60 秒，支付后可观看本场直播。价格在开播前确定。", color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
                        if (watchPrice.toIntOrNull() !in 1..1_000_000) Text("请输入 1～1000000 金币", color = Color(0xFFFFC7D0), fontSize = 12.sp)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = {
                    onCreate(RoomCreateConfig(title.trim(), roomType, welcomeText.trim(), backgroundId, coverPath,
                        if (paidWatching) watchPrice.toInt() else 0))
                }, enabled = title.isNotBlank() && !uploading && !starting && (!paidWatching || watchPrice.toIntOrNull() in 1..1_000_000),
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = createRoomPurple),
                    contentPadding = PaddingValues(0.dp)) {
                    Text(if (starting) "正在开播…" else "创建房间", fontSize = 17.sp)
                }
                if (roomType == "SINGLE") TextButton(onClick = onBeauty, enabled = !starting) {
                    Icon(Icons.Rounded.AutoFixHigh, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("美颜设置", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
