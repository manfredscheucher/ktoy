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
import org.bytefred.ktoy.core.EXAMPLE_SOURCES
import org.bytefred.ktoy.core.Example
import org.bytefred.ktoy.core.ExampleKind
import org.bytefred.ktoy.core.IoFormat
import org.bytefred.ktoy.core.ListingLine
import org.bytefred.ktoy.core.Machine
import org.bytefred.ktoy.core.decode
import org.bytefred.ktoy.core.SelectionSets
import org.bytefred.ktoy.core.addrToCLine
import org.bytefred.ktoy.core.asmRowsToAddrs
import org.bytefred.ktoy.core.assemble
import org.bytefred.ktoy.core.decompile
import org.bytefred.ktoy.core.detectCompilers
import org.bytefred.ktoy.core.bytecodeText
import org.bytefred.ktoy.core.parseBytecodeEdit
import org.bytefred.ktoy.core.parseToyo
import org.bytefred.ktoy.core.transitiveSelection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How the C/asm/memory columns are arranged (View -> Layout). */
enum class LayoutMode { AUTO, PORTRAIT, LANDSCAPE }


/** The simulator's panels (C / assembly / CPU & memory), e.g. as divider-share keys. */
enum class Panel { C, ASM, MEMORY }

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
    var title: String = ""; private set

    // The current assembly source text (for .toys Save), or null for a .toyo load
    // with no source. Mirrors pytoy's _asm_text.
    var asmText: String? = null; private set
    val hasSource: Boolean get() = asmText != null

    // ── File-menu state ───────────────────────────────────────────────────────
    // The last file opened from disk (name + raw text), so Reload
    // can re-run it. null until a file is loaded from disk (examples don't set it).
    var currentFileName: String? = null; private set
    private var currentFileText: String? = null
    // where it is on disk (desktop only), so Reload reads the file again
    private var currentFilePath: String? = null

    // View -> Line numbers (the gutter in the source panels). On by default.
    // The View settings are remembered across starts (saved by the View menu).
    var showLineNumbers by mutableStateOf(loadPref(LINE_NUMBERS_PREF) != "false")

    // View -> Layout: AUTO picks side-by-side vs stacked from the window size;
    // PORTRAIT forces the columns stacked; LANDSCAPE forces them side by side.
    var layoutMode by mutableStateOf(LayoutMode.entries.firstOrNull { it.name == loadPref(LAYOUT_PREF) } ?: LayoutMode.AUTO)

    // View -> Auto-scroll to PC: on Step / Run every panel scrolls so the PC's
    // line (">>") is centred.
    var autoScroll by mutableStateOf(loadPref(AUTO_SCROLL_PREF) != "false")

    // View -> Memory: single column (default) or a 16 / 8 column grid of values.
    var memoryView by mutableStateOf(MemoryView.entries.firstOrNull { it.name == loadPref(MEMORY_VIEW_PREF) } ?: MemoryView.SINGLE_COLUMN)

    // Panel shares set by dragging the dividers, one map for side by side and
    // one for stacked. Equal by default; stacked memory gets 2 (it carries the
    // status block on top of the 256-byte listing).
    var wideWeights by mutableStateOf(mapOf(Panel.C to 1f, Panel.ASM to 1f, Panel.MEMORY to 1f))
    var stackedWeights by mutableStateOf(mapOf(Panel.C to 1f, Panel.ASM to 1f, Panel.MEMORY to 2f))

    /**
     * True if the loaded program touches the memory-mapped I/O cells (an
     * instruction operand in 240..255, e.g. `load ready` or `store io0`). Used
     * to show the I/O panel automatically for a program that isn't a bundled
     * example (examples state it in their `io:` header); it starts hidden.
     */
    fun usesIo(): Boolean = listing.any { line ->
        !line.isData && line.bytes.size == 2 && line.bytes[1] >= 240
    }

    // A transient status message for the File menu (e.g. "Saved foo.toyo"), shown
    // briefly in the UI. Cleared by the UI after showing.
    var statusMessage: String? by mutableStateOf(null); private set
    fun clearStatus() { statusMessage = null }
    fun setStatus(msg: String?) { statusMessage = msg }

    // ── C-source state (null / empty for an ASM or TOYO load) ────────────────
    // The loaded C program (source + compiler + C↔asm maps), or null. See
    // loadCProgram / clearCSource.
    var cText: String? = null; private set
    var currentCompiler: String? = null; private set
    val hasCSource: Boolean get() = cText != null
    var cToAsm: Map<Int, List<Int>> = emptyMap(); private set
    var asmToC: Map<Int, Int> = emptyMap(); private set

    // The shared cross-column selection anchors: a C line and/or a byte address.
    // Both null means nothing is selected (deselect clears everything). The
    // machine's own selectedAddr follows `selectedAddr` so the memory panel keeps
    // its anchor highlight; selectedCLine adds the C column.
    var selectedCLine: Int? = null; private set
    var selectedAddr: Int? = null; private set
    // true when the current selection was made by clicking a C line (fan DOWN to
    // all its asm rows); false for an asm-row / memory-cell click (single row,
    // resolve UP to the C line only, no sibling-row fan-out). See select /
    // selectCLine and transitiveSelection(fromC=…).
    private var selectedFromC: Boolean = true

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

    // For a program that isn't a bundled example, the I/O panel opens by itself
    // at most once per load: when the running program first reads/writes an I/O
    // cell (catches self-modifying code, C write() via a copy loop and bytecode,
    // which the load-time check misses). Once the user toggles the panel, it
    // stays as they set it. A bundled example follows its `io:` header instead.
    private var ioAutoDone = false

    /** View -> I/O panel. */
    fun toggleIo() {
        showIo = !showIo
        ioAutoDone = true
    }

    /** Before stepping [m]: open the I/O panel if this instruction accesses 240..255. */
    private fun autoShowIo(m: Machine) {
        if (ioAutoDone || m.stopped) return
        val (instr, arg, _) = decode(m.mem, m.pc)
        if (instr in 17..23 && arg != null && arg >= 240) {
            showIo = true
            ioAutoDone = true
        }
    }
    // ASCII by default (the user reads I/O as characters most of the time).
    var ioFormat by mutableStateOf(IoFormat.ASCII)

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

    /** True once a program has been loaded. */
    val hasProgram: Boolean get() = machine != null

    // ── addr ↔ listing-row maps (derived from the current listing) ───────────
    // Each ListingLine carries its start addr; a two-byte instruction also owns
    // addr+1. Rebuilt whenever the listing changes (every load / switch / edit).
    private var lineToAddr: Map<Int, Int> = emptyMap()
    var addrToLine: Map<Int, Int> = emptyMap(); private set

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

    /** File -> Empty: an empty program of [kind] (C: main returns 0, assembly: stop, bytecode: zeros). */
    fun loadEmpty(kind: ExampleKind) {
        val (name, source) = when (kind) {
            ExampleKind.C -> "empty.toyc" to "int main(void) {\n    return 0;\n}\n"
            ExampleKind.ASM -> "empty.toys" to "        stop\n"
            ExampleKind.TOYO -> "empty.toyo" to ""
        }
        load(Example(name, kind, source, if (kind == ExampleKind.C) detectCompilers(source) else emptyList()))
        clearCurrentFile()
    }

    /**
     * The program shown at app start: the 00_stop example. Read from
     * EXAMPLE_SOURCES, so app start never compiles the C examples.
     */
    fun loadStartExample() = load(EXAMPLE_SOURCES.first { it.name == START_EXAMPLE && it.kind == ExampleKind.ASM })

    // ── loading a file from disk / device ─────────────────────────────────────

    private fun clearCurrentFile() {
        currentFileName = null
        currentFileText = null
        currentFilePath = null
    }

    /** Infer a program's kind from its file name extension (default: ASM). */
    private fun kindOf(name: String): ExampleKind = when {
        name.endsWith(".toyc", ignoreCase = true) -> ExampleKind.C
        name.endsWith(".toyo", ignoreCase = true) -> ExampleKind.TOYO
        else -> ExampleKind.ASM   // .toys and anything else
    }

    /**
     * Load a program the user picked from disk (name + raw text). The kind is
     * inferred from the extension. On a compile/assemble failure the previous
     * program is left intact and an error is surfaced (same as example loading).
     * Records the file so Reload can re-run it ([path]: where it is, if the
     * platform has paths).
     */
    fun loadFromFile(fileName: String, text: String, path: String? = null) {
        val kind = kindOf(fileName)
        val compilers = if (kind == ExampleKind.C) detectCompilers(text) else emptyList()
        if (kind == ExampleKind.C && compilers.isEmpty()) {
            errorMessage = "Neither minicc nor toycc accepts this C source."
            return
        }
        load(Example(fileName, kind, text, compilers))
        // load() clears errorMessage up front and only sets it on failure, so a
        // null error means the load took — record the file for Reload only then.
        if (errorMessage == null) {
            currentFileName = fileName
            currentFileText = text
            currentFilePath = path
            title = fileName
            bump()
        }
    }

    /** True if there is a disk file to reload (not just an example). */
    val canReload: Boolean get() = currentFileText != null

    /** Run the last file again, read anew from disk where there is a path. No-op if none. */
    fun reload(read: (String) -> String? = ::readTextFile) {
        val name = currentFileName ?: return
        val path = currentFilePath
        val text = if (path == null) currentFileText ?: return else read(path)
        if (text == null) {
            errorMessage = "Could not read $path."
            return
        }
        loadFromFile(name, text, path)
    }

    // ── saving the current program ────────────────────────────────────────────

    /**
     * The export formats that fit the current program, as (label, extension).
     * .toyo (the program as built plus hand edits) is always available; .toys only
     * with assembly source; .toyc only for a C program. Mirrors pytoy's
     * _save_formats().
     */
    fun saveFormats(): List<Pair<String, String>> = buildList {
        // highest level first: the save dialog preselects the first one
        if (cText != null) add("C source (.toyc)" to ".toyc")
        if (asmText != null) add("Assembly (.toys)" to ".toys")
        add("Bytecode (.toyo)" to ".toyo")
    }

    /** A suggested base file name for Save (the current file's stem, or title). */
    fun suggestedSaveName(): String {
        val n = currentFileName ?: title
        return n.substringBeforeLast('.').ifBlank { "program" }
    }

    /**
     * The text to write for a given export extension:
     *  - .toyo : the program as built plus the hand edits, without what a run
     *            stored, as [bytecodeText]: nothing but the values.
     *  - .toys : the current assembly source.
     *  - .toyc : the current C source.
     * Returns null if the format doesn't apply to the loaded program.
     */
    fun exportText(extension: String): String? = when (extension) {
        ".toyo" -> machine?.let { bytecodeText(it.programImage()) }
        ".toys" -> asmDraft ?: asmText
        ".toyc" -> cDraft ?: cText
        else -> null
    }

    // ── unsaved edits ─────────────────────────────────────────────────────────
    // Per format, derived from what the last load or save held: the C text, the
    // assembly text, the hand-edited program bytes. Only that format holds such
    // an edit (a compiled C edit is still unsaved after saving only the assembly:
    // the C text is not in there). A run is no edit, and neither is typing into
    // the I/O cells (that is the program's input).
    private var savedC: String? = null
    private var savedAsm: String? = null
    private var savedHandEdits: Map<Int, Int> = emptyMap()

    private fun markLoadedAsSaved() {
        savedC = cText
        savedAsm = asmText
        savedHandEdits = emptyMap()
    }

    private fun handEditsUnsaved(): Boolean {
        val edits = machine?.handEdits ?: return false
        return edits != savedHandEdits
    }

    private fun builtEditsUnsaved() = cText != savedC || asmText != savedAsm || handEditsUnsaved()

    /** The formats holding an unsaved edit, highest level first. */
    fun unsavedFormats(): List<String> = buildList {
        if ((cDraft ?: cText) != savedC) add(".toyc")
        if ((asmDraft ?: asmText) != savedAsm) add(".toys")
        if (handEditsUnsaved()) add(".toyo")
    }

    val unsavedEdits: Boolean get() = unsavedFormats().isNotEmpty()

    /** For "Save changes?": what is not saved, e.g. "C source, memory edits". */
    fun unsavedDescription(): String = unsavedFormats().joinToString(", ") {
        when (it) {
            ".toyc" -> "C source"
            ".toys" -> "assembly"
            else -> "memory edits"
        }
    }

    /** The edits a save as [extension] would not contain, one line each (asks first). */
    fun missingFrom(extension: String): List<String> = buildList {
        if (extension != ".toyc" && cDraft != null)
            add("Your C edits are not compiled yet (C -> Compile).")
        if (extension == ".toyo" && asmDraft != null)
            add("Your assembly edits are not assembled yet (Assembly -> Assemble).")
        if (extension == ".toyc" && asmDraft != null)
            add("Your assembly edits are not in the C source.")
        if (extension != ".toyo" && machine?.handEdits?.isNotEmpty() == true)
            add("Your memory edits are only in the bytecode.")
    }

    /** What Load / Examples / Reload / closing the window wants to do once the
     *  user has answered "Save changes?"; the dialog shows while it is set. */
    var pendingLeave by mutableStateOf<(() -> Unit)?>(null); private set

    /** Run [action] (which replaces the program), asking first if there are unsaved edits. */
    fun leave(action: () -> Unit) {
        if (unsavedEdits) pendingLeave = action else action()
    }

    /** "Discard": drop the edits and go on with the pending action. */
    fun discardAndLeave() {
        val action = pendingLeave ?: return
        pendingLeave = null
        action()
    }

    fun cancelLeave() { pendingLeave = null }

    private var savingExt: String? = null
    private var savingText: String? = null

    /** The text to write as [extension], remembered for [fileSaved]. */
    fun beginSave(extension: String): String? {
        savingExt = extension
        savingText = exportText(extension)
        return savingText
    }

    /**
     * A file save finished ([name], at [path] if the platform has paths) or was
     * cancelled (null). Saving the program's highest level makes that file the
     * current one (Reload, title); a lower level is only an export. A pending
     * action goes on once nothing is unsaved; else "Save changes?" stays and
     * names what is left.
     */
    fun fileSaved(name: String?, path: String? = null) {
        val ext = savingExt
        val text = savingText
        savingExt = null
        savingText = null
        if (name == null) {
            cancelLeave()
            return
        }
        if (ext == saveFormats().first().second && text != null) {
            currentFileName = name
            currentFileText = text
            currentFilePath = path
            title = name
        }
        when (ext) {
            ".toyc" -> savedC = cDraft ?: cText
            ".toys" -> savedAsm = asmDraft ?: asmText
            ".toyo" -> savedHandEdits = machine?.handEdits ?: emptyMap()
        }
        setStatus("Saved $name")
        if (!unsavedEdits) discardAndLeave()
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
        if (loadProgram(example)) markLoadedAsSaved()
    }

    /** [load] without resetting the unsaved state (a build keeps it); false on failure. */
    private fun loadProgram(example: Example): Boolean {
        stopRun()
        // a fresh load clears any stale error from a previous failed attempt, so
        // "errorMessage == null" reliably means "the last load succeeded".
        errorMessage = null
        when (example.kind) {
            ExampleKind.TOYO -> {
                val mem = try {
                    parseToyo(example.source)
                } catch (e: IllegalArgumentException) {
                    errorMessage = e.message
                    return false  // leave the current program untouched
                }
                title = example.name
                listing = (0..255).map { ListingLine(it, listOf(mem[it]), "", true) }
                syms = emptyMap()
                asmText = null
                machine = Machine(mem, (0..255).toSet())
                clearCSource()
                clearSelection()
                rebuildAddrMaps()
            }
            ExampleKind.ASM -> {
                val res: AssemblyResult = assemble(example.source)
                if (res.errors.isNotEmpty()) {
                    errorMessage = res.errors.joinToString("\n")
                    return false  // leave the current program untouched
                }
                title = example.name
                listing = res.listing
                syms = res.syms
                asmText = example.source
                machine = Machine(res.mem, res.dataAddrs)
                clearCSource()
                clearSelection()
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
                        return false  // leave the current program untouched
                    }
                }
            }
        }
        cDraft = null
        asmDraft = null
        // A bundled example says in its `io:` header whether it uses the I/O
        // cells: the panel shows exactly then, and never opens by itself later.
        // Any other program: hidden unless it uses the I/O cells (checked now,
        // and again while it runs). The user can still toggle it in View.
        if (example.io != null) {
            showIo = example.io
            ioAutoDone = true
        } else {
            showIo = usesIo()
            ioAutoDone = showIo
        }
        bump()
        return true
    }

    /** Point the simulator at a built C program: new machine + listing + C maps. */
    private fun loadCProgram(p: CProgram) {
        listing = p.result.listing
        syms = p.result.syms
        asmText = p.asm
        machine = Machine(p.result.mem, p.result.dataAddrs)
        cText = p.cText
        currentCompiler = p.compiler
        cToAsm = p.cToAsm
        asmToC = p.asmToC
        selectedCLine = null
        selectedAddr = null
        memSelectedAddr = null
        rebuildAddrMaps()
    }

    /** Drop any C context (an ASM / TOYO / empty load). */
    private fun clearCSource() {
        cText = null
        savedC = null
        cDraft = null
        currentCompiler = null
        cToAsm = emptyMap()
        asmToC = emptyMap()
        selectedCLine = null
    }

    fun step() {
        stopRun()
        machine?.let { autoShowIo(it); it.step() }
        // stepping drops the whole cross-column selection so a stale highlight
        // can't linger after execution moves on (pytoy clears both anchors)
        clearSelection()
    }

    fun stepBack() {
        stopRun()
        machine?.stepBack()
        clearSelection()
    }

    fun reset() {
        stopRun()
        machine?.reset()
        savedHandEdits = emptyMap()   // Reset never asks, even if a saved file holds an edit
        clearSelection()
    }

    // The address selected specifically in the memory (CPU & memory) panel, or
    // null. Distinct from `selectedAddr` (which a source- or C-line tap also
    // sets) so the Edit button is enabled only for a memory selection.
    var memSelectedAddr: Int? = null; private set
    private var markedRow by mutableStateOf<Pair<String, Int>?>(null)
    // the panel whose right-clicked row stays under the open menu, and the PC then
    private var heldScroll: Pair<String, Int?>? = null

    /**
     * Set the shared cross-column selection from a clicked byte address (an asm
     * or memory tap). The address resolves UP to its C line so the C column
     * lights up too; a null/unmapped address clears everything (deselect). Pass
     * [fromMemory]=true for a memory-panel tap (enables the Edit button); an asm
     * tap passes false.
     */
    fun select(addr: Int?, fromMemory: Boolean = false) {
        if (addr == null) {
            clearSelection()
            return
        }
        markedRow = null
        selectedAddr = addr
        selectedCLine = addrToCLine(addr, addrToLine, asmToC)
        heldScroll = null
        selectedFromC = false   // asm/mem click: single row, no sibling fan-out
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
            clearSelection()
            return
        }
        val addrs = asmRowsToAddrs(rows.toSet(), lineToAddr)
        markedRow = null
        heldScroll = null
        selectedCLine = cLine
        selectedAddr = addrs.minOrNull()
        selectedFromC = true   // C click: fan DOWN to all the statement's rows
        memSelectedAddr = null
        bump()
    }

    /**
     * Highlights a right-clicked row that has no address to select (a comment, a
     * blank line, a draft line): [panel] tells which column it is in.
     */
    fun markRow(panel: String, row: Int) {
        clearSelection()
        markedRow = panel to row
        holdScroll(panel)
        bump()
    }

    /** A right-click selected in [panel]: it does not scroll until the next tap or step. */
    fun holdScroll(panel: String) { heldScroll = panel to machine?.pc }

    fun scrollHeld(panel: String) = heldScroll == panel to machine?.pc

    fun isMarked(panel: String, row: Int) = markedRow == panel to row

    /** Whether this C line has code, so it can be selected. */
    fun cLineHasCode(cLine: Int) = !cToAsm[cLine].isNullOrEmpty()

    private fun clearSelection() {
        markedRow = null
        heldScroll = null
        selectedCLine = null
        selectedAddr = null
        selectedFromC = true   // back to the neutral default
        memSelectedAddr = null
        bump()
    }

    /**
     * The 1-based C line the current PC maps to (its asm row's `# @c:N`), or null
     * when there's no C source or the PC's row has no C origin. Lets the C column
     * show the yellow PC highlight approximately, like the asm/memory panels.
     */
    fun pcCLine(): Int? {
        val m = machine ?: return null
        return addrToCLine(m.pc, addrToLine, asmToC)
    }

    /** The transitive highlight sets for the current selection, shared by all
     *  three panels so one click lights up consistently everywhere. */
    fun selectionSets(): SelectionSets = transitiveSelection(
        selectedCLine, selectedAddr, cToAsm, asmToC, lineToAddr, addrToLine,
        fromC = selectedFromC,
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
        return setOfNotNull(asmToC[row]) to setOf(row)
    }

    fun editCell(addr: Int, value: Int) {
        // Deliberately does NOT stop auto-run: editing an I/O cell while the
        // program busy-polls `ready` is how the user feeds input live and the
        // running program reacts (matches pytoy's _edit_cell, which leaves its
        // timer running). Stopping here would defeat the I/O panel's whole point.
        machine?.editCell(addr, value)
        // resolve the edited byte up to its C line so the C column highlights the
        // owning statement too (consistent with a memory-panel click: single row).
        if (memSelectedAddr != addr) memSelectedAddr = null   // an I/O cell, not the selected row
        markedRow = null
        selectedAddr = addr
        selectedCLine = addrToCLine(addr, addrToLine, asmToC)
        selectedFromC = false
        bump()
        // the I/O cells are not part of the disassembly: typing input there never rebuilds
        if (autoBuild == AutoBuild.DISASSEMBLE && addr < 240) perform(BuildAction.Disassemble)
    }

    // ── editing and building (C / Assembly / Bytecode menus) ─────────────────
    // Edit -> Save only stores the text as a draft; the panel shows it marked
    // "(modified)" and the machine keeps running the old build until C ->
    // Compile / Assembly -> Assemble carries it down (C -> asm -> bytes).

    /** Edited C not compiled yet (null = the C panel shows [cText]). */
    var cDraft: String? by mutableStateOf(null); private set

    /** Edited assembly not assembled yet (null = the asm panel shows the listing). */
    var asmDraft: String? by mutableStateOf(null); private set

    /** What is wrong with bytecode typed into Edit…, or null if it can be saved. */
    fun bytecodeError(text: String): String? =
        try { parseBytecodeEdit(text); null } catch (e: IllegalArgumentException) { e.message }

    /** Edit… on the bytecode: takes effect at once, like editing one cell. */
    fun saveBytecode(text: String) {
        machine?.editCells(parseBytecodeEdit(text))
        selectedAddr = null
        selectedCLine = null
        memSelectedAddr = null
        bump()
        if (autoBuild == AutoBuild.DISASSEMBLE) perform(BuildAction.Disassemble)
    }

    fun saveCDraft(text: String) {
        cDraft = text.takeIf { it != cText }
        if (autoBuild == AutoBuild.COMPILE && cDraft != null) perform(BuildAction.Compile())
    }

    fun saveAsmDraft(text: String) {
        asmDraft = text.takeIf { it != asmText }
        if (autoBuild == AutoBuild.ASSEMBLE && asmDraft != null) perform(BuildAction.Assemble)
    }

    /** A build step that runs by itself after an edit in its layer (C, Assembly, Bytecode menu). */
    enum class AutoBuild { COMPILE, ASSEMBLE, DISASSEMBLE }

    /**
     * At most one: assembling then disassembling would undo each other. Off at start.
     * The builds it triggers never ask: switching it on is the OK for all of them.
     */
    var autoBuild by mutableStateOf<AutoBuild?>(null); private set

    // the toggle that confirmPending switches on along with its first build
    private var pendingAuto: AutoBuild? = null

    /** Off, or on with one build right away; if that build loses something, after the confirm dialog. */
    fun toggleAutoBuild(which: AutoBuild) {
        if (autoBuild == which) { autoBuild = null; return }
        val action = when (which) {
            AutoBuild.COMPILE -> BuildAction.Compile()
            AutoBuild.ASSEMBLE -> BuildAction.Assemble
            AutoBuild.DISASSEMBLE -> BuildAction.Disassemble
        }
        if (lossesFor(action).isEmpty()) { autoBuild = which; perform(action) }
        else { stopRun(); pendingAuto = which; pendingAction = action }
    }

    /** Save & Compile / Assemble / Disassemble: the build, unless saving already ran it. */
    fun buildAfterSave(action: BuildAction) {
        val auto = when (action) {
            is BuildAction.Compile -> AutoBuild.COMPILE
            BuildAction.Assemble -> AutoBuild.ASSEMBLE
            BuildAction.Disassemble -> AutoBuild.DISASSEMBLE
        }
        if (autoBuild != auto) request(action)
    }

    /** A build step from the menus; [request] confirms it first if it loses something. */
    sealed interface BuildAction {
        val label: String
        /** C -> asm -> bytes, with [compiler] (null = the current one). */
        data class Compile(val compiler: String? = null) : BuildAction {
            override val label = if (compiler == null) "Compile" else "Compile with $compiler"
        }
        /** asm -> bytes; for a C program this drops the C. */
        data object Assemble : BuildAction { override val label = "Assemble" }
        /** live bytes -> asm, loaded as a new asm program. */
        data object Disassemble : BuildAction { override val label = "Disassemble" }
    }

    /** The action waiting for the user's OK in the confirm dialog, or null. */
    var pendingAction: BuildAction? by mutableStateOf(null); private set

    /** What [action] would throw away, as phrases for "[action] discards …"; empty = nothing is lost. */
    fun lossesFor(action: BuildAction): List<String> = buildList {
        val m = machine
        if (action !is BuildAction.Compile && hasCSource) {
            add("the C code")
            if (cDraft != null) add("your C edits")
        }
        if (action == BuildAction.Disassemble && hasSource && !hasCSource)
            add("the labels and comments of the assembly")
        if (action != BuildAction.Assemble && asmDraft != null)
            add("your assembly edits")
        if (m != null && m.stepCount > 0) add("the current run")
        if (m != null && action == BuildAction.Disassemble) {
            if ((240..255).any { m.mem[it] != 0 }) add("the values in the I/O cells")
        } else if (m != null && m.hasLiveEdits) add("your memory edits")
    }

    /** Run [action], or park it in [pendingAction] if [lossesFor] is not empty. */
    fun request(action: BuildAction) {
        if (lossesFor(action).isEmpty()) perform(action)
        else { stopRun(); pendingAction = action }
    }

    fun confirmPending() {
        pendingAuto?.let { autoBuild = it }
        pendingAuto = null
        pendingAction?.let(::perform)
        pendingAction = null
    }

    fun cancelPending() { pendingAuto = null; pendingAction = null }

    private fun perform(action: BuildAction) = when (action) {
        is BuildAction.Compile -> compileC(action.compiler)
        BuildAction.Assemble -> assembleAsm()
        BuildAction.Disassemble -> disassemble()
    }

    /**
     * Compile the C (the draft if there is one) with [compiler] (null = the
     * current one; a C program always has one). All or nothing: the result
     * must compile AND assemble within 240 bytes, else an error is shown and
     * nothing changes (the draft is kept). Mirrors pytoy's EditCDialog save and
     * _switch_compiler.
     */
    private fun compileC(compiler: String?) {
        val c = cDraft ?: cText ?: return
        stopRun()
        val target = compiler ?: currentCompiler ?: return
        when (val outcome = CProgram.build(c, target)) {
            is BuildOutcome.Success -> {
                loadCProgram(outcome.program)
                if (cDraft != null) clearCurrentFile()   // Reload would bring back the old file
                cDraft = null
                asmDraft = null
                // the generated asm is no edit; replaced asm / memory edits were confirmed
                savedAsm = asmText
                savedHandEdits = emptyMap()
                bump()
            }
            is BuildOutcome.Failure -> errorMessage = outcome.error
        }
    }

    /**
     * Assemble the asm (the draft if there is one) and load it as a plain asm
     * program, dropping any C. On an assemble error the error is shown and
     * nothing changes.
     */
    private fun assembleAsm() {
        val asm = asmDraft ?: asmText ?: return
        val errors = assemble(asm).errors
        if (errors.isNotEmpty()) {
            errorMessage = errors.joinToString("\n")
            return
        }
        // a compiled C edit now lives on only in this assembly
        val cEdited = cText != savedC
        loadProgram(Example(asmTitle(), ExampleKind.ASM, asm))
        if (cEdited) savedAsm = null
        savedHandEdits = emptyMap()
        clearCurrentFile()   // Reload would bring back the old file
    }

    /**
     * Disassemble the live memory (incl. hand edits and runtime stores) and load
     * it as a new asm program: PC and ACC start at 0, the bytes stay the same.
     */
    private fun disassemble() {
        val m = machine ?: return
        // built edits now live on only in the new assembly; else it is no edit
        val edited = builtEditsUnsaved()
        loadProgram(Example(asmTitle(), ExampleKind.ASM, decompile(m.mem)))
        savedAsm = if (edited) null else asmText
        savedHandEdits = emptyMap()
        clearCurrentFile()   // Reload would bring back the old file
    }

    /** The current title as an asm program name (foo.toyo / foo.toyc -> foo.toys). */
    private fun asmTitle(): String = title.substringBeforeLast('.').ifBlank { "program" } + ".toys"

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
                autoShowIo(m)
                val advanced = m.step()   // capture m: never step a swapped machine
                bump()
                if (!advanced) {
                    running = false; runJob = null; break
                }
                delay(stepDelayMs())
            }
        }
    }

    private fun stepDelayMs(): Long = (1000.0 / stepsPerSecond.coerceIn(0.1, 1000.0)).toLong()
}

/** The example opened at app start. */
const val START_EXAMPLE = "00_stop"
