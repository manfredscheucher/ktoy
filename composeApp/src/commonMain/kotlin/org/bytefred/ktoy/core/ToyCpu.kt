package org.bytefred.ktoy.core

/**
 * Toy CPU ISA core: the shared instruction set, decoder, and executor.
 * Based on https://github.com/freedosproject/toycpu.
 *
 * A faithful Kotlin port of pytoy's core.py — the single source of truth for the
 * CPU semantics. Both the assembler and the simulator build on it, so they can
 * never drift apart. Memory is a 256-element IntArray of byte values (0..255).
 */

// ── Instruction set ─────────────────────────────────────────────────────────

/** mnemonic -> opcode number. */
val OPCODES: Map<String, Int> = mapOf(
    "stop" to 0, "right" to 1, "left" to 2, "not" to 15,
    "and" to 17, "or" to 18, "xor" to 19,
    "load" to 20, "store" to 21, "add" to 22, "sub" to 23,
    "goto" to 24, "ifzero" to 25, "nop" to 128,
)

/** Bit 4 (0x10) of the opcode means the instruction has a 2nd address byte. */
const val FETCH = 0x10

fun hasOperand(op: Int): Boolean = (op and FETCH) != 0

// ── Value parser ────────────────────────────────────────────────────────────

/**
 * Parse a value token exactly the way pytoy does: an 8-char string of 0/1 is
 * base 2; otherwise this mirrors Python's int(s, 0). That means 0x/0X hex,
 * 0b/0B binary, 0o/0O octal, and decimal — with a leading '+'/'-' sign and '_'
 * digit separators allowed — but a DECIMAL literal may NOT have a redundant
 * leading zero (Python rejects "010"/"007"; "0" and "00" oddly differ, so we
 * match int(s,0): "0" ok, "00" ok, "010" error). Throws on anything else.
 */
fun parseVal(raw: String): Int {
    val s = raw.trim()
    if (s.length == 8 && s.all { it == '0' || it == '1' }) return s.toInt(2)
    if (s.isEmpty()) throw NumberFormatException("empty")

    var sign = 1
    var body = s
    if (body.startsWith("+")) body = body.substring(1)
    else if (body.startsWith("-")) { sign = -1; body = body.substring(1) }

    val lower = body.lowercase()
    val (radix, digitsRaw) = when {
        lower.startsWith("0x") -> 16 to body.substring(2)
        lower.startsWith("0b") -> 2 to body.substring(2)
        lower.startsWith("0o") -> 8 to body.substring(2)
        else -> 10 to body
    }
    // Python int(s,0) allows '_' as a digit separator (not leading/trailing).
    if (digitsRaw.isEmpty() || digitsRaw.startsWith("_") || digitsRaw.endsWith("_"))
        throw NumberFormatException("bad number: $s")
    val digits = digitsRaw.replace("_", "")
    if (digits.isEmpty()) throw NumberFormatException("bad number: $s")
    // decimal literals may not carry a redundant leading zero (PEP 3127):
    // reject "010"/"007" but keep "0" and "00" (int(s,0) accepts those).
    if (radix == 10 && digits.length > 1 && digits[0] == '0' && digits.any { it != '0' })
        throw NumberFormatException("leading zero in decimal: $s")
    return sign * digits.toInt(radix)
}

// ── Instruction execution ────────────────────────────────────────────────────

/** Result of decoding: the opcode, the operand address (null for 1-byte ops), and the next PC. */
data class Decoded(val instr: Int, val argAddr: Int?, val nextPc: Int)

/** Decode the instruction at [pc]. Mirrors pytoy decode(): PC wraps mod 256. */
fun decode(mem: IntArray, pc: Int): Decoded {
    val instr = mem[pc]
    return if (hasOperand(instr)) {
        Decoded(instr, mem[(pc + 1) % 256], (pc + 2) % 256)
    } else {
        Decoded(instr, null, (pc + 1) % 256)
    }
}

/** Result of executing one instruction. acc is masked to 8 bits. */
data class Executed(val nextPc: Int, val acc: Int, val argAddr: Int?, val stopped: Boolean)

/**
 * Run one instruction. Mutates [mem] on STORE. acc is masked to 8 bits, PC wraps
 * mod 256 — exactly like pytoy execute_one(). STORE writes unconditionally here;
 * callers wanting the code-overwrite guard check isCodeStore() before calling.
 */
fun executeOne(mem: IntArray, pc: Int, accIn: Int): Executed {
    val (instr, argAddr, nextPcInit) = decode(mem, pc)
    var acc = accIn
    var nextPc = nextPcInit
    var stopped = false
    when (instr) {
        0 -> stopped = true
        1 -> acc = acc shr 1
        2 -> acc = acc shl 1
        15 -> acc = acc.inv()
        17 -> acc = acc and mem[argAddr!!]
        18 -> acc = acc or mem[argAddr!!]
        19 -> acc = acc xor mem[argAddr!!]
        20 -> acc = mem[argAddr!!]
        21 -> mem[argAddr!!] = acc
        22 -> acc = acc + mem[argAddr!!]
        23 -> acc = acc - mem[argAddr!!]
        24 -> nextPc = argAddr!!
        25 -> if (acc == 0) nextPc = argAddr!!
        // opcode 128 (NOP) and any unrecognized opcode: do nothing, just advance
    }
    return Executed(nextPc, acc and 0xFF, argAddr, stopped)
}

/**
 * Opcodes whose executeOne branch assigns to acc (RIGHT/LEFT/NOT/AND/OR/XOR/
 * LOAD/ADD/SUB). STORE/GOTO/IFZERO/STOP/NOP leave it alone. Kept next to
 * executeOne so they can't drift; the UI uses it to highlight ACC writes.
 */
val WRITES_ACC: Set<Int> = setOf(1, 2, 15, 17, 18, 19, 20, 22, 23)

/**
 * True if the instruction at [pc] is a STORE writing into the code region (below
 * [codeGuard]). Used to flag a code overwrite. False when codeGuard is null.
 */
fun isCodeStore(mem: IntArray, pc: Int, codeGuard: Int?): Boolean {
    if (codeGuard == null) return false
    val (instr, argAddr, _) = decode(mem, pc)
    return instr == 21 && argAddr != null && argAddr < codeGuard
}

// opcode -> (mnemonic, operand kind). null = no operand, "a" = address only,
// "av" = address plus the value stored there.
private data class InstrInfo(val name: String, val kind: String?)

private val INSTR: Map<Int, InstrInfo> = mapOf(
    0 to InstrInfo("STOP", null), 1 to InstrInfo("RIGHT", null), 2 to InstrInfo("LEFT", null),
    15 to InstrInfo("NOT", null), 128 to InstrInfo("NOP", null),
    17 to InstrInfo("AND", "av"), 18 to InstrInfo("OR", "av"), 19 to InstrInfo("XOR", "av"),
    20 to InstrInfo("LOAD", "av"), 22 to InstrInfo("ADD", "av"), 23 to InstrInfo("SUB", "av"),
    21 to InstrInfo("STORE", "a"), 24 to InstrInfo("GOTO", "a"), 25 to InstrInfo("IFZERO", "a"),
)

/**
 * A human description of the instruction at (instr, argAddr), e.g.
 * "LOAD a (addr:7, val:3)". [sym] maps an address to its label (or its number).
 * Mirrors pytoy _describe(); unknown opcodes are shown as unknown, not "NOP".
 */
fun describe(instr: Int, argAddr: Int?, sym: (Int) -> String, mem: IntArray): String {
    val info = INSTR[instr] ?: InstrInfo("?? (opcode=$instr, treated as NOP)", null)
    if (info.kind == null) return info.name
    val tail = if (info.kind == "av") "(addr:$argAddr, val:${mem[argAddr!!]})" else "(addr:$argAddr)"
    return "${info.name.padEnd(5)} ${sym(argAddr!!)} $tail"
}
