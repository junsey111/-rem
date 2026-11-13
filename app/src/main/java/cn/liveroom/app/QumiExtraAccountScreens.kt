package cn.liveroom.app

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.launch
import java.util.UUID

private val CostumeBackground = Color(0xFF211B4D)
private val CostumePanel = Color(0xFF160A2B)
private val CostumeMuted = Color(0xFF9891B5)
private data class QumiShopItem(val name: String, val roses: Int, @DrawableRes val image: Int)

private val QumiCostumeCatalog = linkedMapOf(
    "头像框" to listOf(
        QumiShopItem("粉红天使", 14, R.drawable.qumi_shop_frame_angel),
        QumiShopItem("晚宝", 15, R.drawable.qumi_shop_frame_night),
        QumiShopItem("第一神豪", 18, R.drawable.qumi_shop_frame_crown),
        QumiShopItem("帝之皇冠", 26, R.drawable.qumi_shop_frame_emperor),
        QumiShopItem("荷鲁斯之眼", 99, R.drawable.qumi_shop_frame_horus),
        QumiShopItem("炫紫天使", 13, R.drawable.qumi_shop_frame_purple),
    ),
    "聊天气泡" to listOf(
        QumiShopItem("紫气东来", 9, R.drawable.qumi_shop_bubble_purple),
        QumiShopItem("夏日浪漫", 66, R.drawable.qumi_shop_bubble_summer),
        QumiShopItem("炫彩皇冠", 18, R.drawable.qumi_shop_bubble_crown),
        QumiShopItem("蓝色流星", 99, R.drawable.qumi_shop_bubble_star),
    ),
    "进场特效" to listOf(
        QumiShopItem("蜜友", 200, R.drawable.qumi_shop_entry_friend),
        QumiShopItem("玫瑰信约", 66, R.drawable.qumi_shop_entry_rose),
        QumiShopItem("赴约男", 9, R.drawable.qumi_shop_entry_date),
        QumiShopItem("独角兽-进场", 16, R.drawable.qumi_shop_entry_unicorn),
        QumiShopItem("甜蜜星境进场", 23, R.drawable.qumi_shop_entry_sweet),
        QumiShopItem("春日进场", 33, R.drawable.qumi_shop_entry_spring),
    ),
    "荣誉座驾" to listOf(
        QumiShopItem("红旗", 149, R.drawable.qumi_shop_car_flag),
        QumiShopItem("灵魂战车", 99, R.drawable.qumi_shop_car_soul),
        QumiShopItem("一鹿有你", 99, R.drawable.qumi_shop_car_deer),
        QumiShopItem("空军一号", 199, R.drawable.qumi_shop_car_air),
        QumiShopItem("花路喵车", 66, R.drawable.qumi_shop_car_cat),
        QumiShopItem("炫酷超跑", 299, R.drawable.qumi_shop_car_sport),
    ),
    "靓号" to emptyList(),
    "勋章" to emptyList(),
    "开通广播" to emptyList(),
    "名片" to emptyList(),
    "头饰" to emptyList(),
)

@Composable
internal fun QumiCostumeShopScreen(session: UserSession, onBack: () -> Unit) {
    val api = remember { LivePlatformApi() }
    var serverItems by remember { mutableStateOf<List<CostumeItemInfo>?>(null) }
    var ownedItems by remember { mutableStateOf<List<OwnedCostume>>(emptyList()) }
    var serverError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.token) {
        runCatching {
            val catalog = api.costumeCatalog(session.token)
            val owned = api.ownedCostumes(session.token)
            catalog to owned
        }.onSuccess { (catalog, owned) ->
            serverItems = catalog
            ownedItems = owned
        }.onFailure {
            serverItems = emptyList()
            serverError = it.message ?: "装扮服务暂不可用"
        }
    }
    if (serverItems != null || serverError != null) {
        QumiServerCostumeScreen(
            session = session,
            items = serverItems!!,
            owned = ownedItems,
            error = serverError,
            api = api,
            onOwnedChange = { ownedItems = it },
            onBack = onBack,
        )
        return
    }
    Column(Modifier.fillMaxSize().background(CostumeBackground)) {
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White) }
            Text("装扮商城", Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(48.dp))
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Color(0xFF18E6D0))
                Text("正在读取平台装扮目录…", color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun CostumeProductCard(item: QumiShopItem, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(CostumePanel)
            .border(if (selected) 1.dp else 0.dp, if (selected) Color(0xFF18E6D0) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Image(painterResource(item.image), contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(94.dp).clip(RoundedCornerShape(8.dp)))
        Text(item.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("参考价 ${item.roses} 玫瑰", color = CostumeMuted, fontSize = 12.sp)
    }
}

@Composable
private fun QumiServerCostumeScreen(
    session: UserSession,
    items: List<CostumeItemInfo>,
    owned: List<OwnedCostume>,
    error: String?,
    api: LivePlatformApi,
    onOwnedChange: (List<OwnedCostume>) -> Unit,
    onBack: () -> Unit,
) {
    var showingOwned by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf(error) }
    var purchaseItem by remember { mutableStateOf<CostumeItemInfo?>(null) }
    var purchaseBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val purchasePrefs = remember(session.id) { context.getSharedPreferences("qumi_catalog_purchases", android.content.Context.MODE_PRIVATE) }
    val categories = remember(items) { listOf(null) + items.map { it.category }.distinct() }
    val visible = selectedCategory?.let { category -> items.filter { it.category == category } } ?: items
    Column(Modifier.fillMaxSize().background(CostumeBackground)) {
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = Color.White) }
            Text("装扮商城", Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { showingOwned = !showingOwned }) { Text(if (showingOwned) "商城" else "我的", color = Color.White) }
        }
        if (notice != null) TextButton(onClick = { notice = null }) { Text(notice!!, color = Color(0xFFFFB4AB), maxLines = 2) }
        if (showingOwned) {
            if (owned.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Info, null, tint = CostumeMuted, modifier = Modifier.size(46.dp))
                    Text("暂无已拥有装扮", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("从商城选择装扮即可购买", color = CostumeMuted, fontSize = 14.sp)
                }
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth().background(CostumePanel), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(owned, key = { it.id }) { ownedItem ->
                    val item = ownedItem.item
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF24134A)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CostumeAssetImage(item?.assetUrl, item?.name ?: "装扮", Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(item?.name ?: "装扮 ${ownedItem.costumeId}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (ownedItem.equipped) "已装备" else item?.category.orEmpty(), color = CostumeMuted, fontSize = 12.sp)
                        }
                        if (!ownedItem.equipped) TextButton(enabled = busyId == null, onClick = {
                            busyId = ownedItem.costumeId
                            scope.launch {
                                runCatching { api.equipCostume(session.token, ownedItem.costumeId) }
                                    .onSuccess { onOwnedChange(owned.map { it.copy(equipped = it.costumeId == ownedItem.costumeId) }) }
                                    .onFailure { notice = it.message ?: "装备失败" }
                                busyId = null
                            }
                        }) { Text(if (busyId == ownedItem.costumeId) "处理中" else "装备") }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().background(CostumePanel).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                categories.forEach { category ->
                    Text(category ?: "全部", Modifier.clickable { selectedCategory = category }, color = if (selectedCategory == category) Color.White else CostumeMuted, fontSize = 14.sp, fontWeight = if (selectedCategory == category) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            if (visible.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().background(CostumePanel), contentAlignment = Alignment.Center) {
                Text("后台尚未上架装扮", color = CostumeMuted, fontSize = 16.sp)
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth().background(CostumePanel), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF24134A)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CostumeAssetImage(item.assetUrl, item.name, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(item.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(item.description.ifBlank { item.category }, color = CostumeMuted, fontSize = 12.sp, maxLines = 2)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (item.priceCoins > 0) "${item.priceCoins} 金币" else "免费", color = Color(0xFFFFD783), fontSize = 12.sp)
                            TextButton(enabled = !purchaseBusy, onClick = { purchaseItem = item }) { Text("购买", color = Color(0xFF18E6D0)) }
                        }
                    }
                }
            }
            Text("购买前会展示价格，扣币和拥有记录均由服务端确认。", Modifier.fillMaxWidth().background(Color(0xFF2C245E)).padding(12.dp), color = CostumeMuted, fontSize = 12.sp)
        }
    }
    purchaseItem?.let { item ->
        QumiConfirmSheet(
            title = "购买${item.name}",
            message = if (item.priceCoins > 0) "将从金币余额扣除 ${item.priceCoins} 金币，购买后可在“我的”中装备。" else "本次购买不扣除金币，确认后立即加入你的装扮。",
            confirmLabel = "确认购买",
            busy = purchaseBusy,
            onDismiss = { if (!purchaseBusy) purchaseItem = null },
            onConfirm = {
                val key = "costume_${item.id}"
                val requestId = purchasePrefs.getString(key, null) ?: UUID.randomUUID().toString().also {
                    purchasePrefs.edit().putString(key, it).commit()
                }
                purchaseBusy = true
                scope.launch {
                    runCatching {
                        api.purchaseCostume(session.token, item.id, requestId)
                        api.ownedCostumes(session.token)
                    }.onSuccess { updated ->
                        onOwnedChange(updated)
                        purchasePrefs.edit().remove(key).apply()
                        purchaseItem = null
                        notice = "${item.name}已加入我的装扮"
                    }.onFailure { failure ->
                        if (failure is PlatformHttpException && failure.statusCode < 500) purchasePrefs.edit().remove(key).apply()
                        notice = failure.message ?: "购买失败，请稍后重试"
                    }
                    purchaseBusy = false
                }
            },
        )
    }
}

@Composable
private fun CostumeAssetImage(url: String?, description: String, modifier: Modifier) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val value = url?.trim().orEmpty()
                require(value.startsWith("https://") || value.startsWith("http://"))
                val connection = URL(value).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 8_000
                    connection.instanceFollowRedirects = false
                    check(connection.responseCode in 200..299)
                    check(connection.contentLengthLong <= 2 * 1024 * 1024L || connection.contentLengthLong < 0)
                    val bytes = connection.inputStream.use { it.readBytesLimited(2 * 1024 * 1024 + 1) }
                    check(bytes.size in 1..(2 * 1024 * 1024))
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap == null) Icon(Icons.Rounded.Info, description, tint = Color(0xFFE4C1FF), modifier = Modifier.size(34.dp))
        bitmap?.let { Image(it.asImageBitmap(), description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
internal fun QumiYouthModeScreen(accountId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by remember(accountId) { mutableStateOf(YouthModePreferences.load(context, accountId)) }
    var showSetPassword by remember { mutableStateOf(false) }
    var showDisable by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun resetDialog() { password = ""; confirmPassword = ""; error = null }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Text("青少年模式", Modifier.weight(1f).padding(end = 48.dp), textAlign = TextAlign.Center, color = Color(0xFF17171A), fontSize = 19.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.qumi_youth_umbrella), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(210.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))
        Text(if (mode.enabled) "青少年模式已开启" else "青少年模式未开启", Modifier.align(Alignment.CenterHorizontally), color = Color(0xFF17171A), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(30.dp))
        Text("青少年模式是本平台为促进青少年健康成长做出的尝试，开启后未成年人将无法使用本平台核心功能", Modifier.fillMaxWidth().padding(horizontal = 24.dp), color = Color(0xFF9296AC), fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
        Button(onClick = {
            resetDialog()
            if (mode.enabled) showDisable = true else showSetPassword = true
        }, Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp), shape = RoundedCornerShape(32.dp)) {
            Text(if (mode.enabled) "关闭青少年模式" else "开启青少年模式", Modifier.padding(vertical = 8.dp), fontSize = 19.sp)
        }
        Text("本机保护模式：设置四位密码后限制本客户端的直播、私信和消费入口。状态不会同步到服务端。", Modifier.fillMaxWidth().padding(horizontal = 24.dp), color = Color(0xFF9296AC), fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1.1f))
    }
    if (showSetPassword) QumiModalSheet(onDismiss = { showSetPassword = false }) {
        QumiSheetTitle("设置独立密码", "密码仅保存在本机，用于关闭青少年模式。")
        YouthPasswordField("四位数字密码", password) { password = it }
        YouthPasswordField("再次输入密码", confirmPassword) { confirmPassword = it }
        error?.let { Text(it, color = Color(0xFFB4233B), fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showSetPassword = false }, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(onClick = {
            when {
                password.length != 4 -> error = "请输入四位数字密码"
                password != confirmPassword -> error = "两次输入的密码不一致"
                else -> {
                    YouthModePreferences.setPasswordAndEnable(context, accountId, password)
                    mode = YouthModePreferences.load(context, accountId)
                    showSetPassword = false
                }
            }
            }, modifier = Modifier.weight(1f)) { Text("开启") }
        }
    }
    if (showDisable) QumiModalSheet(onDismiss = { showDisable = false }) {
        QumiSheetTitle("关闭青少年模式", "请输入设置时的四位密码。")
        YouthPasswordField("独立密码", password) { password = it }
        error?.let { Text(it, color = Color(0xFFB4233B), fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showDisable = false }, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(onClick = {
            if (YouthModePreferences.disable(context, accountId, password)) {
                mode = YouthModePreferences.load(context, accountId)
                showDisable = false
            } else error = "密码错误，请重试"
            }, modifier = Modifier.weight(1f)) { Text("关闭") }
        }
    }
}

@Composable
private fun YouthPasswordField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        colors = TextFieldDefaults.colors(),
    )
}

