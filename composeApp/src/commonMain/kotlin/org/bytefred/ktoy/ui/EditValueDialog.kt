package org.bytefred.ktoy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.bytefred.ktoy.core.OPCODES
import org.bytefred.ktoy.core.parseCellValue

/** Remembers the last view of the cell editor: "type" is the text field, anything else the buttons. */
internal const val EDIT_VIEW_PREF = "editCellView"

/**
 * Live-edit dialog for a memory cell (the address comes from the long-pressed
 * cell). Two views, the last one used is remembered: Select writes the clicked
 * operation or value at once; Type accepts the assembler's forms (decimal, 0x..,
 * 0b.., 8-bit binary), where values outside 0..255 wrap mod 256, which we point out.
 */
@Composable
fun EditValueDialog(
    addr: Int,
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var typing by remember { mutableStateOf(loadPref(EDIT_VIEW_PREF) == "type") }
    var text by remember { mutableStateOf(current.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    fun chooseView(type: Boolean) {
        typing = type
        savePref(EDIT_VIEW_PREF, if (type) "type" else "select")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter new value for address $addr") },
        text = {
            val vScroll = rememberScrollState()
            ScrollbarBox(vScroll, Modifier.heightIn(max = 620.dp)) {
                Column(Modifier.verticalScroll(vScroll).padding(end = 8.dp)) {  // room for the scrollbar
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !typing,
                            onClick = { chooseView(false) },
                            label = { Text("Select") },
                            modifier = Modifier.testTag("edit-view-select"),
                        )
                        FilterChip(
                            selected = typing,
                            onClick = { chooseView(true) },
                            label = { Text("Type") },
                            modifier = Modifier.testTag("edit-view-type"),
                        )
                    }
                    if (typing) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                error = null
                                note = null
                            },
                            label = { Text("decimal, binary b1010 or hex 0x1f") },
                            singleLine = true,
                            modifier = Modifier.testTag("edit-value"),
                        )
                        error?.let { Text(it, Modifier.padding(top = 4.dp)) }
                        note?.let { Text(it, Modifier.padding(top = 4.dp)) }
                    } else {
                        ValuePicker(onConfirm)
                    }
                }
            }
        },
        confirmButton = {
            if (typing) TextButton(onClick = {
                val raw = runCatching { parseCellValue(text) }.getOrNull()
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
            }) { Text(if (note != null) "Save anyway" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The operations in opcode order, then all 256 values, 16 per row (8 when narrow). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValuePicker(onPick: (Int) -> Unit) {
    Text("Operations", Modifier.padding(top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((name, op) in OPCODES.entries.sortedBy { it.value }) {
            PickButton(
                "${name.uppercase()}($op)",
                "pick-op-$op",
                Modifier.height(30.dp),
                horizontalPadding = 8,
            ) { onPick(op) }
        }
    }
    Text("Data", Modifier.padding(top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 3 digits need about 26 dp a cell at the normal font; narrower gets 8 per row
        val columns = if (maxWidth / 16 >= 26.dp) 16 else 8
        Column {
            for (row in 0 until 256 / columns) Row(Modifier.fillMaxWidth()) {
                for (value in row * columns until (row + 1) * columns) {
                    PickButton(
                        value.toString(),
                        "pick-val-$value",
                        Modifier.weight(1f).height(if (columns == 16) 26.dp else 36.dp).padding(1.dp),
                    ) { onPick(value) }
                }
            }
        }
    }
}

@Composable
private fun PickButton(
    label: String,
    tag: String,
    modifier: Modifier,
    horizontalPadding: Int = 0,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MonoStyle, softWrap = false, maxLines = 1)
    }
}
