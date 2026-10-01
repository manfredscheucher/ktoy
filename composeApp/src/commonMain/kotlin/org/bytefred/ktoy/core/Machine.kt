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
    val accWritten: Boolean,
    val selectedAddr: Int?,
    val hasLiveEdits: Boolean,
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
    var accWritten: Boolean = false; private set
    var selectedAddr: Int? = null; private set
    var hasLiveEdits: Boolean = false; private set

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
        accWritten = false
        selectedAddr = null
        hasLiveEdits = false
        history.clear()
    }

    private fun snapshot(): MachineState = MachineState(
        mem = mem.copyOf(), acc = acc, pc = pc, stopped = stopped,
        stepCount = stepCount, touched = touched.toSet(), changedCell = changedCell,
        accWritten = accWritten, selectedAddr = selectedAddr, hasLiveEdits = hasLiveEdits,
    )

    private fun restore(s: MachineState) {
        mem = s.mem.copyOf()
        acc = s.acc
        pc = s.pc
        stopped = s.stopped
        stepCount = s.stepCount
        touched = s.touched.toSet()
        changedCell = s.changedCell
        accWritten = s.accWritten
        selectedAddr = s.selectedAddr
        hasLiveEdits = s.hasLiveEdits
    }

    /**
     * Execute one instruction. No-op once stopped. On STOP, halts. Records what
     * changed (for highlighting) and pushes an undo snapshot for a real step.
     * Returns false if nothing ran (already stopped, or hit STOP this call).
     */
    fun step(): Boolean {
        if (stopped) return false
        val (instr, argAddr, _) = decode(mem, pc)
        if (instr == 0) {           // STOP
            stopped = true
            return false
        }
        // snapshot before executing so this step can be fully undone
        history.addLast(snapshot())

        val res = executeOne(mem, pc, acc)
        pc = res.nextPc
        acc = res.acc
        if (instr == 21 && argAddr != null) touched = touched + argAddr
        changedCell = if (instr == 21) argAddr else null
        accWritten = instr in WRITES_ACC
        stepCount += 1
        selectedAddr = null
        return true
    }

    /** Undo the last step or edit. No-op if there's nothing to undo. */
    fun stepBack(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        restore(s)
        return true
    }

    /** Select an address (for click-to-highlight in both panels). */
    fun select(addr: Int?) { selectedAddr = addr }

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
        accWritten = false
        selectedAddr = addr
        hasLiveEdits = true
    }

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

/**
 * Parse an exported .toyo byte listing back into a 256-byte memory image.
 * Reads lines whose first token is a decimal address followed by one or two
 * 8-bit binary byte-strings; ignores comments and the SYMBOLS section. Mirrors
 * pytoy parse_toyo().
 */
fun parseToyo(text: String): IntArray {
    val mem = IntArray(256)
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
            } else break   // first non-byte token starts the source text
        }
    }
    return mem
}
