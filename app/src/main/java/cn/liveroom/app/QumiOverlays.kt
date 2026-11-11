package cn.liveroom.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val QumiOverlaySurface = LiveStyle.sheetSurface
private val QumiOverlayInk = LiveStyle.ink
private val QumiOverlayMuted = LiveStyle.muted
private val QumiOverlayPrimary = LiveStyle.accent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QumiModalSheet(
    onDismiss: () -> Unit,
    dismissEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { dismissEnabled || it != SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = { if (dismissEnabled) onDismiss() },
        sheetState = state,
        containerColor = QumiOverlaySurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Spacer(Modifier.size(width = 28.dp, height = 4.dp).background(LiveStyle.muted, RoundedCornerShape(4.dp)))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = { content() },
        )
    }
}

@Composable
internal fun QumiSheetTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = QumiOverlayInk, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, color = QumiOverlayMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun QumiConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = "取消",
    busy: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    QumiModalSheet(onDismiss = onDismiss, dismissEnabled = !busy) {
        QumiSheetTitle(title, message)
        HorizontalDivider(color = LiveStyle.line)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) { Text(dismissLabel) }
            Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.weight(1f)) { Text(if (busy) "处理中…" else confirmLabel) }
        }
    }
}
