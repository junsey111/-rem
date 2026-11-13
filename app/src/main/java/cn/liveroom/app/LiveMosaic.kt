package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Five participants use the reference layout: two above, three below. */
internal fun liveMosaicRows(count: Int): List<Int> = when {
    count <= 0 -> emptyList()
    count <= 2 -> listOf(count)
    count == 3 -> listOf(2, 1)
    count == 4 -> listOf(2, 2)
    count == 5 -> listOf(2, 3)
    else -> buildList { var remaining = count; while (remaining > 0) { add(minOf(3, remaining)); remaining -= last() } }
}

@Composable
internal fun <T> LiveMosaic(participants: List<T>, identity: (T) -> Any, onProfile: (T) -> Unit,
    modifier: Modifier = Modifier, tile: @Composable BoxScope.(T) -> Unit) {
    Column(modifier.background(Color(0xFF1D1A28)), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        var offset = 0
        liveMosaicRows(participants.size).forEach { count ->
            val row = participants.subList(offset, offset + count)
            offset += count
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                row.forEach { person -> key(identity(person)) {
                    Box(Modifier.weight(1f).fillMaxHeight().clickable(onClickLabel = "查看主播主页") { onProfile(person) }) { tile(person) }
                } }
            }
        }
    }
}
