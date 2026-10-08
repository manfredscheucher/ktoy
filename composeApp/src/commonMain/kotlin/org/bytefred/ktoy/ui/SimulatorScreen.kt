package org.bytefred.ktoy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.unit.dp
import org.bytefred.ktoy.core.decode
import org.bytefred.ktoy.core.describe
import org.bytefred.ktoy.core.formatByte
import org.bytefred.ktoy.core.IoFormat

/**
 * The simulator/debugger screen with an adaptive 1/2/3-column layout driven by
 * what's loaded (pytoy's three-column view):
 *   - bytecode (.toyo) → memory only (1 column)
 *   - assembly (.toys) → asm + memory (2 columns)
 *   - C (.toyc)        → C + asm + memory (3 columns)
 * Portrait stacks the columns; landscape puts them side by side. A click in ANY
 * column highlights the linked lines/cells in the others transitively (C ↔ asm ↔
 * bytecode). Highlighting mirrors pytoy.
 */
@Composable
fun SimulatorScreen(vm: SimViewModel, onLoad: () -> Unit) {
    // read tick so the whole screen recomposes on any machine change
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val m = vm.machine ?: return

    var editTarget by remember { mutableStateOf<Int?>(null) }
    // true while the C-edit dialog is open (long-press on the C column)
    var editCOpen by remember { mutableStateOf(false) }

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

    // the shared transitive selection, computed once and read by all panels so a
    // click anywhere lights up consistently everywhere (pytoy's _selection_sets)
    val sel = vm.selectionSets()
    val (changedCLines, changedRows) = vm.changeSets()

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        // top bar: Load button (opens the example list) + current program title +
        // an I/O panel toggle (ktoy's equivalent of pytoy's View → I/O panel)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Button(onClick = onLoad) { Text("Load…") }
            Text(vm.title, style = MonoStyle)
            OutlinedButton(onClick = { vm.showIo = !vm.showIo }) {
                Text(if (vm.showIo) "Hide I/O" else "Show I/O")
            }
        }

        // Adaptive columns, like pytoy: C (only for a C load) | asm (only with
        // source) | CPU & memory (always). Side by side when wide, stacked when
        // narrow; each in a labelled, bordered box.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val wide = maxWidth > maxHeight
            val cCol: @Composable (Modifier) -> Unit = { mod ->
                CSourcePanel(vm, sel.cLines, changedCLines, { editCOpen = true }, mod)
            }
            val src: @Composable (Modifier) -> Unit = { mod ->
                LabeledPanel("Assembly", mod) {
                    SourcePanel(vm, pcBytes, dec.argAddr, sel.asmRows, changedRows, Modifier.fillMaxSize())
                }
            }
            val mem: @Composable (Modifier) -> Unit = { mod ->
                LabeledPanel("CPU & memory", mod) {
                    Column(Modifier.fillMaxSize()) {
                        StatusBlock(vm, symOf)
                        MemoryPanel(vm, pcBytes, dec.argAddr, sel.addrs, { editTarget = it }, Modifier.fillMaxSize())
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    if (vm.hasCSource) cCol(Modifier.weight(1f).fillMaxSize())
                    if (vm.hasSource) src(Modifier.weight(1f).fillMaxSize())
                    mem(Modifier.weight(1f).fillMaxSize())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (vm.hasCSource) cCol(Modifier.weight(1f).fillMaxWidth())
                    if (vm.hasSource) src(Modifier.weight(1f).fillMaxWidth())
                    mem(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }

        // The memory-mapped I/O panel: the friendly view of io0..io14 plus the
        // ready register. Shown below the two panels when toggled on; tapping a
        // cell reuses the same edit dialog as the memory panel, so the user acts
        // as the I/O device while the running program polls.
        if (vm.showIo) {
            // Own bordered container (not LabeledPanel, whose body forces
            // fillMaxSize for the scrollable main panels — the I/O panel wraps
            // its height instead, so it doesn't push the controls off-screen).
            Column(
                Modifier.fillMaxWidth()
                    .padding(top = 3.dp)
                    .border(1.dp, Color(0xFFBBBBBB), RoundedCornerShape(6.dp))
            ) {
                Text(
                    "I/O",
                    style = MonoStyle,
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFFEEEEEE))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                IoPanel(
                    mem = m.mem,
                    pc = m.pc,
                    format = vm.ioFormat,
                    onFormatChange = { vm.ioFormat = it },
                    onEditCell = { editTarget = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Controls(vm, onEdit = { vm.memSelectedAddr?.let { editTarget = it } })
    }

    editTarget?.let { addr ->
        EditValueDialog(
            addr = addr,
            current = m.mem[addr],
            onDismiss = { editTarget = null },
            onConfirm = { value -> vm.editCell(addr, value); editTarget = null },
        )
    }

    // C-edit dialog: long-press the C column to edit + recompile. applyEditedC
    // returns null on success (dialog closes) or the error (dialog stays open).
    if (editCOpen) {
        EditCDialog(
            initial = vm.cText ?: "",
            onSave = { newC -> vm.applyEditedC(newC) },
            onDismiss = { editCOpen = false },
        )
    }

    // Error dialog for failed compiler-switch / C-edit / C-load (pytoy's
    // show_error_dialog). Shown whenever the VM has set an error message.
    vm.errorMessage?.let { msg ->
        ErrorDialog(message = msg, onDismiss = { vm.clearError() })
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
    @Suppress("UNUSED_VARIABLE") val t = vm.tick  // recompose on every machine change
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
private fun SourcePanel(
    vm: SimViewModel,
    pcBytes: Set<Int>,
    argAddr: Int?,
    selAsmRows: Set<Int>,
    changedRows: Set<Int>,
    modifier: Modifier,
) {
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
    // scroll to the PC line only when it MOVES (step/run) — not on every bump,
    // so tapping a distant line to select it doesn't yank the view back to PC.
    LaunchedEffect(firstPcLine) {
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
            // the transitive selection: a click in ANY column resolves to the asm
            // rows that light up here (a selected C line maps to several rows)
            if (i in selAsmRows) colours.add(HL_SELECTED)
            if (i in pcLines) colours.add(HL_PC)
            if (i == argLine) colours.add(HL_ARG)
            // a STORE just overwrote a byte: its owning asm row goes red too
            // (change propagation), even when the written value equals the original
            if (i in changedRows && HL_CODE !in colours) colours.add(HL_CODE)
            if (changed) {
                if (line.isData) {
                    text = "$marker ${line.orig.trimEnd()}  # current value: ${m.mem[line.addr!!]}"
                    colours.add(HL_DATA)
                } else {
                    val raw = (0 until line.bytes.size).joinToString(" ") { m.mem[(line.addr!! + it) % 256].toString() }
                    text = "$marker ${line.orig.trimEnd()}  # now bytes: $raw"
                    if (HL_CODE !in colours) colours.add(HL_CODE)
                }
            }
            // tapping a source line selects its address (resolves across all
            // columns via the VM's shared selection), like pytoy's _source_clicked
            val rowMod = if (line.addr != null)
                Modifier.clickable { vm.select(line.addr) }
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
    selAddrs: Set<Int>,
    onEdit: (Int) -> Unit,
    modifier: Modifier,
) {
    val m = vm.machine ?: return
    // Show ALL 256 addresses. Addresses the program actually occupies (a listing
    // line's byte(s)) or that were written at runtime are "used"; the rest are
    // untouched zero bytes, shown greyed out rather than hidden.
    // A plain set: we only ever test membership (the panel iterates 0..255),
    // so no ordering is needed — and sortedSetOf is JVM-only (breaks js/wasm).
    val usedAddrs = remember(vm.tick, vm.listing) {
        val addrs = mutableSetOf<Int>()
        for (line in vm.listing) {
            val a = line.addr ?: continue
            addrs.add(a)
            if (line.bytes.size == 2) addrs.add((a + 1) % 256)
        }
        addrs.addAll(m.touched)
        addrs
    }
    val visible = (0..255).toList()
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
            var text = "  $marker ${addrCell.padStart(19)}  ->  ${valCell.padStart(19)}"
            // the cell a STORE just overwrote: show its previous value as a
            // comment, for this one step only (changed/prev clear next step).
            if (!m.stopped && a == m.changedCell && m.changedCellPrev != null) {
                text += "  # old: ${m.changedCellPrev}"
            }
            // the transitive selection lights up ALL the bytes of the selected
            // statement here, not just the one cell clicked (pytoy's sel_addrs)
            val colours = memoryCellColours(a, selAddrs, pcBytes, argAddr, m.changedCell, m.stopped)
            // untouched, non-program bytes are shown greyed out
            val textColor = if (a in usedAddrs) Color.Black else Color(0xFFAAAAAA)
            Box(
                Modifier.fillMaxWidth().combinedClickable(
                    onClick = { vm.select(a, fromMemory = true) },
                    onLongClick = { onEdit(a) },
                )
            ) {
                HighlightRow(text, colours, textColor = textColor)
            }
        }
      }
    }
}

/**
 * The C column: the C source with a line-number gutter, a header carrying the
 * compiler-switch button (full C toycc ↔ mini C minicc), and transitive
 * highlighting — the selected statement in orange (HL_SELECTED) and a
 * STORE-changed statement in red (HL_CODE). Tapping a line selects it across all
 * columns (vm.selectCLine); long-pressing opens the C-edit dialog. Mirrors
 * pytoy's c_box + _refresh_c. Shown only when a C program is loaded.
 */
@Composable
private fun CSourcePanel(
    vm: SimViewModel,
    selCLines: Set<Int>,
    changedCLines: Set<Int>,
    onEditC: () -> Unit,
    modifier: Modifier,
) {
    val cText = vm.cText ?: return
    val lines = cText.split("\n")
    val gutterWidth = (lines.size.toString().length).coerceAtLeast(1)
    // the button offers the OTHER compiler, read as the action to take
    val other = if (vm.currentCompiler == "toycc") "minicc" else "toycc"
    val switchLabel = if (other == "minicc") "Switch to mini C" else "Switch to full C"

    Column(
        modifier
            .padding(3.dp)
            .border(1.dp, Color(0xFFBBBBBB), RoundedCornerShape(6.dp))
    ) {
        // header: title + the compiler-switch button (pytoy's btn_switch_compiler)
        Row(
            Modifier.fillMaxWidth()
                .background(Color(0xFFEEEEEE))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("C source", style = MonoStyle)
            OutlinedButton(onClick = { vm.switchCompiler() }) { Text(switchLabel) }
        }
        val vScroll = rememberScrollState()
        ScrollbarBox(vScroll, Modifier.fillMaxSize()) {
          Column(
              Modifier.fillMaxSize().padding(4.dp)
                  .verticalScroll(vScroll)
                  .horizontalScroll(rememberScrollState())
                  // long-press anywhere in the C column opens the edit dialog,
                  // mirroring the memory panel's long-click-to-edit
                  .combinedClickable(onClick = {}, onLongClick = onEditC)
          ) {
            lines.forEachIndexed { i, line ->
                val cLine = i + 1   // 1-based, matching the markers
                val colours = mutableListOf<Color>()
                if (cLine in selCLines) colours.add(HL_SELECTED)
                if (cLine in changedCLines) colours.add(HL_CODE)
                val gutter = "${cLine.toString().padStart(gutterWidth)} | "
                HighlightRow(
                    gutter + line,
                    colours,
                    Modifier.clickable { vm.selectCLine(cLine) },
                )
            }
          }
        }
    }
}

@Composable
private fun Controls(vm: SimViewModel, onEdit: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick  // recompose when canUndo/stopped change
    val m = vm.machine
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { vm.step() }, enabled = m?.stopped == false && !vm.running) { Text("Step") }
            Button(onClick = { vm.toggleRun() }, enabled = m?.stopped == false) { Text(if (vm.running) "Stop" else "Run") }
            OutlinedButton(onClick = { vm.stepBack() }, enabled = m?.canUndo == true) { Text("Undo") }
            OutlinedButton(onClick = { vm.reset() }, enabled = m?.canUndo == true) { Text("Reset") }
            // Edit the value at the address selected in the CPU & memory panel;
            // enabled only when such a selection exists.
            OutlinedButton(onClick = onEdit, enabled = vm.memSelectedAddr != null) { Text("Edit") }
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

/** 8-bit binary string (unsigned), like pytoy's f"{v:08b}". Delegates to the one
 *  binary formatter in Format.kt so the memory panel and the I/O panel can't drift. */
private fun bin8(v: Int): String = formatByte(v, IoFormat.BINARY)
