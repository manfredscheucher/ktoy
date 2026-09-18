package dev.scheucher.ktoy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.scheucher.ktoy.core.decode
import dev.scheucher.ktoy.core.describe

/**
 * The simulator/debugger screen: source panel (assembly) + memory panel, with
 * Step / Run / Undo / Reset. Portrait stacks the panels; landscape (wide) puts
 * them side by side. Highlighting mirrors pytoy.
 */
@Composable
fun SimulatorScreen(vm: SimViewModel, onBack: () -> Unit) {
    // read tick so the whole screen recomposes on any machine change
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val m = vm.machine ?: return

    var editTarget by remember { mutableStateOf<Int?>(null) }

    // current instruction (for PC / argument highlighting)
    val dec = decode(m.mem, m.pc)
    val pcBytes = buildSet {
        add(m.pc)
        if (dec.argAddr != null) add((m.pc + 1) % 256)
    }
    // reverse symbol map (addr -> label), built once instead of a linear scan
    // per describe() call
    val rsym = remember(vm.syms) { vm.syms.entries.associate { (k, v) -> v to k } }
    val symOf: (Int) -> String = { a -> rsym[a] ?: a.toString() }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        // top bar: back + title
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onBack) { Text("‹ Examples") }
            Text(vm.title, style = MonoStyle)
        }

        // status line
        StatusBlock(vm, symOf)

        // panels: side by side when wide, stacked when narrow. Each sits in a
        // labelled, bordered box so the ASM and byte views are clearly separate.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val wide = maxWidth > maxHeight
            val src: @Composable (Modifier) -> Unit = { mod ->
                LabeledPanel("Assembly", mod) { SourcePanel(vm, pcBytes, dec.argAddr, Modifier.fillMaxSize()) }
            }
            val mem: @Composable (Modifier) -> Unit = { mod ->
                LabeledPanel("Memory (bytes)", mod) { MemoryPanel(vm, pcBytes, dec.argAddr, { editTarget = it }, Modifier.fillMaxSize()) }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    if (vm.hasSource) src(Modifier.weight(1f).fillMaxSize())
                    mem(Modifier.weight(1f).fillMaxSize())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (vm.hasSource) src(Modifier.weight(1f).fillMaxWidth())
                    mem(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }

        Controls(vm)
    }

    editTarget?.let { addr ->
        EditValueDialog(
            addr = addr,
            current = m.mem[addr],
            onDismiss = { editTarget = null },
            onConfirm = { value -> vm.editCell(addr, value); editTarget = null },
        )
    }
}

/** A bordered box with a small title label, used to visually separate and name
 *  the ASM and memory panels. */
@Composable
private fun LabeledPanel(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .padding(3.dp)
            .border(1.dp, Color(0xFFBBBBBB), RoundedCornerShape(6.dp))
    ) {
        Text(
            title,
            style = MonoStyle,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFEEEEEE))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun StatusBlock(vm: SimViewModel, symOf: (Int) -> String) {
    val m = vm.machine ?: return
    val dec = decode(m.mem, m.pc)
    val desc = describe(dec.instr, dec.argAddr, symOf, m.mem)
    val header = if (m.stopped) "Stopped after ${m.stepCount} steps." else "Step #${m.stepCount}:"
    // the raw bytes of the current instruction: opcode, plus the operand byte
    // for a two-byte instruction — like pytoy's "OP{n}=b.. b..".
    val bytes = if (dec.argAddr != null) "b${bin8(dec.instr)} b${bin8(m.mem[(m.pc + 1) % 256])}"
                else "b${bin8(dec.instr)}"
    val opCount = bytes.split(" ").size
    // ACC: green when stopped (final result), blue when the last step wrote it
    val accColour: Color? = when {
        m.stopped && m.stepCount > 0 -> HL_ARG
        m.accWritten && m.stepCount > 0 -> HL_CHANGE
        else -> null
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(header, style = MonoStyle)
        HighlightRow("  ACC=${m.acc}=b${bin8(m.acc)}", if (accColour != null) listOf(accColour) else emptyList())
        Text("  PC=${m.pc}", style = MonoStyle)
        Text("  OP$opCount=$bytes", style = MonoStyle)
        Text("  EXPLAIN: $desc", style = MonoStyle)
    }
}

@Composable
private fun SourcePanel(vm: SimViewModel, pcBytes: Set<Int>, argAddr: Int?, modifier: Modifier) {
    val m = vm.machine ?: return
    // addr -> listing line index, for PC/arg line highlighting
    val lineForAddr = remember(vm.tick, vm.listing) {
        val map = mutableMapOf<Int, Int>()
        vm.listing.forEachIndexed { i, l ->
            if (l.addr != null) {
                map[l.addr] = i
                if (l.bytes.size == 2) map[l.addr + 1] = i
            }
        }
        map
    }
    val pcLines = pcBytes.mapNotNull { lineForAddr[it] }.toSet()
    val argLine = argAddr?.let { lineForAddr[it] }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    // keep the current PC line in view while stepping / running (like pytoy's
    // ensureCursorVisible). Approximate the line's y by index * a line height.
    val firstPcLine = pcLines.minOrNull()
    LaunchedEffect(firstPcLine, vm.tick) {
        if (firstPcLine != null) {
            val lineHeightPx = 34  // ~13sp mono line; good enough to keep it visible
            vScroll.animateScrollTo((firstPcLine * lineHeightPx).coerceIn(0, vScroll.maxValue))
        }
    }
    ScrollbarBox(vScroll, modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize().padding(4.dp)
        .verticalScroll(vScroll)
        .horizontalScroll(hScroll)) {
        vm.listing.forEachIndexed { i, line ->
            val marker = when {
                i in pcLines -> ">>"
                i == argLine -> "**"
                else -> "  "
            }
            val changed = m.lineChanged(line.addr, line.bytes.size)
            var text = if (line.orig.isNotEmpty()) "$marker ${line.orig.trimEnd()}" else marker
            val colours = mutableListOf<Color>()
            if (i == m.selectedAddr?.let { lineForAddr[it] }) colours.add(HL_SELECTED)
            if (i in pcLines) colours.add(HL_PC)
            if (i == argLine) colours.add(HL_ARG)
            if (changed) {
                if (line.isData) {
                    text = "$marker ${line.orig.trimEnd()}  # current value: ${m.mem[line.addr!!]}"
                    colours.add(HL_DATA)
                } else {
                    val raw = (0 until line.bytes.size).joinToString(" ") { m.mem[line.addr!! + it].toString() }
                    text = "$marker ${line.orig.trimEnd()}  # now bytes: $raw"
                    colours.add(HL_CODE)
                }
            }
            // tapping a source line selects its address (bidirectional with the
            // memory panel), like pytoy's _source_clicked
            val rowMod = if (line.addr != null)
                Modifier.pointerInput(line.addr) { detectTapGestures(onTap = { vm.select(line.addr) }) }
            else Modifier
            HighlightRow(text, colours, rowMod)
        }
      }
    }
}

@Composable
private fun MemoryPanel(
    vm: SimViewModel,
    pcBytes: Set<Int>,
    argAddr: Int?,
    onEdit: (Int) -> Unit,
    modifier: Modifier,
) {
    val m = vm.machine ?: return
    // Show every byte the program occupies (each listing line's address, plus
    // the operand byte of a two-byte instruction), together with any cell
    // written at runtime — mirroring pytoy's memory panel. A .toyo listing
    // already covers all 256 bytes, so those show in full too.
    val visible = remember(vm.tick, vm.listing) {
        val addrs = sortedSetOf<Int>()
        for (line in vm.listing) {
            val a = line.addr ?: continue
            addrs.add(a)
            if (line.bytes.size == 2) addrs.add((a + 1) % 256)
        }
        addrs.addAll(m.touched)
        addrs.toList()
    }
    // header aligned to the same columns as the rows: 5-char lead ("  >> "),
    // then each label right-justified to width 19, matching the addr/val cells.
    val header = "     " + "address (binary)".padStart(19) + "  ->  " + "value (binary)".padStart(19)
    val vScroll = rememberScrollState()
    ScrollbarBox(vScroll, modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize().padding(4.dp)
        .verticalScroll(vScroll)
        .horizontalScroll(rememberScrollState())) {
        Text(header, style = MonoStyle, softWrap = false, maxLines = 1)
        visible.forEach { a ->
            val v = m.mem[a]
            val marker = if (a == m.pc) ">>" else "  "
            val addrCell = "${a.toString().padStart(3)} (${bin8(a)})"
            val valCell = "${v.toString().padStart(3)} (${bin8(v)})"
            val text = "  $marker ${addrCell.padStart(19)}  ->  ${valCell.padStart(19)}"
            val colours = memoryCellColours(a, m.selectedAddr, pcBytes, argAddr, m.changedCell, m.stopped)
            Box(
                Modifier.fillMaxWidth().pointerInput(a) {
                    detectTapGestures(
                        onTap = { vm.select(a) },
                        onLongPress = { onEdit(a) },
                    )
                }
            ) {
                HighlightRow(text, colours)
            }
        }
      }
    }
}

@Composable
private fun Controls(vm: SimViewModel) {
    val m = vm.machine
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { vm.step() }, enabled = m?.stopped == false && !vm.running) { Text("Step") }
            Button(onClick = { vm.toggleRun() }, enabled = m?.stopped == false) { Text(if (vm.running) "Stop" else "Run") }
            OutlinedButton(onClick = { vm.stepBack() }, enabled = m?.canUndo == true) { Text("Undo") }
            OutlinedButton(onClick = { vm.reset() }, enabled = m?.canUndo == true) { Text("Reset") }
        }
        // steps-per-second for Run (float, like pytoy's spinbox; range 0.1..1000)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("steps/sec:", style = MonoStyle)
            var spsText by remember { mutableStateOf(vm.stepsPerSecond.toString()) }
            OutlinedTextField(
                value = spsText,
                onValueChange = {
                    spsText = it
                    it.toDoubleOrNull()?.let { v -> vm.stepsPerSecond = v.coerceIn(0.1, 1000.0) }
                },
                singleLine = true,
                modifier = Modifier.width(110.dp),
            )
        }
    }
}

/** 8-bit binary string (unsigned), like pytoy's f"{v:08b}". */
private fun bin8(v: Int): String {
    val u = v and 0xFF
    return (7 downTo 0).joinToString("") { if ((u shr it) and 1 == 1) "1" else "0" }
}
