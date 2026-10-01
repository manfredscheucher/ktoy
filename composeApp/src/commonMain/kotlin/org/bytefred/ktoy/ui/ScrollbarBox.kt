package org.bytefred.ktoy.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Wraps a vertically-scrolling area and draws a thin scroll-position indicator on
 * the right edge (Compose on Android has no built-in scrollbar). The thumb's
 * height reflects the visible fraction and its position reflects the scroll
 * offset. Purely visual; scrolling is still by touch.
 */
@Composable
fun ScrollbarBox(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        content()

        val max = scrollState.maxValue
        if (max > 0) {
            val trackHeightPx = constraints.maxHeight.toFloat()
            val viewport = trackHeightPx
            val contentHeight = trackHeightPx + max
            val thumbFraction = (viewport / contentHeight).coerceIn(0.08f, 1f)
            val thumbHeightPx = trackHeightPx * thumbFraction
            val travel = trackHeightPx - thumbHeightPx
            val thumbTopPx = travel * (scrollState.value.toFloat() / max)

            with(androidx.compose.ui.platform.LocalDensity.current) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = thumbTopPx.toDp())
                        .width(4.dp)
                        .height(thumbHeightPx.toDp())
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x66000000)),
                )
            }
        }
    }
}
