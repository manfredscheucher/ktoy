package org.bytefred.ktoy.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Inner padding left and right of the text in a [NumberField]. */
private val FIELD_PADDING = 6.dp

/**
 * A compact number input: a low field with a 1 dp grey rounded border, mono text centred
 * vertically, [height] high (as high as the buttons next to it) and [chars] characters wide (the
 * longest allowed value). Material's OutlinedTextField is at least 280 dp wide and 56 dp high,
 * far bigger than a number. The border turns red when [isError]. Used by the steps/sec stepper
 * and the cell editor's Type view.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    chars: Int,
    height: Dp,
    tag: String,
    isError: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val style = MonoStyle
    val textPx = remember(chars, style) { measurer.measure("0".repeat(chars), style).size.width }
    // + 2 dp for the cursor after the last character
    val width = with(LocalDensity.current) { textPx.toDp() } + FIELD_PADDING * 2 + 2.dp
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        modifier = Modifier.width(width).height(height).testTag(tag),
        // centre the text vertically instead of fixed padding, so a larger
        // system font scale doesn't clip it
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize()
                    .border(1.dp, if (isError) MaterialTheme.colorScheme.error else Color(0xFF888888), RoundedCornerShape(4.dp))
                    .padding(horizontal = FIELD_PADDING),
                contentAlignment = Alignment.CenterStart,
            ) { inner() }
        },
    )
}
