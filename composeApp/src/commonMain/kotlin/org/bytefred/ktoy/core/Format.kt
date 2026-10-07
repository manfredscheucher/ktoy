package org.bytefred.ktoy.core

/**
 * Memory-mapped I/O layout and reserved labels — a faithful port of pytoy's
 * format.py. The top of memory (240..255) is reserved for I/O: 15 bidirectional
 * cells io0..io14 plus a `ready` register. Program code and data must stay below
 * IO_BASE, so the usable program space is 0..239.
 */

const val IO_BASE = 240   // io0 lives here
const val IO_COUNT = 15   // io0..io14 -> 240..254
const val IO_READY = 255  // the ready register
const val SP_TOP = 239    // stack starts here (top reserved for I/O)

/** address -> label (io0..io14 and ready), for the GUI to annotate cells. */
val IO_LABELS: Map<Int, String> =
    (0 until IO_COUNT).associate { (IO_BASE + it) to "io$it" } +
        mapOf(IO_READY to "ready")

/** label -> address, pre-seeded into the assembler's symbol table so programs
 *  can write `store io0` / `load ready`. */
val IO_SYMBOLS: Map<String, Int> = IO_LABELS.entries.associate { (addr, name) -> name to addr }

/** The I/O display formats, matching pytoy's format.py FORMATS. */
enum class IoFormat { DECIMAL, BINARY, HEX, ASCII }

/**
 * Format a byte (0..255) as a short string for the given display mode — a port
 * of pytoy's format_byte(). ASCII shows printable codes (32..126) as the
 * character itself; everything else (control codes, 127, high bytes) as a middle
 * dot "·", kept distinct from a real ".".
 */
fun formatByte(b: Int, fmt: IoFormat): String {
    val v = b and 0xFF
    return when (fmt) {
        IoFormat.DECIMAL -> v.toString()
        IoFormat.BINARY -> (7 downTo 0).joinToString("") { if ((v shr it) and 1 == 1) "1" else "0" }
        IoFormat.HEX -> v.toString(16).uppercase().padStart(2, '0')
        IoFormat.ASCII -> if (v in 32..126) v.toChar().toString() else "·"
    }
}

/**
 * True when the instruction at [pc] is a LOAD (opcode 20) of the ready register
 * (255) — i.e. the program is busy-polling for input. The GUI uses this to flag
 * the ready cell red ("the CPU is waiting for input"). Decoded without mutating
 * state; the CPU core stays unaware of I/O. A port of pytoy's polling_input().
 */
fun pollingInput(mem: IntArray, pc: Int): Boolean {
    val dec = decode(mem, pc)
    return dec.instr == 20 && dec.argAddr == IO_READY
}
