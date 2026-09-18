package dev.scheucher.ktoy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
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
 * Pad every rendered row to this width so a background colour fills the whole
 * line, and so a multi-colour row alternates across the full width — like
 * pytoy's ROW_WIDTH.
 */
const val ROW_WIDTH = 80

/**
 * One panel row, coloured like pytoy's highlight_row: padded to a fixed width so
 * the colour reaches the edge, and — when several colours apply — its characters
 * alternate through them (character j gets colours[j % n]), giving the same fine
 * per-character stripe pattern. One colour tints the whole row; none leaves it
 * plain. Consecutive same-colour characters are merged into one Text.
 */
@Composable
fun HighlightRow(
    text: String,
    colours: List<Color>,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Black,
) {
    val style = MonoStyle.copy(color = textColor)
    val padded = if (text.length < ROW_WIDTH) text.padEnd(ROW_WIDTH) else text
    Row(modifier = modifier) {
        when {
            colours.isEmpty() -> Text(padded, style = style, softWrap = false, maxLines = 1)
            colours.size == 1 ->
                Text(padded, style = style, softWrap = false, maxLines = 1,
                    modifier = Modifier.background(colours[0]))
            else -> {
                // emit one Text per maximal run of the same colour (with >1
                // colour that's every character, since neighbours always differ)
                val n = colours.size
                for (j in padded.indices) {
                    Text(
                        padded[j].toString(),
                        style = style,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.background(colours[j % n]),
                    )
                }
            }
        }
    }
}
