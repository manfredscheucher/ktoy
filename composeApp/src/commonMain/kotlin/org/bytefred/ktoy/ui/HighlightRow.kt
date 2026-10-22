package org.bytefred.ktoy.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
import ktoy.composeapp.generated.resources.Res
import ktoy.composeapp.generated.resources.dejavu_sans_mono
import org.jetbrains.compose.resources.Font

/**
 * The monospace style for every data view, provided once at the app root
 * ([ProvideMonoStyle]) so the font is loaded once, not per row. Falls back to
 * FontFamily.Monospace outside the provider (e.g. previews).
 */
val LocalMonoStyle = staticCompositionLocalOf {
    TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
}

/** Monospace text style shared by every data view, so columns line up. */
val MonoStyle: TextStyle
    @Composable get() = LocalMonoStyle.current

/**
 * Provide the bundled DejaVu Sans Mono as [MonoStyle]. FontFamily.Monospace is
 * not reliably monospaced across targets (the web build has no system fonts and
 * falls back to a proportional face, so ">>" and two spaces differ in width);
 * shipping the font makes every data view line up on every platform. License:
 * doc/licenses/LICENSE_DEJAVU.txt.
 */
@Composable
fun ProvideMonoStyle(content: @Composable () -> Unit) {
    val family = FontFamily(Font(Res.font.dejavu_sans_mono))
    val style = remember(family) { TextStyle(fontFamily = family, fontSize = 13.sp) }
    CompositionLocalProvider(LocalMonoStyle provides style, content = content)
}

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
