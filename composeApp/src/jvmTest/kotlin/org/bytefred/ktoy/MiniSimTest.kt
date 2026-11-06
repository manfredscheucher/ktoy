package org.bytefred.ktoy

import org.bytefred.ktoy.core.EXAMPLES
import org.bytefred.ktoy.core.ExampleKind
import org.bytefred.ktoy.core.assemble
import org.bytefred.ktoy.core.bytecodeText
import org.bytefred.ktoy.core.executeOne
import org.bytefred.ktoy.core.parseToyo
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The same expected output as pytoy's tests/test_minisim.py. */
class MiniSimTest {
    private val program = "20\n6\n3\n22\n6\n0\n21\n"   // load x, invalid opcode, add x, stop, x: 21

    private fun runLines(text: String, verbose: Boolean = false): Pair<Pair<Int, Int>, List<String>> {
        val captured = ByteArrayOutputStream()
        val stdout = System.out
        System.setOut(PrintStream(captured))
        try {
            return miniSimRun(miniSimLoad(text), verbose) to captured.toString().trim().lines()
        } finally {
            System.setOut(stdout)
        }
    }

    @Test
    fun stepLinesAndResult() {
        val (result, lines) = runLines(program)
        assertEquals(42 to 4, result)
        assertEquals(
            listOf(
                "step=1 acc=0 pc=0 op=20",
                "step=2 acc=21 pc=2 op=3",
                "step=3 acc=21 pc=3 op=22",
                "step=4 acc=42 pc=5 op=0",
                "stopped after 4 steps, acc=42",
            ),
            lines,
        )
    }

    @Test
    fun oneStepIsSingular() {
        val (result, lines) = runLines("")
        assertEquals(0 to 1, result)
        assertEquals("stopped after 1 step, acc=0", lines.last())
    }

    @Test
    fun verboseDumps16RowsAndMarksThePc() {
        val (_, lines) = runLines(program, verbose = true)
        assertEquals("x_000...x_015:  20*  6   3  22   6   0  21 " + "  0 ".repeat(9), lines[1])
        assertEquals("x_240...x_255: " + "  0 ".repeat(16), lines[16])
        assertTrue(lines.subList(1, 17).all { it.length == 79 })
        assertEquals("x_000...x_015:  20   6   3* 22   6   0  21 " + "  0 ".repeat(9), lines[18])
        assertTrue(lines[17].startsWith("step=2 "))
    }

    @Test
    fun loadAllowsCommentsAndFillsWithZeros() {
        val mem = miniSimLoad("# a program\n20 # load\n6\n\n")
        assertEquals(256, mem.size)
        assertEquals(listOf(20, 6), mem.take(2))
        assertTrue(mem.drop(2).all { it == 0 })
    }

    @Test
    fun loadRejectsBadFiles() {
        for (bad in listOf("1\n".repeat(257), "256\n", "-1\n", "abc\n", "20 6\n"))
            assertFailsWith<IllegalArgumentException>(bad) { miniSimLoad(bad) }
    }

    @Test
    fun agreesWithTheCpuCoreOnAllExamples() {
        val images = EXAMPLES.filter { it.subfolder != "04-io" }.mapNotNull {
            when (it.kind) {
                ExampleKind.ASM -> it.name to assemble(it.source).mem
                ExampleKind.TOYO -> it.name to parseToyo(it.source)
                ExampleKind.C -> null
            }
        }
        assertTrue(images.size > 10)
        for ((name, image) in images) {
            val mem = image.copyOf()
            var pc = 0
            var acc = 0
            var steps = 0
            while (true) {
                steps++
                val r = executeOne(mem, pc, acc)
                pc = r.nextPc
                acc = r.acc
                if (r.stopped) break
            }
            assertContentEquals(image, miniSimLoad(bytecodeText(image)), name)
            assertEquals(acc to steps, runLines(bytecodeText(image)).first, name)
        }
    }
}
