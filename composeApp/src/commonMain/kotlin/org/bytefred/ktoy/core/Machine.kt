package org.bytefred.ktoy.core

/**
 * A snapshot of the machine's state, used for step-back (undo). All fields are
 * value types or copied containers, so restoring one can't alias the live state.
 */
data class MachineState(
    val mem: IntArray,
    val acc: Int,
    val pc: Int,
    val stopped: Boolean,
    val stepCount: Int,
    val touched: Set<Int>,
    val changedCell: Int?,
    val changedCellPrev: Int?,
    val accWritten: Boolean,
    val hasLiveEdits: Boolean,
    val handEdits: Map<Int, Int>,
)

/**
 * The runnable Toy CPU: pure logic, no UI. Holds the current state and the undo
 * stack, and exposes step / stepBack / editCell / reset. A faithful port of the
 * simulator half of pytoy (the parts that live in simulator.py's ToyDebugger),
 * with the same undo semantics.
 *
 * [memOriginal] is the freshly-assembled/loaded image; reset() restores it.
 * [dataAddrs] seed the "touched" (visible-as-data) set. A store into the code
 * region is surfaced visually by the source panel (it compares live memory to
 * the original), so no separate code-overwrite guard is needed here.
 */
class Machine(
    private val memOriginal: IntArray,
    private val dataAddrs: Set<Int>,
) {
    var mem: IntArray = memOriginal.copyOf(); private set
    var acc: Int = 0; private set
    var pc: Int = 0; private set
    var stopped: Boolean = false; private set
    var stepCount: Int = 0; private set
    var touched: Set<Int> = dataAddrs.toSet(); private set
    var changedCell: Int? = null; private set
    // value that [changedCell] held BEFORE the STORE overwrote it, for the one
    // step the write is highlighted (the memory panel shows it as "# old: N").
    var changedCellPrev: Int? = null; private set
    var accWritten: Boolean = false; private set
    var hasLiveEdits: Boolean = false; private set
    // program bytes (0..239) changed by hand and now differing from the original;
    // unlike [mem], nothing the running program stored
    var handEdits: Map<Int, Int> = emptyMap(); private set

    private val history = ArrayDeque<MachineState>()

    val canUndo: Boolean get() = history.isNotEmpty()

    init { reset() }

    fun reset() {
        mem = memOriginal.copyOf()
        acc = 0
        pc = 0
        stopped = false
        stepCount = 0
        touched = dataAddrs.toSet()
        changedCell = null
        changedCellPrev = null
        accWritten = false
        hasLiveEdits = false
        handEdits = emptyMap()
        history.clear()
    }

    private fun snapshot(): MachineState = MachineState(
        mem = mem.copyOf(), acc = acc, pc = pc, stopped = stopped,
        stepCount = stepCount, touched = touched.toSet(), changedCell = changedCell,
        changedCellPrev = changedCellPrev, accWritten = accWritten,
        hasLiveEdits = hasLiveEdits, handEdits = handEdits,
    )

    private fun restore(s: MachineState) {
        mem = s.mem.copyOf()
        acc = s.acc
        pc = s.pc
        stopped = s.stopped
        stepCount = s.stepCount
        touched = s.touched.toSet()
        changedCell = s.changedCell
        changedCellPrev = s.changedCellPrev
        accWritten = s.accWritten
        hasLiveEdits = s.hasLiveEdits
        handEdits = s.handEdits
    }

    /**
     * Execute one instruction. No-op once stopped. STOP counts as a step (a
     * program that is just `stop` stops after 1 step) and is undoable like any
     * other; it halts with the PC left on the STOP. Records what changed (for
     * highlighting). Returns false if the program did not go on (already
     * stopped, or hit STOP this call).
     */
    fun step(): Boolean {
        if (stopped) return false
        val (instr, argAddr, _) = decode(mem, pc)
        // snapshot before executing so this step can be fully undone
        history.addLast(snapshot())
        if (instr == 0) {           // STOP
            stopped = true
            stepCount += 1
            changedCell = null
            changedCellPrev = null
            accWritten = false
            return false
        }

        // remember the cell's value before a STORE overwrites it, so the memory
        // panel can show "# old: N" for this one step only (decode above already
        // resolved argAddr; executeOne re-resolves it identically).
        val oldVal = if (instr == 21 && argAddr != null) mem[argAddr] else null

        val res = executeOne(mem, pc, acc)
        pc = res.nextPc
        acc = res.acc
        if (instr == 21 && argAddr != null) touched = touched + argAddr
        changedCell = if (instr == 21) argAddr else null
        changedCellPrev = oldVal   // null on any non-STORE step (clears itself)
        accWritten = instr in WRITES_ACC
        stepCount += 1
        return true
    }

    /** Undo the last step or edit. No-op if there's nothing to undo. */
    fun stepBack(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        restore(s)
        return true
    }

    /**
     * Live-edit a memory cell: write [value] (already masked to 0..255) into
     * [addr]. Undoable like a step; marks the cell changed and records that a
     * hand edit happened (so Load/Reload can warn).
     */
    fun editCell(addr: Int, value: Int) {
        history.addLast(snapshot())
        mem[addr] = value and 0xFF
        touched = touched + addr
        changedCell = addr
        changedCellPrev = null   // a hand edit isn't a STORE — no "# old" to show
        accWritten = false
        hasLiveEdits = true
        recordHandEdit(addr)
    }

    /** Live-edit the whole memory as one undoable edit. No-op if nothing changes. */
    fun editCells(values: IntArray) {
        val changed = mem.indices.filter { mem[it] != values[it] }
        if (changed.isEmpty()) return
        history.addLast(snapshot())
        for (a in changed) {
            mem[a] = values[a]
            recordHandEdit(a)
        }
        touched = touched + changed
        changedCell = changed.singleOrNull()
        changedCellPrev = null
        accWritten = false
        hasLiveEdits = true
    }

    private fun recordHandEdit(addr: Int) {
        if (addr < IO_BASE) handEdits =
            if (mem[addr] == memOriginal[addr]) handEdits - addr else handEdits + (addr to mem[addr])
    }

    /** The program as built plus the hand edits, without what a run stored. */
    fun programImage(): IntArray = memOriginal.copyOf().also { for ((a, v) in handEdits) it[a] = v }

    /** Has the byte(s) this listing line owns changed from the assembled original? */
    fun lineChanged(addr: Int?, byteCount: Int): Boolean {
        if (addr == null || byteCount == 0) return false
        for (k in 0 until byteCount) {
            if (addr + k >= mem.size) break
            if (mem[addr + k] != memOriginal[addr + k]) return true
        }
        return false
    }
}

/** A value typed into a bytecode edit: what [parseVal] takes, plus binary as b1010. */
fun parseCellValue(s: String): Int = parseVal(if (s.trim().startsWith("b")) "0" + s.trim() else s)

/**
 * The text of the whole-bytecode edit: one value per line ([parseCellValue], not
 * wrapped), at most 256, cells after the last line are 0, # starts a comment.
 */
fun parseBytecodeEdit(text: String): IntArray {
    val rows = text.lines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
    require(rows.size <= 256) { "expected at most 256 lines, got ${rows.size}" }
    val values = rows.map { r ->
        runCatching { parseCellValue(r) }.getOrNull()?.takeIf { it in 0..255 }
            ?: throw IllegalArgumentException("not a value 0..255: '$r'")
    }
    return values.toIntArray() + IntArray(256 - values.size)
}

/**
 * The bytecode file for a memory image: one integer per line, up to the last
 * non-zero cell (cells after that are 0). What Save writes; mirrors pytoy
 * bytecode_text().
 */
fun bytecodeText(mem: IntArray): String =
    mem.take(mem.indexOfLast { it != 0 } + 1).joinToString("") { "$it\n" }

/**
 * Parse a .toyo file into a 256-byte memory image. Mirrors pytoy parse_toyo().
 * Two formats. Bytecode ([bytecodeText]): only one integer per line, at most
 * 256, cells after the last line are 0, # starts a comment; a value out of range
 * or too many lines throws IllegalArgumentException. Byte listing: lines whose
 * first token is a decimal address followed by one or two 8-bit binary
 * byte-strings; ignores comments and the SYMBOLS section. Text that is neither
 * throws too; an empty or comment-only file is an all-zero program.
 */
fun parseToyo(text: String): IntArray {
    val rows = text.split("\n").map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
    if (rows.isNotEmpty() && rows.all { r -> r.all { it in '0'..'9' } }) {
        val values = rows.map { it.toIntOrNull() ?: Int.MAX_VALUE }
        require(values.size <= 256 && values.all { it <= 255 }) {
            "expected at most 256 integers 0..255, got ${values.size} values, largest ${values.max()}"
        }
        return values.toIntArray() + IntArray(256 - values.size)
    }
    val mem = IntArray(256)
    var found = false
    for (rawLine in text.split("\n")) {
        val s = rawLine.trim()
        if (s.isEmpty() || s.startsWith("#")) continue
        val tok = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tok.isEmpty() || !tok[0].all { it.isDigit() }) continue
        val addr = tok[0].toInt()
        var offset = 0
        for (t in tok.drop(1)) {
            if (t.length == 8 && t.all { it == '0' || it == '1' }) {
                if (addr + offset in 0..255) mem[addr + offset] = t.toInt(2)
                offset += 1
                found = true
            } else break   // first non-byte token starts the source text
        }
    }
    require(found || rows.isEmpty()) { "not a bytecode file (one integer 0..255 per line) or byte listing" }
    return mem
}
