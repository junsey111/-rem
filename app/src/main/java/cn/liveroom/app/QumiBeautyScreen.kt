package cn.liveroom.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.SurfaceHolder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pedro.library.view.OpenGlView
import kotlin.math.roundToInt

private val beautyPurple = Color(0xFF9874FF)

@Composable
internal fun QumiBeautyScreen(
    settings: CameraBeautySettings,
    onSettingsChange: (CameraBeautySettings) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val foreground = rememberChatForeground()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it; permissionDenied = !it
    }
    LaunchedEffect(foreground) {
        if (foreground) granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    var section by remember { mutableStateOf(BeautySection.SKIN) }
    var skinKey by remember { mutableStateOf("smooth") }
    var shapeKey by remember { mutableStateOf("slim") }
    var bodyKey by remember { mutableStateOf("bodyWaist") }
    var compare by remember { mutableStateOf(false) }
    var publisher by remember { mutableStateOf<SrsIngestPublisher?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var trackingError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(publisher) {
        while (true) { trackingError = publisher?.beautyTrackingError; kotlinx.coroutines.delay(700) }
    }
    val previewSettings = if (compare) settings.copy(enabled = false) else settings
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color(0xFF171522))) {
        if (granted && foreground) BeautyCameraPreview(previewSettings,
            onPublisher = { publisher = it }, onError = { previewError = it }, modifier = Modifier.fillMaxSize())
        if (!granted || previewError != null) Column(Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(previewError ?: "开启摄像头，实时查看美颜效果", color = Color.White, fontSize = 16.sp)
            if (!granted) Button(onClick = {
                if (permissionDenied) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"))) else permission.launch(Manifest.permission.CAMERA)
            }, colors = ButtonDefaults.buttonColors(containerColor = LiveStyle.accent)) {
                Text(if (permissionDenied) "打开权限设置" else "允许使用摄像头")
            }
        }
        Box(Modifier.fillMaxWidth().height(175.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .5f), Color.Transparent))))
        Row(Modifier.statusBarsPadding().fillMaxWidth().height(58.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White) }
            Text("美颜设置", Modifier.weight(1f), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { runCatching { publisher?.flipCamera() }.onFailure { previewError = "摄像头切换失败，请重新进入" } }, enabled = granted) {
                Icon(Icons.Rounded.Cameraswitch, "翻转摄像头", tint = Color.White)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (compare) "原始画面" else "按住对比", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .34f))
                        .pointerInput(Unit) { detectTapGestures(onPress = { compare = true; try { tryAwaitRelease() } finally { compare = false } }) }
                        .padding(horizontal = 14.dp, vertical = 9.dp))
                Spacer(Modifier.weight(1f))
                Text("自然预设", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .34f))
                        .clickable { onSettingsChange(settings.copy(enabled = true, filter = "natural", filterStrength = .45f,
                            amounts = mapOf("smooth" to .35f, "whiten" to .18f, "rosy" to .1f, "slim" to .15f, "bigEyes" to .1f))) }
                        .padding(horizontal = 14.dp, vertical = 9.dp))
            }
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .26f)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BeautySwitch("开启美颜", settings.enabled) { onSettingsChange(settings.copy(enabled = it)) }
                Spacer(Modifier.weight(1f))
                BeautySwitch("高性能模式", settings.highPerformance) { onSettingsChange(settings.copy(highPerformance = it)) }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF5262034), Color(0xF518151F))))) {
                val key = when (section) { BeautySection.SKIN -> skinKey; BeautySection.SHAPE -> shapeKey; BeautySection.BODY -> bodyKey; BeautySection.FILTER -> settings.filter }
                val options = when (section) { BeautySection.SKIN -> beautySkinOptions; BeautySection.SHAPE -> beautyShapeOptions; BeautySection.BODY -> beautyBodyOptions; BeautySection.FILTER -> beautyFilterOptions }
                val selected = options.firstOrNull { it.key == key } ?: options.first()
                val value = if (section == BeautySection.FILTER) settings.filterStrength else settings.amount(key)
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.title + if (section == BeautySection.FILTER) " · 强度" else "", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (section == BeautySection.FILTER && key == "original") "原图" else "${(value * 100).roundToInt()}",
                        Modifier.clip(RoundedCornerShape(8.dp)).background(beautyPurple.copy(alpha = .13f))
                            .padding(horizontal = 9.dp, vertical = 3.dp), color = Color(0xFFCDB9FF), fontSize = 12.sp)
                }
                Slider(value = value, onValueChange = {
                    onSettingsChange(if (section == BeautySection.FILTER) settings.copy(filterStrength = it) else settings.withAmount(key, it))
                }, enabled = settings.enabled && !(section == BeautySection.FILTER && key == "original"),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(38.dp),
                    colors = SliderDefaults.colors(thumbColor = beautyPurple, activeTrackColor = beautyPurple,
                        inactiveTrackColor = Color.White.copy(alpha = .35f), disabledInactiveTrackColor = Color.White.copy(alpha = .15f)))
                LazyRow(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(key = "reset") {
                        BeautyOptionTile("恢复", active = false, modified = false,
                            onClick = { onSettingsChange(settings.reset(section)) }) {
                            Icon(Icons.Rounded.RestartAlt, "恢复${section.title}", tint = Color(0xFFB9B1CC), modifier = Modifier.size(26.dp))
                        }
                    }
                    items(options, key = { it.key }) { option ->
                        val active = option.key == key
                        val modified = if (section == BeautySection.FILTER) option.key == settings.filter && option.key != "original" else settings.amount(option.key) > 0f
                        BeautyOptionTile(option.title, active, modified, onClick = {
                            when (section) {
                                BeautySection.SKIN -> skinKey = option.key
                                BeautySection.SHAPE -> shapeKey = option.key
                                BeautySection.BODY -> bodyKey = option.key
                                BeautySection.FILTER -> onSettingsChange(settings.copy(filter = option.key))
                            }
                        }) {
                            if (section == BeautySection.FILTER) Image(painterResource(R.drawable.live_placeholder_1), null,
                                Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = beautyThumbnailFilter(option.key))
                            else BeautyEffectIcon(option.key, active)
                        }
                    }
                }
                if (section == BeautySection.SHAPE) Text("正对镜头，美型随人脸生效", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                    color = Color.White.copy(alpha = .42f), fontSize = 10.sp)
                if (section == BeautySection.BODY) Text("露出肩部和胯部，瘦腿需腿部入镜", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                    color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
                trackingError?.let { Text(it, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp), color = Color(0xFFFFCBCE), fontSize = 11.sp) }
                HorizontalDivider(color = Color.White.copy(alpha = .1f))
                Row(Modifier.fillMaxWidth().navigationBarsPadding().height(55.dp), verticalAlignment = Alignment.CenterVertically) {
                    BeautySection.entries.forEach { tab ->
                        Column(Modifier.weight(1f).fillMaxHeight().clickable { section = tab }, horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text(tab.title, color = if (section == tab) beautyPurple else Color.White, fontSize = 16.sp,
                                fontWeight = if (section == tab) FontWeight.SemiBold else FontWeight.Normal)
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.size(18.dp, 3.dp).background(if (section == tab) beautyPurple else Color.Transparent, CircleShape))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BeautySwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp)
        Switch(checked = value, onCheckedChange = onChange, modifier = Modifier.size(48.dp, 40.dp).scale(.72f),
            colors = SwitchDefaults.colors(checkedThumbColor = beautyPurple, checkedTrackColor = Color(0xFF55418D),
                uncheckedTrackColor = Color.White.copy(alpha = .2f), uncheckedThumbColor = Color(0xFFB1ADBE), uncheckedBorderColor = Color.Transparent))
    }
}

@Composable
private fun BeautyOptionTile(title: String, active: Boolean, modified: Boolean, onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    Column(Modifier.width(62.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(54.dp).clip(shape)
            .background(Brush.linearGradient(if (active) listOf(Color(0xFF6945B0), Color(0xFF3C2B60))
                else listOf(Color(0xFF393342), Color(0xFF2C2735))))
            .border(if (active) 1.5.dp else .8.dp, if (active) Color(0xFFB998FF) else Color.White.copy(alpha = .12f), shape),
            contentAlignment = Alignment.Center) {
            content()
            if (modified) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(5.dp)
                .background(if (active) Color(0xFFE7DAFF) else beautyPurple, CircleShape))
            // Draw after a filter thumbnail so its selected frame stays visible.
            if (active) Box(Modifier.matchParentSize().border(1.5.dp, Color(0xFFB998FF), shape))
        }
        Text(title, Modifier.padding(top = 7.dp), color = if (active) Color(0xFFCDB9FF) else Color.White.copy(alpha = .76f),
            fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
    }
}

private fun beautyThumbnailFilter(key: String): ColorFilter {
    val matrix = ColorMatrix()
    if (key == "gray") matrix.setToSaturation(.25f)
    else if (key == "mono") matrix.setToSaturation(0f)
    else if (key == "film") matrix.setToSaturation(.78f)
    val tint = when (key) {
        "cool" -> floatArrayOf(.97f, 1f, 1.05f, 0f)
        "warm" -> floatArrayOf(1.06f, 1.015f, .94f, 3f)
        "pink" -> floatArrayOf(1.055f, .98f, 1.025f, 3f)
        "bright" -> floatArrayOf(1.06f, 1.05f, 1.05f, 6f)
        "sepia" -> floatArrayOf(1.08f, 1f, .8f, 3f)
        "vignette" -> floatArrayOf(.88f, .88f, .88f, 0f)
        else -> floatArrayOf(1f, 1f, 1f, 0f)
    }
    val tone = ColorMatrix(floatArrayOf(tint[0], 0f, 0f, 0f, tint[3], 0f, tint[1], 0f, 0f, tint[3],
        0f, 0f, tint[2], 0f, tint[3], 0f, 0f, 0f, 1f, 0f))
    matrix.timesAssign(tone)
    return ColorFilter.colorMatrix(matrix)
}

@Composable
private fun BeautyCameraPreview(settings: CameraBeautySettings, onPublisher: (SrsIngestPublisher?) -> Unit,
    onError: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val publisher = remember { SrsIngestPublisher(context, settings) }
    val latestError by rememberUpdatedState(onError)
    DisposableEffect(publisher) { onPublisher(publisher); onDispose { publisher.stop(); onPublisher(null) } }
    SideEffect { publisher.updateBeauty(settings) }
    AndroidView(factory = { viewContext ->
        OpenGlView(viewContext).apply {
            keepScreenOn = true
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    runCatching { publisher.startLocalPreview(this@apply) }.onFailure { latestError(it.message ?: "摄像头预览失败") }
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            })
        }
    }, onRelease = { publisher.stop() }, modifier = modifier)
}
