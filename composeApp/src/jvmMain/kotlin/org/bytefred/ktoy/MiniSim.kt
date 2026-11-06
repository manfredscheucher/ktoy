package org.bytefred.ktoy

import java.io.File

// minisim: run a Toy CPU bytecode file (one integer per line, the rest is 0, # starts a comment).
//   kotlinc MiniSim.kt -include-runtime -d minisim.jar && java -jar minisim.jar program.txt -v

fun miniSimLoad(text: String): IntArray {
    val mem = text.lines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.map { it.toInt() }
    require(mem.size <= 256 && mem.all { it in 0..255 }) { "expected at most 256 integers 0..255" }
    return mem.toIntArray() + IntArray(256 - mem.size)
}

fun miniSimRun(mem: IntArray, verbose: Boolean = false): Pair<Int, Int> {
    var pc = 0
    var acc = 0
    var steps = 0
    while (true) {
        val op = mem[pc]
        val arg = mem[(pc + 1) % 256]
        steps++
        println("step=$steps acc=$acc pc=$pc op=$op")
        if (verbose) for (i in 0 until 256 step 16)
            println("x_%03d...x_%03d: ".format(i, i + 15) + (i until i + 16).joinToString("") { "%3d%s".format(mem[it], if (it == pc) "*" else " ") })
        if (op == 0) {
            println("stopped after $steps step${if (steps == 1) "" else "s"}, acc=$acc")
            return acc to steps
        }
        pc = (pc + 1 + (op shr 4 and 1)) % 256     // bit 4: two-byte instruction
        when (op) {
            1 -> acc = acc shr 1                   // right
            2 -> acc = acc shl 1                   // left
            15 -> acc = acc.inv()                  // not
            17 -> acc = acc and mem[arg]           // and
            18 -> acc = acc or mem[arg]            // or
            19 -> acc = acc xor mem[arg]           // xor
            20 -> acc = mem[arg]                   // load
            21 -> mem[arg] = acc                   // store
            22 -> acc += mem[arg]                  // add
            23 -> acc -= mem[arg]                  // sub
            24 -> pc = arg                         // goto
            25 -> if (acc == 0) pc = arg           // ifzero
        }                                          // any other opcode: nop
        acc = acc and 0xFF
    }
}

fun main(args: Array<String>) {
    val files = args.filter { it != "-v" }
    require(files.size == 1) { "usage: minisim <program> [-v]" }
    miniSimRun(miniSimLoad(File(files[0]).readText()), "-v" in args)
}
