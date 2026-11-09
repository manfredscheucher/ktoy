package org.bytefred.ktoy.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * Edit the C or assembly source: a multiline text field prefilled with the
 * current text plus Save / Cancel. Save only hands the text to [onSave] (stored
 * as a draft); building it is a separate step (C -> Compile, Assembly ->
 * Assemble). pytoy's EditCDialog, without the build on Save.
 */
@Composable
fun EditSourceDialog(
    title: String,
    label: String,
    initial: String,
    buildLabel: String? = null,
    singleLine: Boolean = false,
    firstLineNumber: Int = 1,
    check: (String) -> String? = { null },
    onSave: (String) -> Unit,
    onBuild: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(initial)) }
    val text = field.text
    var error by remember { mutableStateOf<String?>(null) }

    fun save(build: Boolean) {
        error = check(text)
        if (error == null) { onSave(text); onDismiss(); if (build) onBuild() }
    }

    @Composable
    fun Buttons() {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        TextButton(onClick = { save(false) }) { Text("Save") }
        if (buildLabel != null) TextButton(onClick = { save(true) }) { Text("Save & $buildLabel") }
    }

    if (singleLine) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it; error = null },
                    label = { Text(label) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    textStyle = MonoStyle,
                    modifier = Modifier.testTag("edit-source"),
                )
            },
            confirmButton = { Row { Buttons() } },
        )
        return
    }
    BigDialog(title, onDismiss, buttons = { Buttons() }) {
        // The field grows with its text and the box around it scrolls both ways, like
        // the code panels: a text field's own scrolling has no scrollbars. The error
        // stays outside, in view.
        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()
        FollowCursor(field, firstLineNumber, vScroll, hScroll)
        // the field fills the box at least, so it doesn't sit small in a big dialog
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val minW = maxWidth - 8.dp
        val minH = maxHeight - 8.dp
        ScrollbarBox(vScroll, Modifier.fillMaxSize(), hScroll) {
            Box(Modifier.verticalScroll(vScroll).horizontalScroll(hScroll).padding(end = 8.dp, bottom = 8.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it; error = null },
                    label = { Text(label) },
                    isError = error != null,
                    textStyle = MonoStyle,
                    visualTransformation = LineNumbers(firstLineNumber),
                    modifier = Modifier.widthIn(min = minW).heightIn(min = minH).testTag("edit-source"),
                )
            }
        }
        }
        error?.let { Text(it, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * Keeps the cursor in view: the scrolling box around the field doesn't follow the
 * arrow keys by itself. The lines don't wrap and the font is monospace, so the
 * cursor's place follows from its line and column.
 */
@Composable
private fun FollowCursor(field: TextFieldValue, firstLineNumber: Int, vScroll: ScrollState, hScroll: ScrollState) {
    val glyph = rememberTextMeasurer().measure("0", MonoStyle).size
    val padding = with(LocalDensity.current) { 16.dp.roundToPx() }
    val end = field.selection.end
    LaunchedEffect(end, field.text) {
        withFrameNanos { }   // after the layout of the new text, so the scroll range fits it
        val before = field.text.take(end)
        val line = before.count { it == '\n' }
        val lastLine = firstLineNumber + field.text.count { it == '\n' }
        val column = end - (before.lastIndexOf('\n') + 1) + lastLine.toString().length + 3
        vScroll.reveal(padding + line * glyph.height, glyph.height, padding)
        hScroll.reveal(padding + column * glyph.width, glyph.width, padding)
    }
}

private suspend fun ScrollState.reveal(start: Int, size: Int, margin: Int) {
    if (viewportSize == 0) return   // not laid out yet: the dialog opens at the top left
    if (start - margin < value) scrollTo(start - margin)
    else if (start + size + margin > value + viewportSize) scrollTo(start + size + margin - viewportSize)
}

/** Shows a line number in front of each line; they are not part of the text. */
internal class LineNumbers(private val first: Int) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val lines = text.text.split("\n")
        val width = (first + lines.size - 1).toString().length
        val prefix = width + 3   // the number and " | "
        val starts = lines.runningFold(0) { start, line -> start + line.length + 1 }.dropLast(1)
        val shown = lines.indices.joinToString("\n") { (first + it).toString().padStart(width) + " | " + lines[it] }
        return TransformedText(AnnotatedString(shown), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + prefix * (starts.indexOfLast { it <= offset } + 1)

            override fun transformedToOriginal(offset: Int): Int {
                val i = starts.indices.last { starts[it] + prefix * it <= offset }
                return starts[i] + (offset - starts[i] - prefix * i - prefix).coerceAtLeast(0)
            }
        })
    }
}

/**
 * Asks before a build step that would throw something away: one line per
 * [losses] entry, then [actionLabel] / Cancel.
 */
@Composable
fun ConfirmLossDialog(actionLabel: String, losses: List<String>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$actionLabel?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                losses.forEach { Text("• $it") }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(actionLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A plain error dialog for a failed compiler-switch / C-edit / C-load — pytoy's
 * show_error_dialog. One OK button; the message is shown inline.
 */
@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit, title: String = "The program could not be built") {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
