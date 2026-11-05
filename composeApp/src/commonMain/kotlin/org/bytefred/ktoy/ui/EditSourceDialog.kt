package org.bytefred.ktoy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = { Text(label) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = singleLine,
                textStyle = MonoStyle,
                visualTransformation = if (singleLine) VisualTransformation.None else LineNumbers(firstLineNumber),
                modifier = (if (singleLine) Modifier else Modifier.heightIn(min = 160.dp, max = 360.dp))
                    .testTag("edit-source"),
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    error = check(text)
                    if (error == null) { onSave(text); onDismiss() }
                }) { Text("Save") }
                if (buildLabel != null) {
                    TextButton(onClick = { onSave(text); onDismiss(); onBuild() }) { Text("Save & $buildLabel") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
