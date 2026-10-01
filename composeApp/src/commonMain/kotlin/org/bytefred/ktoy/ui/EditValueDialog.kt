package org.bytefred.ktoy.ui

import androidx.compose.foundation.layout.Column
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
import org.bytefred.ktoy.core.parseVal

/**
 * Live-edit dialog for a memory cell. Asks only for the value (the address comes
 * from the long-pressed cell). Accepts the assembler's forms (decimal, 0x.., 0b..,
 * 8-bit binary); values outside 0..255 wrap mod 256, which we point out. Mirrors
 * pytoy's right-click edit.
 */
@Composable
fun EditValueDialog(
    addr: Int,
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(current.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter new value for address $addr") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                        note = null
                    },
                    label = { Text("decimal, 0x.., 0b.., or 8-bit binary") },
                    singleLine = true,
                )
                error?.let { Text(it, Modifier.padding(top = 4.dp)) }
                note?.let { Text(it, Modifier.padding(top = 4.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val raw = runCatching { parseVal(text) }.getOrNull()
                if (raw == null) {
                    error = "Could not parse '$text' as a number."
                    return@TextButton
                }
                val value = raw and 0xFF
                // If the value wraps to 8 bits, show that explicitly first (the
                // teaching point) and require a second tap to confirm — otherwise
                // the note would be dismissed in the same frame it appears.
                if (raw != value && note == null) {
                    note = "$raw doesn't fit in 8 bits — will be stored as " +
                        "$value ($raw mod 256). Tap again to confirm."
                    return@TextButton
                }
                onConfirm(value)
            }) { Text(if (note != null) "Set anyway" else "Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
