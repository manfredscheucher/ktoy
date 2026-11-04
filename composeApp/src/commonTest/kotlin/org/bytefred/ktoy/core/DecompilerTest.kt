package org.bytefred.ktoy.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** decompile(): bytecode -> asm that re-assembles to the same bytes. */
class DecompilerTest {

    private fun assertRoundTrip(mem: IntArray, what: String) {
        val asm = decompile(mem)
        val res = assemble(asm)
        assertEquals(emptyList(), res.errors, "$what:\n$asm")
        assertContentEquals(mem.copyOf(IO_BASE), res.mem.copyOf(IO_BASE), "$what:\n$asm")
    }

    @Test
    fun everyExampleRoundTrips() {
        for (ex in EXAMPLES) {
            val mem = when (ex.kind) {
                ExampleKind.ASM -> assemble(ex.source).mem
                ExampleKind.TOYO -> parseToyo(ex.source)
                ExampleKind.C -> {
                    val compiler = ex.compilers.firstOrNull() ?: continue
                    val p = (CProgram.build(ex.source, compiler) as? BuildOutcome.Success)?.program ?: continue
                    p.result.mem
                }
            }
            assertRoundTrip(mem, ex.name)
        }
    }

    @Test
    fun codeAndDataAreSeparatedByControlFlow() {
        val asm = decompile(assemble("load x\nadd one\nstore x\nstop\nx: 20\none: 1\n").mem)
        assertEquals(
            """
            # decompiled from bytecode

                    load   D7
                    add    D8
                    store  D7
                    stop
            D7:     20
            D8:     1
            """.trimIndent() + "\n",
            asm,
        )
    }

    @Test
    fun jumpTargetsGetLabelsAndBothIfzeroBranchesAreCode() {
        val src = "loop: load n\n ifzero done\n sub one\n store n\n goto loop\ndone: stop\nn: 3\none: 1\n"
        val asm = decompile(assemble(src).mem)
        assertTrue("L0:     load   D11" in asm, asm)
        assertTrue("ifzero L10" in asm, asm)
        assertTrue("goto   L0" in asm, asm)
        assertTrue("L10:    stop" in asm, asm)
    }

    @Test
    fun ioCellsKeepTheirNamesAndOperandBytesGetOffsets() {
        // self-modifying: store into the operand byte of `load x`
        val src = "load ready\nstore io0\nload ptr\nstore patch+1\npatch: load x\nstop\nx: 7\nptr: 0\n"
        val asm = decompile(assemble(src).mem)
        assertTrue("load   ready" in asm, asm)
        assertTrue("store  io0" in asm, asm)
        assertTrue("store  L8+1" in asm, asm)
        assertRoundTrip(assemble(src).mem, "self-modifying")
    }

    @Test
    fun emptyAndUnreachableMemory() {
        assertEquals("# decompiled from bytecode\n\n        stop\n", decompile(IntArray(256)))
        // unreachable instruction bytes after stop come out as data
        val mem = IntArray(256).also { it[1] = 20; it[2] = 5 }
        assertRoundTrip(mem, "unreachable")
        assertTrue("\n        20\n        5\n" in decompile(mem))
    }

    @Test
    fun anInvalidOpcodeIsANopOnThePath() {
        // load x; 3 (invalid, runs as a NOP); add x; stop; x: 21
        val mem = assemble("load x\n3\nadd x\nstop\nx: 21\n").mem
        val asm = decompile(mem)
        assertTrue("3      # invalid opcode (a NOP by default)" in asm, asm)
        assertTrue("add    D6" in asm, "the code after it is still code:\n$asm")
        assertRoundTrip(mem, "invalid opcode")
    }

    @Test
    fun aTwoByteInvalidOpcodeSkipsItsOperandByte() {
        // 26 has the operand bit set: as a NOP it takes 2 bytes (26, 99)
        val mem = IntArray(256).also { it[0] = 26; it[1] = 99; it[2] = 20; it[3] = 5; it[4] = 0; it[5] = 7 }
        val asm = decompile(mem)
        assertTrue("26     # invalid opcode (a NOP by default, skips the next byte too)" in asm, asm)
        assertTrue("load   D5" in asm, "the trace goes on at address 2:\n$asm")
        assertRoundTrip(mem, "2-byte invalid opcode")
    }
}
