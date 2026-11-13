package cn.liveroom.app

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveAppTheme(colorScheme: ColorScheme = LiveStyle.colors, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, shapes = LiveStyle.shapes) {
        CompositionLocalProvider(
            LocalIndication provides NoTapHighlight,
            LocalRippleConfiguration provides null,
            content = content,
        )
    }
}

// Apply the same interaction appearance to every build variant and nested app theme.
private object NoTapHighlight : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() = drawContent()
        }

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0
}
