package org.bytefred.ktoy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilterChip
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
    // true while the C-edit dialog is open (double-tap the C column on desktop/web)
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
        // Menu bar (File / View / Help) + the current program title. Replaces the
        // old inline Load button and I/O toggle (now menu items). pytoy's menus.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            SimMenuBar(vm, onBrowseExamples = onLoad)
            // The title must never push the menu off-screen or wrap the bar: cap
            // it to one line + ellipsis and let it shrink (weight) so a long
            // program name is truncated, not overflowed. General layout fix
            // (web + jvm), not target-specific.
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
            // How many columns are actually shown (C only for a C load; asm only
            // with source; memory always).
            val cols = (if (vm.hasCSource) 1 else 0) +
                (if (vm.hasSource) 1 else 0) + 1
            // Side-by-side vs stacked. AUTO decides purely by WIDTH so it reacts
            // to window resize (an aspect test made a tall-narrow window wrongly
            // go side-by-side and not switch back): side-by-side once the window
            // is wide enough to give each shown column a usable minimum width,
            // stacked otherwise. ~300.dp/column is the floor the panels need
            // before text clips. PORTRAIT / LANDSCAPE override the auto choice
            // (View -> Layout).
            val minPerCol = 300.dp
            val wide = when (vm.layoutMode) {
                LayoutMode.LANDSCAPE -> true
                LayoutMode.PORTRAIT -> false
                LayoutMode.AUTO -> maxWidth >= minPerCol * cols
            }
            val pcCLine = vm.pcCLine()
            val cCol: @Composable (Modifier) -> Unit = { mod ->
                CSourcePanel(vm, sel.cLines, changedCLines, pcCLine, { editCOpen = true }, mod)
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
            } else if (maxWidth < TABS_MAX_WIDTH && cols > 1) {
                // Phones: one panel at a time, chosen by tabs, so it gets the
                // full height instead of a third of it.
                val tab = vm.visibleTab()
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (vm.hasCSource) PanelTabChip("C", tab == PanelTab.C) { vm.phoneTab = PanelTab.C }
                        if (vm.hasSource) PanelTabChip("Assembly", tab == PanelTab.ASM) { vm.phoneTab = PanelTab.ASM }
                        PanelTabChip("Memory", tab == PanelTab.MEMORY) { vm.phoneTab = PanelTab.MEMORY }
                    }
                    val fill = Modifier.weight(1f).fillMaxWidth()
                    when (tab) {
                        PanelTab.C -> cCol(fill)
                        PanelTab.ASM -> src(fill)
                        PanelTab.MEMORY -> mem(fill)
                    }
                }
            } else {
                // Stacked: memory gets twice the height of C / assembly, since it
                // carries the status block on top of the 256-byte listing.
                Column(Modifier.fillMaxSize()) {
                    if (vm.hasCSource) cCol(Modifier.weight(1f).fillMaxWidth())
                    if (vm.hasSource) src(Modifier.weight(1f).fillMaxWidth())
                    mem(Modifier.weight(2f).fillMaxWidth())
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

        Controls(
            vm,
            onLoad = onLoad,
            onEdit = { vm.memSelectedAddr?.let { editTarget = it } },
        )
    }

    editTarget?.let { addr ->
        EditValueDialog(
            addr = addr,
            current = m.mem[addr],
            onDismiss = { editTarget = null },
            onConfirm = { value -> vm.editCell(addr, value); editTarget = null },
        )
    }

    // C-edit dialog: double-tap the C column to edit + recompile (works on both
    // desktop/web and touch). applyEditedC returns null on success (dialog
    // closes) or the error message (dialog stays open).
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
    // keep the focused asm line centred: a selected row (a click elsewhere
    // resolved here) wins, else the current PC line while stepping / running
    // (like pytoy's ensureCursorVisible). Fires only when the line changes, so
    // tapping doesn't fight the view.
    val focusLine = selAsmRows.minOrNull() ?: pcLines.minOrNull()
    scrollToLine(vScroll, focusLine)
    // display-only line-number gutter (pytoy's _refresh_source), toggled from
    // View -> Line numbers. Never part of any saved file; labels, not numbers,
    // are how asm refers to lines.
    val gutterWidth = vm.listing.size.toString().length.coerceAtLeast(1)
    ScrollbarBox(vScroll, modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize().padding(4.dp)
        .verticalScroll(vScroll)
        .horizontalScroll(hScroll)) {
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
            HighlightRow(gutter + text, colours, rowMod)
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
    // scroll so a selected cell (or, failing that, the PC) is visible+centred
    // when a click elsewhere resolves here. +1 for the header row above address 0.
    val focusAddr = selAddrs.minOrNull() ?: m.pc
    scrollToLine(vScroll, focusAddr + 1)
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
            // the transitive selection: a C-line click lights up ALL the bytes of
            // the statement here; an asm/memory click lights only the clicked
            // row's own bytes (pytoy's sel_addrs)
            val colours = memoryCellColours(a, selAddrs, pcBytes, argAddr, m.changedCell, m.stopped)
            // untouched, non-program bytes are shown greyed out
            val textColor = if (a in usedAddrs) Color.Black else Color(0xFFAAAAAA)
            // single-tap selects the cell; double-tap (desktop/web) edits it.
            // mobile edits via the bottom-bar Edit button, not a long-press.
            Box(
                Modifier.fillMaxWidth().pointerInput(a) {
                    detectTapGestures(
                        onTap = { vm.select(a, fromMemory = true) },
                        onDoubleTap = { onEdit(a) },
                    )
                }
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
 * STORE-changed statement in red (HL_CODE). Single-tap a line to select it across
 * all columns (vm.selectCLine); DOUBLE-TAP opens the C-edit dialog (works on
 * desktop/web and touch — no long-press). Mirrors pytoy's c_box + _refresh_c.
 * Shown only when a C program is loaded.
 */
@Composable
private fun CSourcePanel(
    vm: SimViewModel,
    selCLines: Set<Int>,
    changedCLines: Set<Int>,
    pcCLine: Int?,
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
        // keep the selected C line visible (centred) when a click in another
        // column resolves to a C statement here; falls back to the PC's C line.
        scrollToLine(vScroll, selCLines.minOrNull()?.minus(1) ?: pcCLine?.minus(1))
        ScrollbarBox(vScroll, Modifier.fillMaxSize()) {
          Column(
              Modifier.fillMaxSize().padding(4.dp)
                  .verticalScroll(vScroll)
                  .horizontalScroll(rememberScrollState())
          ) {
            lines.forEachIndexed { i, line ->
                val cLine = i + 1   // 1-based, matching the markers
                val colours = mutableListOf<Color>()
                if (cLine in selCLines) colours.add(HL_SELECTED)
                if (cLine == pcCLine) colours.add(HL_PC)
                if (cLine in changedCLines) colours.add(HL_CODE)
                val gutter = if (vm.showLineNumbers)
                    "${cLine.toString().padStart(gutterWidth)} | " else ""
                // single-tap selects the C line; double-tap (desktop/web) opens
                // the C edit dialog. Per-line gesture (not on the container) so a
                // child tap handler can't swallow the double-tap. Mobile edits via
                // the bottom-bar Edit button (memory cell), same as pytoy.
                HighlightRow(
                    gutter + line,
                    colours,
                    Modifier.pointerInput(cLine) {
                        detectTapGestures(
                            onTap = { vm.selectCLine(cLine) },
                            onDoubleTap = { onEditC() },
                        )
                    },
                )
            }
          }
        }
    }
}

/**
 * Scroll [state] so the row at [lineIndex] (0-based) is roughly centred in the
 * viewport, whenever [lineIndex] changes. Used so a click in one column scrolls
 * the others to the matching line. Approximates row height (mono ~34px); null
 * leaves the scroll untouched.
 */
@Composable
private fun scrollToLine(state: androidx.compose.foundation.ScrollState, lineIndex: Int?) {
    LaunchedEffect(lineIndex) {
        if (lineIndex != null) {
            val lineHeightPx = 34
            // centre the line: subtract half the viewport so it lands mid-screen,
            // not at the very top.
            val target = (lineIndex * lineHeightPx) -
                (state.viewportSize / 2) + (lineHeightPx / 2)
            state.animateScrollTo(target.coerceIn(0, state.maxValue))
        }
    }
}

/**
 * The bottom controls bar, in pytoy's order:
 *   Load · Examples · Reload — GAP — Step · Run · steps/sec · Undo · Reset — Edit
 * (Edit pushed all the way to the right). Load opens the platform file picker,
 * Examples opens the bundled example list ([onLoad]), Reload re-runs the last
 * file. Two weight spacers hold the gap after the file group and keep Edit at
 * the far right. Edit is the mobile entry point for editing a selected cell
 * (desktop/web also double-tap the cell).
 */
@Composable
private fun Controls(vm: SimViewModel, onLoad: () -> Unit, onEdit: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick  // recompose when canUndo/stopped change
    val m = vm.machine
    val loadFromDisk = rememberFileLoader { name, text -> vm.loadFromFile(name, text) }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val oneRowMin = if (fileReloadSupported) CONTROLS_ONE_ROW_MIN_RELOAD else CONTROLS_ONE_ROW_MIN
        // narrow screens (phones) get two rows of compact, low controls so the
        // panels keep as much height as possible
        val compact = maxWidth < oneRowMin

        // Order like pytoy: Load Examples Reload | Step Run steps/sec Undo Reset | Edit.
        val fileGroup: @Composable () -> Unit = {
            if (fileLoadSupported) CtlButton("Load", compact, onClick = loadFromDisk)
            CtlButton("Examples", compact, onClick = onLoad)
            if (fileReloadSupported) {
                CtlButton("Reload", compact, enabled = vm.canReload) { vm.reload() }
            }
        }
        val runGroup: @Composable () -> Unit = {
            CtlButton("Step", compact, filled = true, enabled = m?.stopped == false && !vm.running) { vm.step() }
            CtlButton(if (vm.running) "Stop" else "Run", compact, filled = true, enabled = m?.stopped == false) {
                vm.toggleRun()
            }
            StepsPerSecInput(vm.stepsPerSecond, compact) { vm.stepsPerSecond = it }
            CtlButton("Undo", compact, enabled = m?.canUndo == true) { vm.stepBack() }
            CtlButton("Reset", compact, enabled = m?.canUndo == true) { vm.reset() }
        }
        // Edit the value at the address selected in the CPU & memory panel;
        // enabled only when such a selection exists. On touch this is THE way to
        // edit (double-tap is the desktop/web shortcut), so it must never be clipped.
        val edit: @Composable () -> Unit = {
            CtlButton("Edit", compact, enabled = vm.memSelectedAddr != null, onClick = onEdit)
        }
        val center = androidx.compose.ui.Alignment.CenterVertically
        val gap = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)

        if (!compact) {
            // wide: one row, gaps pushing the run group to the middle and Edit right
            Row(Modifier.fillMaxWidth(), verticalAlignment = center, horizontalArrangement = gap) {
                fileGroup()
                Box(Modifier.weight(1f))
                runGroup()
                Box(Modifier.weight(1f))
                edit()
            }
        } else {
            // narrow: two rows so nothing is squeezed or cut off. Row 1: file
            // group + Edit far right; row 2: run controls, scrollable if they
            // still don't fit.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = center, horizontalArrangement = gap) {
                    fileGroup()
                    Box(Modifier.weight(1f))
                    edit()
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = center,
                    horizontalArrangement = gap,
                ) {
                    runGroup()
                }
            }
        }
    }
}

// Below this width a stacked layout shows one panel at a time (tabs), so each
// gets the full height. Phones are ~360-430 dp wide, small tablets ~600 dp.
private val TABS_MAX_WIDTH = 600.dp

/** One tab of the phone panel switcher. */
@Composable
private fun PanelTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

// Below this width the bottom bar splits into two compact rows. The one-row bar
// needs about 950 dp of content (Material3 button min widths + the 96 dp
// steps/sec field), plus ~100 dp more when Reload is shown (desktop).
private val CONTROLS_ONE_ROW_MIN = 1000.dp
private val CONTROLS_ONE_ROW_MIN_RELOAD = 1100.dp

// Height of a compact (phone) control, instead of Material3's 40 dp buttons and
// the 56 dp text field.
private val COMPACT_HEIGHT = 34.dp
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
 * Shape copied from kotlin-common's IntegerInput, adapted to Float (no shared
 * dependency, per the house rules). pytoy's spin_speed. [compact] (phones) uses
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
        if (compact) {
            BasicTextField(
                value = text,
                onValueChange = onText,
                singleLine = true,
                textStyle = MonoStyle,
                modifier = Modifier.width(64.dp).height(COMPACT_HEIGHT),
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
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                singleLine = true,
                modifier = Modifier.width(96.dp),
            )
        }
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
