package org.bytefred.ktoy.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/** Monospace text style shared by every data view, so columns line up. */
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
 * alternate through them (character j gets colours[j % n]), the same fine
 * per-character stripe. One colour tints the whole row; none leaves it plain.
 *
 * The whole row is ONE Text over an AnnotatedString (per-character background
 * SpanStyles), not N per-character Text composables. A single text-layout pass
 * means every row — plain, one-colour, or striped — has identical monospace
 * advance widths, so columns stay aligned (N separate Texts drifted sub-pixel
 * and misaligned the striped rows against the plain ones).
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
    when {
        colours.isEmpty() ->
            Text(padded, style = style, softWrap = false, maxLines = 1, modifier = modifier)
        colours.size == 1 ->
            Text(padded, style = style, softWrap = false, maxLines = 1,
                modifier = modifier.background(colours[0]))
        else -> {
            val n = colours.size
            val annotated: AnnotatedString = buildAnnotatedString {
                for (j in padded.indices) {
                    withStyle(SpanStyle(background = colours[j % n])) {
                        append(padded[j])
                    }
                }
            }
            Text(annotated, style = style, softWrap = false, maxLines = 1, modifier = modifier)
        }
    }
}
