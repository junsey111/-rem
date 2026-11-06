package cn.liveroom.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared 4dp layout grid for all consumer surfaces. */
internal object QumiGrid {
    val pagePadding = PaddingValues(horizontal = 16.dp)
    val sectionGap = 16.dp
    val itemGap = 8.dp
    val rowHeight = 72.dp
    val compactRowHeight = 56.dp
}

@Composable
internal fun QumiGridRow(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QumiGrid.itemGap),
        content = content,
    )
}

internal fun Modifier.qumiPageGrid(): Modifier = padding(QumiGrid.pagePadding)
