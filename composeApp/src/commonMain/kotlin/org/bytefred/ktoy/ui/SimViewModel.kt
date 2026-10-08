package org.bytefred.ktoy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.bytefred.ktoy.core.AssemblyResult
import org.bytefred.ktoy.core.BuildOutcome
import org.bytefred.ktoy.core.CProgram
import org.bytefred.ktoy.core.Example
import org.bytefred.ktoy.core.ExampleKind
import org.bytefred.ktoy.core.IoFormat
import org.bytefred.ktoy.core.ListingLine
import org.bytefred.ktoy.core.Machine
import org.bytefred.ktoy.core.SelectionSets
import org.bytefred.ktoy.core.addrToCLine
import org.bytefred.ktoy.core.asmRowsToAddrs
import org.bytefred.ktoy.core.assemble
import org.bytefred.ktoy.core.detectCompilers
import org.bytefred.ktoy.core.parseToyo
import org.bytefred.ktoy.core.transitiveSelection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Holds the running Machine and the derived UI state for the simulator screen.
 * Being a ViewModel, it survives Android configuration changes (rotation), so
 * the program keeps its ACC/PC/step state when the phone is turned — matching
 * the spec's "rotation must not reset" rule.
 *
 * A UI-visible [tick] counter is bumped on every state change so Compose
 * recomposes (Machine itself is plain mutable state, not observable).
 *
 * For a C program the view is three columns (C | asm | memory) with transitive
 * highlighting: a click in any column resolves across all present levels via the
 * `# @c:N` C↔asm map composed with the listing's asm↔bytecode map. The C state
 * (text, maps, compiler) lives here so it survives rotation and drives both the
 * extra column and the compiler-switch / C-edit actions. Ported from pytoy's
 * three-column ToyDebugger (simulator.py).
 */
class SimViewModel : ViewModel() {

    var machine: Machine? = null; private set
    var listing: List<ListingLine> = emptyList(); private set
    var syms: Map<String, Int> = emptyMap(); private set
    var hasSource: Boolean = true; private set
    var title: String = ""; private set

    // ── C-source state (null / empty for an ASM or TOYO load) ────────────────
    // The loaded C program (source + compiler + C↔asm maps), or null. hasCSource
    // mirrors it so the C column shows only for a C load. See loadCProgram /
    // clearCSource.
    var cText: String? = null; private set
    var currentCompiler: String? = null; private set
    var hasCSource: Boolean = false; private set
    var cToAsm: Map<Int, List<Int>> = emptyMap(); private set
    var asmToC: Map<Int, Int> = emptyMap(); private set

    // The shared cross-column selection anchors: a C line and/or a byte address.
    // Both null means nothing is selected (deselect clears everything). The
    // machine's own selectedAddr follows `selectedAddr` so the memory panel keeps
    // its anchor highlight; selectedCLine adds the C column.
    var selectedCLine: Int? = null; private set
    var selectedAddr: Int? = null; private set

    // An error to surface in a dialog (failed compiler switch / C edit / load).
    // The UI shows it and calls clearError() on dismiss.
    var errorMessage: String? by mutableStateOf(null); private set

    // bumped on every mutation so composables that read it recompose
    var tick by mutableIntStateOf(0); private set

    var running by mutableStateOf(false); private set
    var stepsPerSecond by mutableStateOf(2.0)

    // I/O panel: whether it's shown, and its display format. Both live here (not
    // in the composable) so they survive rotation like the rest of the sim state.
    var showIo by mutableStateOf(false)
    var ioFormat by mutableStateOf(IoFormat.DECIMAL)

    // the auto-run coroutine, kept so it can be cancelled the instant any other
    // action touches the machine (load / reset / manual step / edit) — otherwise
    // a loop sitting in delay() could wake and step a just-swapped machine.
    private var runJob: Job? = null

    private fun bump() { tick += 1 }

    /** Stop auto-run: cancel the loop and clear the flag. Safe to call anytime. */
    private fun stopRun() {
        runJob?.cancel()
        runJob = null
        running = false
    }

    /** True once any program (or the empty image) has been loaded. */
    val hasProgram: Boolean get() = machine != null

    // ── addr ↔ listing-row maps (derived from the current listing) ───────────
    // Each ListingLine carries its start addr; a two-byte instruction also owns
    // addr+1. Rebuilt whenever the listing changes (every load / switch / edit).
    private var lineToAddr: Map<Int, Int> = emptyMap()
    private var addrToLine: Map<Int, Int> = emptyMap()

    private fun rebuildAddrMaps() {
        val l2a = mutableMapOf<Int, Int>()
        val a2l = mutableMapOf<Int, Int>()
        listing.forEachIndexed { i, line ->
            val a = line.addr ?: return@forEachIndexed
            l2a[i] = a
            a2l[a] = i
            if (line.bytes.size == 2) a2l[a + 1] = i
        }
        lineToAddr = l2a
        addrToLine = a2l
    }

    /**
     * Start with an empty, zeroed 256-byte memory and no source — like pytoy's
     * empty_gui(). The user then loads an example via the Load button.
     */
    fun loadEmpty() {
        stopRun()
        title = "(empty)"
        val mem = IntArray(256)
        listing = (0..255).map { ListingLine(it, listOf(mem[it]), "", true) }
        syms = emptyMap()
        hasSource = false
        machine = Machine(mem, (0..255).toSet())
        clearCSource()
        rebuildAddrMaps()
        bump()
    }

    /**
     * Load an example:
     *   - ASM  — assemble the source directly.
     *   - C    — compile the C source to assembly first (preferring toycc, else
     *            whichever compiler the example declares), keeping the C source
     *            and the C↔asm maps so the three-column view and the switch /
     *            edit actions work. A compile/assemble failure surfaces an error
     *            and leaves the previous program untouched.
     *   - TOYO — parse the source as a byte image.
     */
    fun load(example: Example) {
        stopRun()
        when (example.kind) {
            ExampleKind.TOYO -> {
                val mem = parseToyo(example.source)
                title = example.name
                listing = (0..255).map { ListingLine(it, listOf(mem[it]), "", true) }
                syms = emptyMap()
                hasSource = false
                machine = Machine(mem, (0..255).toSet())
                clearCSource()
                rebuildAddrMaps()
            }
            ExampleKind.ASM -> {
                val res: AssemblyResult = assemble(example.source)
                title = example.name
                listing = res.listing
                syms = res.syms
                hasSource = true
                machine = Machine(res.mem, res.dataAddrs)
                clearCSource()
                rebuildAddrMaps()
            }
            ExampleKind.C -> {
                // Prefer the full compiler (toycc) when the example lists it, so a
                // program that both accept opens on toycc (matching pytoy's Load).
                val compiler = if ("toycc" in example.compilers) "toycc"
                else example.compilers.firstOrNull() ?: "toycc"
                when (val outcome = CProgram.build(example.source, compiler)) {
                    is BuildOutcome.Success -> {
                        title = example.name
                        loadCProgram(outcome.program)
                    }
                    is BuildOutcome.Failure -> {
                        errorMessage = outcome.error
                        return  // leave the current program untouched
                    }
                }
            }
        }
        bump()
    }

    /** Point the simulator at a built C program: new machine + listing + C maps. */
    private fun loadCProgram(p: CProgram) {
        listing = p.result.listing
        syms = p.result.syms
        hasSource = true
        machine = Machine(p.result.mem, p.result.dataAddrs)
        cText = p.cText
        currentCompiler = p.compiler
        cToAsm = p.cToAsm
        asmToC = p.asmToC
        hasCSource = true
        selectedCLine = null
        selectedAddr = null
        rebuildAddrMaps()
    }

    /** Drop any C context (an ASM / TOYO / empty load). */
    private fun clearCSource() {
        cText = null
        currentCompiler = null
        cToAsm = emptyMap()
        asmToC = emptyMap()
        hasCSource = false
        selectedCLine = null
    }

    fun step() {
        stopRun()
        machine?.step()
        // stepping drops the whole cross-column selection so a stale highlight
        // can't linger after execution moves on (pytoy clears both anchors)
        selectedCLine = null
        selectedAddr = null
        memSelectedAddr = null
        bump()
    }

    fun stepBack() {
        stopRun()
        machine?.stepBack()
        selectedCLine = null
        selectedAddr = null
        memSelectedAddr = null
        bump()
    }

    fun reset() {
        stopRun()
        machine?.reset()
        selectedCLine = null
        selectedAddr = null
        memSelectedAddr = null
        bump()
    }

    // The address selected specifically in the memory (CPU & memory) panel, or
    // null. Distinct from `selectedAddr` (which a source- or C-line tap also
    // sets) so the Edit button is enabled only for a memory selection.
    var memSelectedAddr: Int? = null; private set

    /**
     * Set the shared cross-column selection from a clicked byte address (an asm
     * or memory tap). The address resolves UP to its C line so the C column
     * lights up too; a null/unmapped address clears everything (deselect). Pass
     * [fromMemory]=true for a memory-panel tap (enables the Edit button); an asm
     * tap passes false.
     */
    fun select(addr: Int?, fromMemory: Boolean = false) {
        if (addr == null) {
            clearSelection(fromMemory)
            return
        }
        selectedAddr = addr
        selectedCLine = addrToCLine(addr, addrToLine, asmToC)
        machine?.select(addr)
        memSelectedAddr = if (fromMemory) addr else null
        bump()
    }

    /**
     * Set the selection from a clicked C line. The line resolves DOWN to its asm
     * rows and their bytes (the first owned byte becomes the anchor address so
     * the asm/memory panels get their usual highlight). A C line with no asm
     * origin clears the selection. Mirrors pytoy's _c_clicked.
     */
    fun selectCLine(cLine: Int?) {
        val rows = cLine?.let { cToAsm[it] }
        if (cLine == null || rows == null || rows.isEmpty()) {
            clearSelection(fromMemory = false)
            return
        }
        val addrs = asmRowsToAddrs(rows.toSet(), lineToAddr)
        selectedCLine = cLine
        selectedAddr = addrs.minOrNull()
        machine?.select(selectedAddr)
        memSelectedAddr = null
        bump()
    }

    private fun clearSelection(fromMemory: Boolean) {
        selectedCLine = null
        selectedAddr = null
        machine?.select(null)
        memSelectedAddr = null
        bump()
    }

    /** The transitive highlight sets for the current selection, shared by all
     *  three panels so one click lights up consistently everywhere. */
    fun selectionSets(): SelectionSets = transitiveSelection(
        selectedCLine, selectedAddr, cToAsm, asmToC, lineToAddr, addrToLine,
    )

    /**
     * The transitive "changed" sets (STORE → red up): the asm row and C line
     * owning the cell a STORE just overwrote, so the change shows red on the asm
     * line and the C line, not only on the byte. Empty while stopped or when
     * nothing changed. Returns (cLines, asmRows). Mirrors pytoy's _change_sets.
     */
    fun changeSets(): Pair<Set<Int>, Set<Int>> {
        val m = machine ?: return emptySet<Int>() to emptySet()
        val changed = if (m.stopped) null else m.changedCell
        if (changed == null) return emptySet<Int>() to emptySet()
        val row = addrToLine[changed] ?: return emptySet<Int>() to emptySet()
        val cLine = asmToC[row]
        val cLines = if (cLine != null) setOf(cLine) else emptySet()
        return cLines to setOf(row)
    }

    fun editCell(addr: Int, value: Int) {
        // Deliberately does NOT stop auto-run: editing an I/O cell while the
        // program busy-polls `ready` is how the user feeds input live and the
        // running program reacts (matches pytoy's _edit_cell, which leaves its
        // timer running). Stopping here would defeat the I/O panel's whole point.
        machine?.editCell(addr, value)
        // resolve the edited byte up to its C line so the C column highlights the
        // owning statement too (consistent with a memory-panel click).
        selectedAddr = addr
        selectedCLine = addrToCLine(addr, addrToLine, asmToC)
        bump()
    }

    // ── Part 3: compiler switch + C edit ─────────────────────────────────────

    /**
     * Recompile the loaded C with the OTHER compiler (full C toycc ↔ mini C
     * minicc), in place. The target must both compile AND assemble within 240
     * bytes before anything changes: minicc can reject recursion (a compile
     * error) or overflow (an assemble error). On either failure an error is
     * surfaced and the simulator stays exactly on the current compiler/program —
     * no half-load. On success the program reloads at step 0 with fresh maps.
     * Mirrors pytoy's _switch_compiler.
     */
    fun switchCompiler() {
        val c = cText ?: return
        val cur = currentCompiler ?: return
        stopRun()
        val target = if (cur == "toycc") "minicc" else "toycc"
        // CProgram.build is all-or-nothing: on failure nothing here changes.
        when (val outcome = CProgram.build(c, target)) {
            is BuildOutcome.Success -> {
                loadCProgram(outcome.program)
                bump()
            }
            is BuildOutcome.Failure -> {
                errorMessage = outcome.error
            }
        }
    }

    /**
     * Recompile edited C with the current compiler (falling back to detection,
     * preferring toycc, if none is pinned), annotating C lines. On a compile or
     * assemble error returns the message and changes NOTHING (the UI keeps its
     * edit dialog open); on success swaps in the new program + maps + cText and
     * returns null. Mirrors pytoy's EditCDialog save.
     */
    fun applyEditedC(newC: String): String? {
        stopRun()
        val compiler = currentCompiler ?: run {
            val accepted = detectCompilers(newC)
            if ("toycc" in accepted) "toycc" else accepted.firstOrNull() ?: "toycc"
        }
        return when (val outcome = CProgram.build(newC, compiler)) {
            is BuildOutcome.Success -> {
                loadCProgram(outcome.program)
                bump()
                null
            }
            is BuildOutcome.Failure -> outcome.error
        }
    }

    /** Dismiss the current error dialog. */
    fun clearError() { errorMessage = null }

    fun toggleRun() {
        if (running) {
            stopRun()
            return
        }
        val m = machine ?: return
        if (m.stopped) return
        running = true
        runJob = viewModelScope.launch {
            while (isActive) {
                val advanced = m.step()   // capture m: never step a swapped machine
                bump()
                if (!advanced) { running = false; runJob = null; break }
                val sps = stepsPerSecond.coerceIn(0.1, 1000.0)
                delay((1000.0 / sps).toLong())
            }
        }
    }
}
