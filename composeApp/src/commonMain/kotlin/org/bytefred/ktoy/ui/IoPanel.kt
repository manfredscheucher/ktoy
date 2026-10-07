package org.bytefred.ktoy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.bytefred.ktoy.core.IO_BASE
import org.bytefred.ktoy.core.IO_COUNT
import org.bytefred.ktoy.core.IO_LABELS
import org.bytefred.ktoy.core.IO_READY
import org.bytefred.ktoy.core.IoFormat
import org.bytefred.ktoy.core.formatByte
import org.bytefred.ktoy.core.pollingInput

/**
 * The friendly memory-mapped I/O view — a port of pytoy's IOPanel. Shows the 15
 * I/O cells io0..io14 (addresses 240..254) plus the `ready` register (255) as a
 * 16th cell. Decimal / hex / ascii lay them out in one row; binary (each value 8
 * chars wide) in two rows of 8. A format selector picks the display mode. While
 * the CPU busy-polls `ready` (current instruction is `load 255`) the ready cell
 * is highlighted red. Tapping a cell opens the same edit dialog as the memory
 * panel, so the user acts as the I/O device and the running program reacts.
 *
 * Stateless: the format is owned by the caller ([SimulatorScreen]) so it survives
 * recomposition and config changes (it lives in the ViewModel). Clicking a cell
 * routes to [onEditCell], the same hook the memory panel uses.
 */
@Composable
fun IoPanel(
    mem: IntArray,
    pc: Int,
    format: IoFormat,
    onFormatChange: (IoFormat) -> Unit,
    onEditCell: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val polling = pollingInput(mem, pc)
    // 16 cells: io0..io14 then ready (255).
    val cellAddrs = (0 until IO_COUNT).map { IO_BASE + it } + IO_READY
    // Binary values are 8 chars wide, too wide for 16 in a line: split into two
    // rows of 8, matching pytoy's _relayout().
    val perRow = if (format == IoFormat.BINARY) 8 else 16
    // Fixed cell width so the name/value columns line up; wide enough for 8-char
    // binary plus a little breathing room.
    val cellWidth = if (format == IoFormat.BINARY) 72.dp else 40.dp

    Column(modifier.padding(4.dp)) {
        FormatSelector(format, onFormatChange)
        Column(Modifier.padding(top = 4.dp)) {
            cellAddrs.chunked(perRow).forEach { rowAddrs ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowAddrs.forEach { addr ->
                        IoCell(
                            addr = addr,
                            value = mem[addr],
                            format = format,
                            polling = polling,
                            onClick = { onEditCell(addr) },
                            modifier = Modifier.width(cellWidth),
                        )
                    }
                }
            }
        }
    }
}

/** One I/O cell: a name label on top (io0.., ready) and its formatted value
 *  below. The `ready` cell always shows decimal (its value is a flag/count, not
 *  a character) and turns red while the CPU polls it. Tapping edits it. */
@Composable
private fun IoCell(
    addr: Int,
    value: Int,
    format: IoFormat,
    polling: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isReady = addr == IO_READY
    val label = IO_LABELS[addr] ?: addr.toString()
    // ready never reads as a character; show it decimal even in ascii mode.
    val cellFmt = if (isReady && format == IoFormat.ASCII) IoFormat.DECIMAL else format
    val valueBg = if (isReady && polling) HL_READY_POLL else Color.Transparent
    val valueColor = if (isReady && polling) Color.White else Color.Black

    Column(
        modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MonoStyle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            formatByte(value, cellFmt),
            style = MonoStyle.copy(color = valueColor),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(valueBg),
        )
    }
}

/** The display-format selector (decimal / binary / hex / ascii), as a row of
 *  filter chips — the ktoy equivalent of pytoy's View → I/O format menu. */
@Composable
private fun FormatSelector(format: IoFormat, onFormatChange: (IoFormat) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IoFormat.entries.forEach { f ->
            FilterChip(
                selected = f == format,
                onClick = { onFormatChange(f) },
                label = { Text(f.name.lowercase(), style = MonoStyle) },
            )
        }
    }
}

/** Red background for the `ready` cell while the CPU is busy-polling it
 *  (current instruction is `load 255`) — pytoy's ready_style_polling. */
private val HL_READY_POLL = Color(0xFFCC0000)
