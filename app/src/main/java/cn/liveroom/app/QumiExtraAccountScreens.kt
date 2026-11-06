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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
internal fun QumiCostumeShopScreen(onBack: () -> Unit) {
    var showingOwned by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableIntStateOf(0) }
    var selectedItem by remember { mutableIntStateOf(0) }
    val category = QumiCostumeCatalog.keys.elementAt(selectedCategory)
    val products = QumiCostumeCatalog.getValue(category)
    val selected = products.getOrNull(selectedItem.coerceIn(0, (products.size - 1).coerceAtLeast(0)))

    Column(Modifier.fillMaxSize().background(CostumeBackground)) {
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White) }
            Text(
                "装扮商城",
                Modifier.weight(1f).clickable { showingOwned = false },
                textAlign = TextAlign.End,
                color = if (!showingOwned) Color.White else CostumeMuted,
                fontSize = 19.sp,
                fontWeight = if (!showingOwned) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                "我的装扮",
                Modifier.weight(1f).clickable { showingOwned = true }.padding(start = 20.dp),
                color = if (showingOwned) Color.White else CostumeMuted,
                fontSize = 19.sp,
                fontWeight = if (showingOwned) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(Modifier.width(24.dp))
        }

        if (showingOwned) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = CostumeMuted, modifier = Modifier.size(46.dp))
                    Text("装扮列表暂不可用", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("当前平台尚未接入装扮库存接口", color = CostumeMuted, fontSize = 14.sp)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(145.dp), contentAlignment = Alignment.Center) {
                if (selected != null) {
                    Image(painterResource(selected.image), contentDescription = selected.name, contentScale = ContentScale.Fit, modifier = Modifier.size(width = 220.dp, height = 145.dp))
                } else {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = CostumeMuted, modifier = Modifier.size(44.dp))
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(CostumePanel)
                    .horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QumiCostumeCatalog.keys.forEachIndexed { index, name ->
                    Column(
                        Modifier.clickable {
                            selectedCategory = index
                            selectedItem = 0
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(name, color = if (selectedCategory == index) Color.White else CostumeMuted, fontSize = 15.sp, fontWeight = if (selectedCategory == index) FontWeight.SemiBold else FontWeight.Normal)
                        Box(Modifier.width(18.dp).height(3.dp).clip(CircleShape).background(if (selectedCategory == index) Color.White else Color.Transparent))
                    }
                }
            }

            if (products.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(CostumePanel), contentAlignment = Alignment.Center) {
                    Text("该分类商品目录暂未接入", color = CostumeMuted, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().background(CostumePanel),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(products.chunked(3)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { inRow, item ->
                                val index = products.indexOf(item)
                                CostumeProductCard(
                                    item = item,
                                    selected = selectedItem == index,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedItem = index },
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().background(Color(0xFF2C245E))) {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFFFF6468), shape = RoundedCornerShape(6.dp)) { Text("参考目录", Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = Color.White, fontSize = 12.sp) }
                    Text("  商品和价格未在本平台上架", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(Modifier.fillMaxWidth().height(92.dp).background(Color(0xFF321958)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(selected?.name ?: category, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(selected?.let { "参考价 ${it.roses} 玫瑰 · 未在本平台上架" } ?: "该分类商品目录尚未接入", color = CostumeMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(onClick = {}, enabled = false, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) { Text("未开放购买") }
                }
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
    if (showSetPassword) AlertDialog(
        onDismissRequest = { showSetPassword = false },
        icon = { Icon(Icons.Rounded.ChildCare, contentDescription = null) },
        title = { Text("设置独立密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("密码仅保存在本机，用于关闭青少年模式。")
                YouthPasswordField("四位数字密码", password) { password = it }
                YouthPasswordField("再次输入密码", confirmPassword) { confirmPassword = it }
                if (error != null) Text(error!!, color = Color(0xFFB4233B), fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = {
            when {
                password.length != 4 -> error = "请输入四位数字密码"
                password != confirmPassword -> error = "两次输入的密码不一致"
                else -> {
                    YouthModePreferences.setPasswordAndEnable(context, accountId, password)
                    mode = YouthModePreferences.load(context, accountId)
                    showSetPassword = false
                }
            }
        }) { Text("开启") } },
        dismissButton = { TextButton(onClick = { showSetPassword = false }) { Text("取消") } },
    )
    if (showDisable) AlertDialog(
        onDismissRequest = { showDisable = false },
        title = { Text("关闭青少年模式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("请输入设置时的四位密码。")
                YouthPasswordField("独立密码", password) { password = it }
                if (error != null) Text(error!!, color = Color(0xFFB4233B), fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = {
            if (YouthModePreferences.disable(context, accountId, password)) {
                mode = YouthModePreferences.load(context, accountId)
                showDisable = false
            } else error = "密码错误，请重试"
        }) { Text("关闭") } },
        dismissButton = { TextButton(onClick = { showDisable = false }) { Text("取消") } },
    )
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

