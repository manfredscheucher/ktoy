package dev.scheucher.ktoy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/** Monospace text style shared by both panels, so columns line up. */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

/**
 * One full-width panel row. When several highlight colours apply (a byte that is
 * both the PC and the just-written cell), the row background is split into equal
 * vertical segments, one per colour — the on-a-real-UI form of pytoy's
 * multi-colour row. With no colours the row is plain; with one it's a solid tint.
 */
@Composable
fun HighlightRow(
    text: String,
    colours: List<Color>,
    modifier: Modifier = Modifier,
) {
    val bg: Modifier = when (colours.size) {
        0 -> Modifier
        1 -> Modifier.background(colours[0])
        else -> Modifier   // multi-colour drawn as segments below
    }
    Row(modifier = modifier.fillMaxWidth().wrapContentHeight().then(bg)) {
        if (colours.size <= 1) {
            Text(text, style = MonoStyle)
        } else {
            // draw the same text on top of N coloured weighted segments
            SegmentedBackgroundText(text, colours)
        }
    }
}

/**
 * Renders [text] once, but paints [colours] as equal-weight horizontal bands
 * behind it by splitting the string into N contiguous chunks (a simple, robust
 * approximation of pytoy's per-character alternation on a text UI).
 */
@Composable
private fun SegmentedBackgroundText(text: String, colours: List<Color>) {
    val n = colours.size
    val len = text.length
    for (i in 0 until n) {
        val start = i * len / n
        val end = if (i == n - 1) len else (i + 1) * len / n
        Text(
            text.substring(start, end),
            style = MonoStyle,
            modifier = Modifier.background(colours[i]),
        )
    }
}
