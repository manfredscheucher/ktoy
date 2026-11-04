package org.bytefred.ktoy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable

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
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = 160.dp, max = 360.dp).testTag("edit-source"),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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

/**
 * Execution -> Invalid opcode = Always ask: the run paused on a byte at [addr]
 * that is no opcode. NOP runs it as a NOP (the toycpu behaviour), Stop halts
 * with an error; "Remember this decision" makes the answer the setting.
 */
@Composable
fun InvalidOpcodeDialog(addr: Int, opcode: Int, onAnswer: (asNop: Boolean, remember: Boolean) -> Unit) {
    var remember by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},   // an answer is needed: the run waits for it
        title = { Text("Invalid opcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The byte $opcode at address $addr is not a valid opcode. " +
                    "Like Jim Hall's toycpu, ktoy runs it as a NOP unless you stop here.")
                // the label ticks the box too
                Row(
                    Modifier.toggleable(remember, role = Role.Checkbox) { remember = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(remember, onCheckedChange = null)
                    Text("Remember this decision")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true, remember) }) { Text("NOP") } },
        dismissButton = { TextButton(onClick = { onAnswer(false, remember) }) { Text("Stop") } },
    )
}
