package org.bytefred.ktoy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    // true while the C / asm edit dialog is open (the column's right-click /
    // long-press menu, or C / Assembly -> Edit…)
    var editCOpen by remember { mutableStateOf(false) }
    var editAsmOpen by remember { mutableStateOf(false) }
    var editBytecodeOpen by remember { mutableStateOf(false) }
    var editCLine by remember { mutableStateOf<Int?>(null) }     // 0-based
    var editAsmLine by remember { mutableStateOf<Int?>(null) }   // 0-based

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
        // Menu bar + the current program title, a grey bar so it reads as one.
        Row(
            Modifier.fillMaxWidth()
                .background(MENU_BAR_COLOR, RoundedCornerShape(6.dp))
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            SimMenuBar(
                vm,
                onBrowseExamples = onLoad,
                onEditC = { editCOpen = true },
                onEditAsm = { editAsmOpen = true },
                onEditBytecode = { editBytecodeOpen = true },
                onEditCell = { vm.memSelectedAddr?.let { editTarget = it } },
            )
            // one line + ellipsis, shrinking (weight), so a long name can't push
            // the menu off-screen
            Text(
                vm.title,
                style = MonoStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp),
            )
        }
        // a transient "Saved …" status line from the File menu, auto-cleared
        vm.statusMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3000)
                vm.clearStatus()
            }
            Text(msg, style = MonoStyle, color = Color(0xFF1A7F37))
        }

        // Adaptive columns, like pytoy: C (only for a C load) | asm (only with
        // source) | CPU & memory (always). Side by side when wide, stacked when
        // narrow; each in a labelled, bordered box.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val cols = (if (vm.hasCSource) 1 else 0) +
                (if (vm.hasSource) 1 else 0) + 1
            // AUTO: side by side once each shown column gets its usable minimum
            // width (by width only, so it follows a window resize); PORTRAIT /
            // LANDSCAPE override it (View -> Layout).
            val minPerCol = 300.dp
            val wide = when (vm.layoutMode) {
                LayoutMode.LANDSCAPE -> true
                LayoutMode.PORTRAIT -> false
                LayoutMode.AUTO -> maxWidth >= minPerCol * cols
            }
            val pcCLine = vm.pcCLine()
            val cCol: @Composable (Modifier) -> Unit = { mod ->
                CSourcePanel(vm, sel.cLines, changedCLines, pcCLine, { editCOpen = true }, { editCLine = it }, mod)
            }
            val src: @Composable (Modifier) -> Unit = { mod ->
                LabeledPanel(if (vm.asmDraft != null) "Assembly (modified)" else "Assembly", mod) {
                    SourcePanel(vm, pcBytes, dec.argAddr, sel.asmRows, changedRows, { editAsmOpen = true }, { editAsmLine = it }, Modifier.fillMaxSize())
                }
            }
            val mem: @Composable (Modifier) -> Unit = { mod ->
                LabeledPanel("CPU & memory", mod) {
                    Column(Modifier.fillMaxSize()) {
                        StatusBlock(vm, symOf)
                        MemoryPanel(vm, pcBytes, dec.argAddr, sel.addrs, { editBytecodeOpen = true }, { editTarget = it }, Modifier.fillMaxSize())
                    }
                }
            }
            val panels = buildList {
                if (vm.hasCSource) add(SplitPanel(Panel.C, cCol))
                if (vm.hasSource) add(SplitPanel(Panel.ASM, src))
                add(SplitPanel(Panel.MEMORY, mem))
            }
            // stacked on phones too; each layout keeps its own divider positions
            key(wide) {
                SplitPane(
                    panels,
                    if (wide) vm.wideWeights else vm.stackedWeights,
                    { if (wide) vm.wideWeights = it else vm.stackedWeights = it },
                    horizontal = wide,
                    modifier = Modifier.fillMaxSize(),
                )
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

    // Edit dialogs (the column's context menu, or C / Assembly -> Edit…). Save only
    // stores the text as a draft; Save & Compile / Save & Assemble also builds it.
    if (editCOpen) {
        EditSourceDialog(
            title = "Edit C source",
            label = "C source",
            initial = vm.cDraft ?: vm.cText ?: "",
            buildLabel = "Compile",
            onSave = vm::saveCDraft,
            onBuild = { vm.request(SimViewModel.BuildAction.Compile()) },
            onDismiss = { editCOpen = false },
        )
    }
    if (editAsmOpen) {
        EditSourceDialog(
            title = "Edit assembly",
            label = "Assembly",
            initial = vm.asmDraft ?: vm.asmText ?: "",
            buildLabel = "Assemble",
            onSave = vm::saveAsmDraft,
            onBuild = { vm.request(SimViewModel.BuildAction.Assemble) },
            onDismiss = { editAsmOpen = false },
        )
    }
    if (editBytecodeOpen) {
        // Save writes the differences to the text shown, so memory must hold still
        LaunchedEffect(Unit) { if (vm.running) vm.toggleRun() }
        EditSourceDialog(
            title = "Edit bytecode",
            label = "Bytecode",
            initial = m.mem.joinToString("\n"),
            buildLabel = "Disassemble",
            firstLineNumber = 0,   // the number is the address
            check = vm::bytecodeError,
            onSave = vm::saveBytecode,
            onBuild = { vm.request(SimViewModel.BuildAction.Disassemble) },
            onDismiss = { editBytecodeOpen = false },
        )
    }
    editCLine?.let { line ->
        EditLineDialog(
            line, vm.cDraft ?: vm.cText ?: "", "C source", "Compile", vm::saveCDraft,
            onBuild = { vm.request(SimViewModel.BuildAction.Compile()) },
            onDismiss = { editCLine = null },
        )
    }
    editAsmLine?.let { line ->
        EditLineDialog(
            line, vm.asmDraft ?: vm.asmText ?: "", "Assembly", "Assemble", vm::saveAsmDraft,
            onBuild = { vm.request(SimViewModel.BuildAction.Assemble) },
            onDismiss = { editAsmLine = null },
        )
    }

    // Compile / Assemble / Disassemble that would lose something: list it and
    // ask first (SimViewModel.request).
    vm.pendingAction?.let { action ->
        ConfirmLossDialog(action.label, vm.lossesFor(action), vm::confirmPending, vm::cancelPending)
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
private fun LabeledPanel(
    title: String,
    modifier: Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .padding(3.dp)
            .border(1.dp, Color(0xFFBBBBBB), RoundedCornerShape(6.dp))
            .testTag("panel-" + title.substringBefore(" ("))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFEEEEEE))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(title, style = MonoStyle)
            action?.invoke()
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/** Edits one line (0-based) of [source]; [onSave] gets the whole text back. */
@Composable
private fun EditLineDialog(
    line: Int,
    source: String,
    label: String,
    buildLabel: String,
    onSave: (String) -> Unit,
    onBuild: () -> Unit,
    onDismiss: () -> Unit,
) {
    val lines = source.split("\n")
    EditSourceDialog(
        title = "Edit line ${line + 1}",
        label = label,
        initial = lines.getOrElse(line) { "" },
        buildLabel = buildLabel,
        singleLine = true,
        onSave = { new -> onSave(lines.mapIndexed { i, l -> if (i == line) new else l }.joinToString("\n")) },
        onBuild = onBuild,
        onDismiss = onDismiss,
    )
}

@Composable
private fun StatusBlock(vm: SimViewModel, symOf: (Int) -> String) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick  // recompose on every machine change
    val m = vm.machine ?: return
    val dec = decode(m.mem, m.pc)
    val desc = describe(dec.instr, dec.argAddr, symOf, m.mem)
    val header = when {
        m.stopped -> "Stopped after ${m.stepCount} steps."
        else -> "Step #${m.stepCount}:"
    }
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
    onEdit: () -> Unit,
    onEditLine: (Int) -> Unit,
    modifier: Modifier,
) {
    val m = vm.machine ?: return
    val menu = rememberEditMenu()
    val gestures = LocalEditGestures.current
    vm.asmDraft?.let { draft ->
        AsmDraftPanel(draft, vm, menu, gestures, onEdit, onEditLine, modifier)
        return
    }
    val pcLines = pcBytes.mapNotNull { vm.addrToLine[it] }.toSet()
    val argLine = argAddr?.let { vm.addrToLine[it] }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    // keep the focused asm line centred: a selected row (a click elsewhere
    // resolved here) wins, else the current PC line while stepping / running
    // (like pytoy's ensureCursorVisible; View -> Auto-scroll to PC). Fires only
    // when the line changes, so tapping doesn't fight the view.
    val focusLine = selAsmRows.minOrNull() ?: pcLines.minOrNull()?.takeIf { vm.autoScroll }
    scrollToLine(vScroll, focusLine, vm.scrollHeld("asm"))
    // display-only line-number gutter (pytoy's _refresh_source), toggled from
    // View -> Line numbers. Never part of any saved file; labels, not numbers,
    // are how asm refers to lines.
    val gutterWidth = vm.listing.size.toString().length.coerceAtLeast(1)
    EditMenuBox(menu, modifier.fillMaxSize()) {
    ScrollColumn(vScroll, hScroll) {
        vm.listing.forEachIndexed { i, line ->
            val gutter = if (vm.showLineNumbers)
                "${(i + 1).toString().padStart(gutterWidth)} | " else ""
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
            if (i in selAsmRows || vm.isMarked("asm", i)) colours.add(HL_SELECTED)
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
            // columns via the VM's shared selection), like pytoy's _source_clicked;
            // right-click / long-press opens the edit menu. A listing row is its
            // line of the assembly source.
            val rowMod = Modifier.editableRow(
                menu, gestures, i to line.addr, { line.addr?.let { vm.select(it) } },
                { line.addr?.let { vm.select(it); vm.holdScroll("asm") } ?: vm.markRow("asm", i) }, onEdit,
            ) { onEditLine(i) }
            HighlightRow(gutter + text, colours, rowMod)
        }
    }
    }
}

/**
 * Unassembled asm edits as plain text: no listing yet, so no highlighting or
 * selection until Assembly -> Assemble. The Edit… menu edits again.
 */
@Composable
private fun AsmDraftPanel(
    draft: String,
    vm: SimViewModel,
    menu: EditMenuState,
    gestures: EditGestures,
    onEdit: () -> Unit,
    onEditLine: (Int) -> Unit,
    modifier: Modifier,
) {
    val lines = draft.split("\n")
    val gutterWidth = lines.size.toString().length
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    EditMenuBox(menu, modifier.fillMaxSize()) {
    ScrollColumn(vScroll, hScroll) {
        lines.forEachIndexed { i, line ->
            val gutter = if (vm.showLineNumbers) "${(i + 1).toString().padStart(gutterWidth)} | " else ""
            // "   " lines the text up with the listing's ">> " marker column
            HighlightRow(
                gutter + "   " + line,
                if (vm.isMarked("asm", i)) listOf(HL_SELECTED) else emptyList(),
                Modifier.editableRow(menu, gestures, i, onTap = null, { vm.markRow("asm", i) }, onEdit) { onEditLine(i) },
            )
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
    onEdit: () -> Unit,
    onEditLine: (Int) -> Unit,
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
    // header aligned to the same columns as the rows: 3-char lead (">> "), then
    // the two 14-char cells ("  3 (00000011)")
    val header = "   " + "addr (binary)".padStart(14) + " -> " + "value (binary)"
    val vScroll = rememberScrollState()
    // scroll so a selected cell (or, failing that, the PC if View -> Auto-scroll
    // to PC is on) is centred. +1 for the header row above address 0.
    val focusAddr = selAddrs.minOrNull() ?: m.pc.takeIf { vm.autoScroll }
    scrollToLine(vScroll, focusAddr?.plus(1), vm.scrollHeld("mem"))
    val hScroll = rememberScrollState()
    val menu = rememberEditMenu()
    val gestures = LocalEditGestures.current
    EditMenuBox(menu, modifier.fillMaxSize()) {
    ScrollColumn(vScroll, hScroll) {
        Text(header, style = MonoStyle, softWrap = false, maxLines = 1)
        visible.forEach { a ->
            val v = m.mem[a]
            val marker = if (a == m.pc) ">>" else "  "
            val addrCell = "${a.toString().padStart(3)} (${bin8(a)})"
            val valCell = "${v.toString().padStart(3)} (${bin8(v)})"
            var text = "$marker $addrCell -> $valCell"
            // the cell a STORE just overwrote: show its previous value as a
            // comment, for this one step only (changed/prev clear next step).
            if (!m.stopped && a == m.changedCell && m.changedCellPrev != null) {
                text += "  # old: ${m.changedCellPrev}"
            }
            // the transitive selection: a C-line click lights up ALL the bytes of
            // the statement here; an asm/memory click lights only the clicked
            // row's own bytes (pytoy's sel_addrs)
            // selected, PC, argument, change: all the roles this cell has
            val colours = buildList {
                if (a in selAddrs) add(HL_SELECTED)
                if (a in pcBytes) add(HL_PC)
                if (a == argAddr) add(HL_ARG)
                if (!m.stopped && a == m.changedCell) add(HL_CHANGE)
            }
            // untouched, non-program bytes are shown greyed out
            val textColor = if (a in usedAddrs) Color.Black else Color(0xFFAAAAAA)
            // tap selects the cell; right-click / long-press opens the edit menu
            Box(
                Modifier.fillMaxWidth().editableRow(
                    menu, gestures, a, { vm.select(a, fromMemory = true) }, { vm.select(a, fromMemory = true); vm.holdScroll("mem") }, onEdit,
                ) { onEditLine(a) }
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
 * highlighting — the selected statement in orange (HL_SELECTED), the current
 * instruction's C line in yellow (HL_PC, mapped up from the PC's asm row) and a
 * STORE-changed statement in red (HL_CODE). Tap a line to select it across all
 * columns (vm.selectCLine); right-click / long-press opens the Edit… menu.
 * Mirrors pytoy's c_box + _refresh_c.
 * Shown only when a C program is loaded.
 */
@Composable
private fun CSourcePanel(
    vm: SimViewModel,
    selCLines: Set<Int>,
    changedCLines: Set<Int>,
    pcCLine: Int?,
    onEditC: () -> Unit,
    onEditLine: (Int) -> Unit,
    modifier: Modifier,
) {
    // an uncompiled draft is shown as plain text: the C <-> asm maps belong to
    // the last compiled C, so no highlighting or selection until C -> Compile
    val draft = vm.cDraft
    val cText = draft ?: vm.cText ?: return
    val lines = cText.split("\n")
    val gutterWidth = (lines.size.toString().length).coerceAtLeast(1)
    // the button offers the OTHER compiler, read as the action to take (pytoy's
    // btn_switch_compiler); same action as the compiler choice in the C menu
    val other = if (vm.currentCompiler == "toycc") "minicc" else "toycc"
    val switchLabel = if (other == "minicc") "Switch to mini C" else "Switch to full C"
    val switch: @Composable () -> Unit = {
        OutlinedButton(onClick = { vm.request(SimViewModel.BuildAction.Compile(other)) }) { Text(switchLabel) }
    }

    LabeledPanel(if (draft != null) "C source (modified)" else "C source", modifier, switch) {
        val vScroll = rememberScrollState()
        // keep the selected C line visible (centred) when a click in another
        // column resolves to a C statement here; falls back to the PC's C line.
        val pcLine = pcCLine?.takeIf { vm.autoScroll }
        scrollToLine(vScroll, if (draft != null) null else selCLines.minOrNull()?.minus(1) ?: pcLine?.minus(1), vm.scrollHeld("c"))
        val hScroll = rememberScrollState()
        val menu = rememberEditMenu()
        val gestures = LocalEditGestures.current
        EditMenuBox(menu, Modifier.fillMaxSize()) {
        ScrollColumn(vScroll, hScroll) {
            lines.forEachIndexed { i, line ->
                val cLine = i + 1   // 1-based, matching the markers
                val colours = mutableListOf<Color>()
                if (vm.isMarked("c", cLine)) colours.add(HL_SELECTED)
                if (draft == null) {
                    if (cLine in selCLines) colours.add(HL_SELECTED)
                    if (cLine == pcCLine) colours.add(HL_PC)
                    if (cLine in changedCLines) colours.add(HL_CODE)
                }
                val gutter = if (vm.showLineNumbers)
                    "${cLine.toString().padStart(gutterWidth)} | " else ""
                // tap selects the C line; right-click / long-press opens the
                // Edit… menu
                HighlightRow(
                    gutter + line,
                    colours,
                    Modifier.editableRow(
                        menu, gestures, cLine to draft, { if (draft == null) vm.selectCLine(cLine) },
                        { if (draft == null && vm.cLineHasCode(cLine)) { vm.selectCLine(cLine); vm.holdScroll("c") } else vm.markRow("c", cLine) }, onEditC,
                    ) { onEditLine(cLine - 1) },
                )
            }
        }
        }
    }
}

/**
 * Scroll [state] so the row at [lineIndex] (0-based) is centred in the
 * viewport, whenever [lineIndex] changes. Used so a click in one column scrolls
 * the others to the matching line, and to follow the PC. The row height is
 * measured from [MonoStyle] (every row is one line of it) and the column's 4 dp
 * top padding is added; null leaves the scroll untouched.
 */
@Composable
private fun scrollToLine(state: androidx.compose.foundation.ScrollState, lineIndex: Int?, held: Boolean = false) {
    val rowPx = rememberTextMeasurer().measure("0", MonoStyle).size.height
    val topPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    LaunchedEffect(lineIndex) {
        if (lineIndex != null && !held) {
            val target = topPx + lineIndex * rowPx + rowPx / 2 - state.viewportSize / 2
            state.animateScrollTo(target.coerceIn(0, state.maxValue))
        }
    }
}

/**
 * The bottom controls bar:
 *   Reload — GAP — Reset · Undo · Step · Run · steps/sec
 * Reload (desktop only) re-runs the last file, asking first about unsaved edits.
 * Load and Examples are in the File menu.
 * A cell is edited via its right-click / long-press menu or Bytecode -> Edit line….
 */
@Composable
private fun Controls(vm: SimViewModel) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick  // recompose when canUndo/stopped change
    val m = vm.machine

    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        // narrow screens (phones) get two rows of compact, low controls so the
        // panels keep as much height as possible
        val compact = maxWidth < CONTROLS_ONE_ROW_MIN

        // Reload | Reset Undo Step Run steps/sec
        val fileGroup: @Composable () -> Unit = {
            if (fileReloadSupported) {
                CtlButton("Reload", compact, enabled = vm.canReload) { vm.leave { vm.reload() } }
            }
        }
        val stepGroup: @Composable () -> Unit = {
            CtlButton("Reset", compact, enabled = m?.canUndo == true) { vm.reset() }
            CtlButton("Undo", compact, enabled = m?.canUndo == true) { vm.stepBack() }
            CtlButton("Step", compact, filled = true, enabled = m?.stopped == false && !vm.running) { vm.step() }
        }
        val runGroup: @Composable () -> Unit = {
            CtlButton(if (vm.running) "Stop" else "Run", compact, filled = true, enabled = m?.stopped == false) {
                vm.toggleRun()
            }
            StepsPerSecInput(vm.stepsPerSecond, compact) { vm.stepsPerSecond = it }
        }
        val center = androidx.compose.ui.Alignment.CenterVertically
        val gap = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)

        if (!compact) {
            // wide: one row, the run controls in the middle
            Row(Modifier.fillMaxWidth(), verticalAlignment = center, horizontalArrangement = gap) {
                fileGroup()
                Box(Modifier.weight(1f))
                stepGroup()
                runGroup()
                Box(Modifier.weight(1f))
            }
        } else {
            // narrow: the groups wrap as needed, compact so the panels keep their height
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = gap,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (group in listOf(fileGroup, stepGroup, runGroup)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = center, horizontalArrangement = gap) {
                        group()
                    }
                }
            }
        }
    }
}

// Below this width the bottom bar turns compact and wraps.
private val CONTROLS_ONE_ROW_MIN = 800.dp

// Height of a compact (phone) control, instead of Material3's 40 dp buttons and
// the 56 dp text field.
private val COMPACT_HEIGHT = 34.dp

private val MENU_BAR_COLOR = Color(0xFFE6E8EB)
private val COMPACT_PADDING = PaddingValues(horizontal = 10.dp, vertical = 0.dp)

/** A bottom-bar button: filled (Step/Run) or outlined; low and tight when [compact]. */
@Composable
private fun CtlButton(
    label: String,
    compact: Boolean,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val mod = if (compact) Modifier.height(COMPACT_HEIGHT) else Modifier
    if (filled) {
        if (compact) Button(onClick, mod, enabled, contentPadding = COMPACT_PADDING) { Text(label) }
        else Button(onClick, mod, enabled) { Text(label) }
    } else {
        if (compact) OutlinedButton(onClick, mod, enabled, contentPadding = COMPACT_PADDING) { Text(label) }
        else OutlinedButton(onClick, mod, enabled) { Text(label) }
    }
}

// steps/sec range (float, like pytoy's QDoubleSpinBox).
private const val SPS_MIN = 0.1
private const val SPS_MAX = 1000.0
private const val SPS_STEP = 0.5

/**
 * A −/+ stepper for the Run speed (steps per second). A Double, shown with one
 * decimal so the user sees that sub-1 values are allowed (e.g. "0.5"). The field
 * accepts any float in [SPS_MIN, SPS_MAX]; the buttons nudge by [SPS_STEP].
 * pytoy's spin_speed. [compact] (phones) uses
 * a low bordered field instead of the 56 dp Material text field.
 */
@Composable
private fun StepsPerSecInput(value: Double, compact: Boolean, onValueChange: (Double) -> Unit) {
    Text("steps/sec:", style = MonoStyle)
    // Local edit buffer so a half-typed value (e.g. "0.") isn't clobbered while
    // typing. Re-sync it from [value] only when a ± button moved the value away
    // from what the buffer parses to — so button taps update the field but typing
    // doesn't fight itself.
    var text by remember { mutableStateOf(fmt1(value)) }
    LaunchedEffect(value) {
        if (text.toDoubleOrNull() != value) text = fmt1(value)
    }
    val onText: (String) -> Unit = {
        text = it
        // Only commit a value that's already IN range, so an in-progress
        // keystroke ("0." → 0.0, below SPS_MIN) isn't coerced up and then
        // written back over what the user is typing. The field re-clamps on
        // the ± buttons / resync.
        it.toDoubleOrNull()?.let { v ->
            if (v in SPS_MIN..SPS_MAX) onValueChange(v)
        }
    }
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 0.dp),
    ) {
        // Text "−"/"+" buttons rather than material-icons, to avoid pulling in the
        // icons dependency (ktoy keeps its deps minimal).
        CtlButton("−", compact, enabled = value > SPS_MIN) {
            onValueChange((value - SPS_STEP).coerceIn(SPS_MIN, SPS_MAX))
        }
        // as high as the buttons next to it (Material's OutlinedTextField is
        // 56 dp, far taller than the number in it)
        BasicTextField(
            value = text,
            onValueChange = onText,
            singleLine = true,
            textStyle = MonoStyle,
            modifier = Modifier
                .width(if (compact) 64.dp else 80.dp)
                .height(if (compact) COMPACT_HEIGHT else ButtonDefaults.MinHeight)
                .testTag("steps-per-sec"),
            // centre the text vertically instead of fixed padding, so a larger
            // system font scale doesn't clip it
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxSize()
                        .border(1.dp, Color(0xFF888888), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp),
                    contentAlignment = androidx.compose.ui.Alignment.CenterStart,
                ) { inner() }
            },
        )
        CtlButton("+", compact, enabled = value < SPS_MAX) {
            onValueChange((value + SPS_STEP).coerceIn(SPS_MIN, SPS_MAX))
        }
    }
}

/** Format a Double with one decimal (e.g. 2.0, 0.5) without platform printf. */
private fun fmt1(v: Double): String {
    val scaled = kotlin.math.round(v * 10).toLong()
    return "${scaled / 10}.${kotlin.math.abs(scaled % 10)}"
}

/** 8-bit binary string (unsigned), like pytoy's f"{v:08b}". Delegates to the one
 *  binary formatter in Format.kt so the memory panel and the I/O panel can't drift. */
private fun bin8(v: Int): String = formatByte(v, IoFormat.BINARY)
