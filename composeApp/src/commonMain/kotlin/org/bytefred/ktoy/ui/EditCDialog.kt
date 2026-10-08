package org.bytefred.ktoy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp

/**
 * Edit the loaded C source and recompile it — the C-column counterpart to the
 * memory panel's value editor. A multiline text field prefilled with the current
 * C plus Save / Cancel. Save calls [onSave], which recompiles with the active
 * compiler and returns null on success (the dialog closes) or an error message
 * (compile error, or a result overflowing the 240-byte region) — in which case
 * the error is shown and the dialog stays open so the user can fix the C. The
 * program is never half-loaded. Mirrors pytoy's EditCDialog.
 */
@Composable
fun EditCDialog(
    initial: String,
    onSave: (String) -> String?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit C source") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("C source — Save recompiles") },
                    modifier = Modifier.heightIn(min = 160.dp, max = 360.dp),
                )
                error?.let { Text(it, Modifier.padding(top = 4.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = onSave(text)
                if (err == null) onDismiss() else error = err
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A plain error dialog for a failed compiler-switch / C-edit / C-load — pytoy's
 * show_error_dialog. One OK button; the message is shown inline.
 */
@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("The program could not be built") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
