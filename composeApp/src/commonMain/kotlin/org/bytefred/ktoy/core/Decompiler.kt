package org.bytefred.ktoy.core

/**
 * Bytecode -> assembly. A byte image carries no code/data markers, so the
 * decompiler follows the control flow from address 0 (goto, both ifzero
 * branches, stop ends a path): every reachable instruction is code, every other
 * byte is data. Operand addresses get labels (`L12` for code, `D40` for data;
 * an address inside a 2-byte instruction becomes `L12+1`, the I/O cells keep
 * their io0..ready names). Re-assembling the result gives the same bytes in
 * 0..239. Unreachable code comes out as data. An invalid opcode on the path
 * runs as a NOP (the CPU default), so the trace goes on after it; it is
 * written as a data byte with a comment, since asm has no mnemonic for it.
 */
fun decompile(mem: IntArray): String {
    val mnemonic = OPCODES.entries.associate { (k, v) -> v to k }
    // an instruction the assembler can express: a known opcode whose operand
    // byte (if any) is still below the I/O region
    fun isInstr(a: Int) = mem[a] in mnemonic && (!hasOperand(mem[a]) || a + 1 < IO_BASE)

    // 1. trace the reachable instruction starts (and the invalid opcodes on the
    // way, which run as NOPs: 1 byte, or 2 if their operand bit is set)
    val code = mutableSetOf<Int>()
    val invalid = mutableSetOf<Int>()
    val work = ArrayDeque(listOf(0))
    while (work.isNotEmpty()) {
        val a = work.removeLast()
        if (a >= IO_BASE || a in code || a in invalid) continue
        val next = a + if (hasOperand(mem[a])) 2 else 1
        if (mem[a] !in mnemonic) {
            invalid.add(a)
            work.add(next)
            continue
        }
        if (!isInstr(a)) continue
        code.add(a)
        when (mem[a]) {
            0 -> {}                                    // stop
            24 -> work.add(mem[a + 1])                 // goto
            25 -> { work.add(mem[a + 1]); work.add(next) }   // ifzero
            else -> work.add(next)
        }
    }

    // 2. split 0..end into lines: an instruction at each code start (unless an
    // earlier instruction's operand already covers it), data bytes elsewhere
    val operands = code.filter { hasOperand(mem[it]) }.map { mem[it + 1] }
    val end = maxOf(
        (IO_BASE - 1 downTo 0).firstOrNull { mem[it] != 0 } ?: 0,
        code.maxOfOrNull { it + if (hasOperand(mem[it])) 1 else 0 } ?: 0,
        operands.filter { it < IO_BASE }.maxOrNull() ?: 0,
    )
    class Line(val addr: Int, val size: Int, val isCode: Boolean)
    val lines = mutableListOf<Line>()
    var a = 0
    while (a <= end) {
        val line = if (a in code) Line(a, if (hasOperand(mem[a])) 2 else 1, true) else Line(a, 1, false)
        lines.add(line)
        a += line.size
    }
    val lineAt = IntArray(IO_BASE) { -1 }
    lines.forEachIndexed { i, l -> repeat(l.size) { lineAt[l.addr + it] = i } }

    // 3. labels for every referenced line, operands as label(+offset)
    val labelOf = mutableMapOf<Int, String>()
    for (t in operands.filter { it < IO_BASE }) {
        val l = lines[lineAt[t]]
        labelOf[l.addr] = (if (l.isCode) "L" else "D") + l.addr
    }
    fun ref(t: Int): String {
        if (t >= IO_BASE) return IO_LABELS.getValue(t)
        val l = lines[lineAt[t]]
        val off = t - l.addr
        return labelOf.getValue(l.addr) + if (off > 0) "+$off" else ""
    }

    val out = mutableListOf("# decompiled from bytecode", "")
    for (l in lines) {
        val label = labelOf[l.addr]?.let { "$it:" } ?: ""
        val body = if (l.isCode) {
            val op = mem[l.addr]
            val mn = mnemonic.getValue(op)
            if (hasOperand(op)) mn.padEnd(7) + ref(mem[l.addr + 1]) else mn
        } else if (l.addr in invalid) {
            mem[l.addr].toString().padEnd(7) + "# invalid opcode (a NOP by default" +
                (if (hasOperand(mem[l.addr])) ", skips the next byte too)" else ")")
        } else mem[l.addr].toString()
        out.add((label.padEnd(8) + body).trimEnd())
    }
    return out.joinToString("\n") + "\n"
}
