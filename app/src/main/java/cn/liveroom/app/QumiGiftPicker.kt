package cn.liveroom.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

private val giftAccent = Color(0xFFAB8AF6)
private val giftMuted = Color(0xFF9795A6)
private val giftGold = Color(0xFFF1CF84)

internal val demoGiftCatalog = listOf(
    Gift("ROSE", "玫瑰", 1, 0), Gift("HEART", "爱心泡泡", 52, 0),
    Gift("PANDA", "爱心熊猫", 33, 0), Gift("ROCKET", "爱心火箭", 1314, 0),
    Gift("RING", "爱心钻戒", 3344, 0), Gift("CROWN", "璀璨皇冠", 5200, 0),
    Gift("CASTLE", "梦幻城堡", 5200, 0), Gift("GEM", "海洋之心", 11000, 0),
    Gift("FIREWORK", "星光烟火", 399, 0), Gift("CAKE", "生日派对", 1888, 0),
    Gift("AIRPLANE", "浪漫告白", 520, 0), Gift("BOUQUET", "桃花运", 1314, 0),
)

internal fun giftArt(gift: Gift): Int {
    val key = "${gift.id} ${gift.name}".lowercase()
    return when {
        gift.id.equals("COFFEE", ignoreCase = true) -> R.drawable.gift_coffee
        gift.id.equals("CLOUD", ignoreCase = true) -> R.drawable.gift_cloud
        gift.id.equals("DONUT", ignoreCase = true) -> R.drawable.gift_doughnut
        gift.id.equals("PERFUME", ignoreCase = true) -> R.drawable.gift_perfume
        gift.id.equals("DRUM", ignoreCase = true) -> R.drawable.gift_drum
        gift.id.equals("LION", ignoreCase = true) -> R.drawable.gift_lion
        "rose" in key || "玫瑰" in key -> R.drawable.gift_rose
        "panda" in key || "熊猫" in key -> R.drawable.gift_panda
        "rocket" in key || "火箭" in key -> R.drawable.gift_rocket
        "ring" in key || "钻戒" in key -> R.drawable.gift_ring
        "heart" in key || "爱心" in key -> R.drawable.gift_sparkling_heart
        "crown" in key || "皇冠" in key -> R.drawable.gift_crown
        "castle" in key || "城堡" in key -> R.drawable.gift_castle
        "gem" in key || "钻石" in key || "海洋" in key -> R.drawable.gift_gem_stone
        "firework" in key || "烟火" in key -> R.drawable.gift_fireworks
        "cake" in key || "生日" in key -> R.drawable.gift_birthday_cake
        "airplane" in key || "告白" in key -> R.drawable.gift_airplane
        "bouquet" in key || "桃花" in key -> R.drawable.gift_bouquet
        "donut" in key -> R.drawable.gift_doughnut
        "coffee" in key -> R.drawable.gift_coffee
        "cloud" in key -> R.drawable.gift_cloud
        "perfume" in key -> R.drawable.gift_perfume
        "drum" in key -> R.drawable.gift_drum
        "lion" in key -> R.drawable.gift_lion
        else -> R.drawable.gift_bouquet
    }
}

@Composable
internal fun GiftArtwork(gift: Gift, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val bitmap by produceState<Bitmap?>(null, gift.iconUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val raw = gift.iconUrl.trim()
                val address = if (raw.matches(Regex("^/gift-icons/[A-Za-z0-9_-]{1,32}(\\?v=[0-9]+)?$"))) {
                    BuildConfig.BILLD_API_URL.trimEnd('/') + raw
                } else {
                    val uri = URI(raw)
                    if (raw.length > 512 || uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) return@runCatching null
                    raw
                }
                val connection = URL(address).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    connection.instanceFollowRedirects = false
                    if (connection.responseCode != 200 || connection.contentLengthLong > 2 * 1024 * 1024) return@runCatching null
                    val bytes = connection.inputStream.use { it.readBytesLimited(2 * 1024 * 1024 + 1) }
                    if (bytes.size > 2 * 1024 * 1024) return@runCatching null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return@runCatching null
                    val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 512)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample })
                } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription, modifier, contentScale = ContentScale.Fit)
    else Image(painterResource(giftArt(gift)), contentDescription, modifier, contentScale = ContentScale.Fit)
}

@Composable
internal fun QumiGiftPicker(
    gifts: List<Gift>,
    selectedId: String?,
    balance: Long?,
    busy: Boolean,
    demo: Boolean,
    onSelect: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = gifts.firstOrNull { it.id == selectedId }
    val enough = demo || (balance != null && selected != null && balance >= selected.coinPrice)
    var category by remember(gifts) { mutableIntStateOf(0) }
    val categories = listOf("推荐", "心意", "派对", "典藏")
    val visibleGifts = when (category) {
        1 -> gifts.filter { it.coinPrice < 100 }
        2 -> gifts.filter { it.coinPrice in 100..999 }
        3 -> gifts.filter { it.coinPrice >= 1000 }
        else -> gifts
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF22232F), Color(0xFF171820))))) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("礼物", color = Color(0xFFF7F6FC), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (demo) Text("演示房间", color = giftMuted, fontSize = 11.sp)
            IconButton(onClick = onDismiss, enabled = !busy) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭礼物", tint = Color(0xFFB8B6C5))
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(start = 10.dp, end = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            categories.forEachIndexed { index, label ->
                Column(Modifier.clickable { category = index }.padding(horizontal = 13.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = if (category == index) giftGold else giftMuted,
                        fontSize = 14.sp, fontWeight = if (category == index) FontWeight.Bold else FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.size(width = 24.dp, height = 2.dp)
                        .background(if (category == index) giftGold else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = .08f))
        if (visibleGifts.isEmpty()) Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
            Text("暂无可赠送的礼物", color = giftMuted)
        } else LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().height(250.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(visibleGifts, key = { it.id }) { gift ->
                val selectedGift = gift.id == selectedId
                Column(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(6.dp))
                    .then(if (selectedGift) Modifier.border(1.dp, Color(0xFFD9BD78), RoundedCornerShape(6.dp)) else Modifier)
                    .background(if (selectedGift) Color(0xFF34303E) else Color.Transparent)
                    .clickable(enabled = !busy) { onSelect(gift.id) }
                    .padding(horizontal = 3.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    GiftArtwork(gift, modifier = Modifier.size(61.dp))
                    Text(gift.name, color = Color(0xFFEAE8F0), fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text("${gift.coinPrice} 金币", color = if (selectedGift) giftGold else giftMuted,
                        fontSize = 10.sp)
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = .08f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MonetizationOn, contentDescription = null,
                tint = giftGold, modifier = Modifier.size(19.dp))
            Text(if (demo) " 不扣金币" else " ${balance ?: "--"} 金币",
                color = Color(0xFFF6F4FA), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            val buttonLabel = when {
                busy -> "赠送中"
                selected == null -> "选择礼物"
                !enough -> "余额不足"
                demo -> "演示赠送"
                else -> "赠送 ${selected.coinPrice} 金币"
            }
            Text(buttonLabel,
                Modifier.clip(CircleShape)
                    .background(if (selected != null && enough && !busy) giftAccent else Color(0xFF4A4855))
                    .clickable(enabled = selected != null && !busy, onClick = onSend)
                    .padding(horizontal = 17.dp, vertical = 9.dp),
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
