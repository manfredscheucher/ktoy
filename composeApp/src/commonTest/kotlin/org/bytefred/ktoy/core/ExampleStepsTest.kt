package org.bytefred.ktoy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every bundled example's header numbers hold in ktoy too: `# result:` /
 * `# steps:` (asm) and `// result:` / `// steps: toycc N, minicc M` (C). pytoy
 * checks the same headers with scripts/example_steps.py, so both
 * implementations are held to the same numbers. The I/O examples wait for
 * input and have no step count.
 */
class ExampleStepsTest {

    private fun header(src: String, re: String): String? =
        Regex(re, setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).find(src)?.groupValues?.get(1)?.trim()

    /** Run to STOP (counted) and return (acc, steps). */
    private fun run(mem: IntArray, dataAddrs: Set<Int>, name: String): Pair<Int, Int> {
        val m = Machine(mem, dataAddrs)
        while (m.step()) assertTrue(m.stepCount < 1_000_000, "$name does not stop")
        return m.acc to m.stepCount
    }

    @Test
    fun asmExamplesMatchTheirHeader() {
        val asm = EXAMPLES.filter { it.kind == ExampleKind.ASM && it.subfolder != "04-io" }
        assertTrue(asm.size > 30)
        for (ex in asm) {
            val res = assemble(ex.source)
            assertTrue(res.errors.isEmpty(), "${ex.name}: ${res.errors}")
            val (acc, steps) = run(res.mem, res.dataAddrs, ex.name)
            assertEquals(assertNotNull(header(ex.source, """^#\s*result:\s*(\d+)"""), "${ex.name}: no result line").toInt(), acc, ex.name)
            assertEquals(assertNotNull(header(ex.source, """^#\s*steps:\s*(\d+)"""), "${ex.name}: no steps line").toInt(), steps, ex.name)
        }
    }

    @Test
    fun bytecodeExamplesMatchTheHeaderInTheirListing() {
        // a .toyo carries its source's header lines in the SOURCE column
        val toyo = EXAMPLES.filter { it.kind == ExampleKind.TOYO }
        assertTrue(toyo.size >= 3)
        for (ex in toyo) {
            val mem = parseToyo(ex.source)
            val (acc, steps) = run(mem, emptySet(), ex.name)
            assertEquals(assertNotNull(header(ex.source, """^#\s+#\s*result:\s*(\d+)"""), "${ex.name}: no result line").toInt(), acc, ex.name)
            assertEquals(assertNotNull(header(ex.source, """^#\s+#\s*steps:\s*(\d+)"""), "${ex.name}: no steps line").toInt(), steps, ex.name)
        }
    }

    @Test
    fun cExamplesMatchTheirHeaderWithEveryCompiler() {
        val c = EXAMPLES.filter { it.kind == ExampleKind.C && it.subfolder != "04-io" }.groupBy { it.name }
        assertTrue(c.size > 20)
        for ((name, variants) in c) {
            val src = variants.first().source
            val expect = assertNotNull(header(src, """//\s*result:\s*(\d+)"""), "$name: no expect line").toInt()
            val per = mutableListOf<String>()
            for (compiler in listOf("toycc", "minicc")) {
                // a compiler that rejects the source, or whose output does not
                // fit 240 bytes, is left out (like pytoy's example_steps.py); the
                // steps line below then catches a compiler missing by mistake
                val prog = (CProgram.build(src, compiler) as? BuildOutcome.Success)?.program ?: continue
                val (acc, steps) = run(prog.result.mem, prog.result.dataAddrs, "$name ($compiler)")
                assertEquals(expect, acc, "$name ($compiler)")
                per.add("$compiler $steps")
            }
            assertEquals(header(src, """//\s*steps:\s*(.+)"""), per.joinToString(", "), "$name steps")
        }
    }
}
