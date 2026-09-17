package dev.scheucher.ktoy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ports pytoy's assembler tests (test_invalid_input.py + the parse_val / happy-path
 * cases from test_toyasm.py) to Kotlin. Pins down parseVal, the assemble() happy
 * path, and the exact error messages err()/didYouMean()/resolve() produce.
 */
class AssemblerTest {

    // ── Value parser ────────────────────────────────────────────────────────

    @Test
    fun parseValDecimal() {
        assertEquals(42, parseVal("42"))
    }

    @Test
    fun parseValHex() {
        assertEquals(42, parseVal("0x2a"))
        assertEquals(42, parseVal("0X2A"))
        assertEquals(255, parseVal("0xFF"))
    }

    @Test
    fun parseValBinaryPrefix() {
        assertEquals(42, parseVal("0b101010"))
        assertEquals(10, parseVal("0b1010"))
    }

    @Test
    fun parseValBinaryRaw8Char() {
        // an 8-char string of 0/1 is parsed as base 2
        assertEquals(42, parseVal("00101010"))
        assertEquals(15, parseVal("00001111"))
    }

    @Test
    fun parseValZero() {
        assertEquals(0, parseVal("0"))
    }

    @Test
    fun parseValBadTokenThrows() {
        // parseVal throws (NumberFormatException); callers catch it. Assert failure.
        assertTrue(runCatching { parseVal("notanumber") }.isFailure)
        assertTrue(runCatching { parseVal("xyz") }.isFailure)
    }

    // ── Assembler happy path ────────────────────────────────────────────────

    @Test
    fun assembleSimpleStop() {
        val r = assemble("stop")
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertEquals(OPCODES["stop"], r.mem[0])
    }

    @Test
    fun assembleLoadAddStopWithData() {
        val src = """
            load a
            add  b
            stop
a:      10
b:      20
""".trimIndent()
        val r = assemble(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertTrue("a" in r.syms)
        assertTrue("b" in r.syms)
        // load a  -> opcode 20 + operand addr, add b -> opcode 22 + operand addr, stop -> 0
        assertEquals(OPCODES["load"], r.mem[0])
        assertEquals(r.syms["a"], r.mem[1])
        assertEquals(OPCODES["add"], r.mem[2])
        assertEquals(r.syms["b"], r.mem[3])
        assertEquals(OPCODES["stop"], r.mem[4])
        // data bytes hold their values
        assertEquals(10, r.mem[r.syms["a"]!!])
        assertEquals(20, r.mem[r.syms["b"]!!])
    }

    @Test
    fun assembleLabelsHaveCorrectAddresses() {
        val src = """
start:  load val
        stop
val:    42
""".trimIndent()
        val r = assemble(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertEquals(0, r.syms["start"])
        assertEquals(3, r.syms["val"])
        assertEquals(42, r.mem[3])
    }

    // ── Error cases ─────────────────────────────────────────────────────────

    @Test
    fun missingOperand() {
        val r = assemble("load")
        assertEquals(1, r.errors.size)
        assertTrue(r.errors[0].startsWith("line 1:"))
        assertTrue("needs an operand" in r.errors[0])
    }

    @Test
    fun unknownLabel() {
        val r = assemble("goto nowhere")
        assertEquals(1, r.errors.size)
        assertTrue(r.errors[0].startsWith("line 1:"))
        assertTrue("nowhere" in r.errors[0])
    }

    @Test
    fun badDataValue() {
        val r = assemble("stop\n# data\nx: notanumber")
        assertEquals(1, r.errors.size)
        assertTrue(r.errors[0].startsWith("line 3:"))
        assertTrue("notanumber" in r.errors[0])
    }

    @Test
    fun typoOpcodeSuggestsNearest() {
        // 'addd' -> 'add' (dist 1, maxlen 4, ratio 0.75 >= 0.6); operand 'x' ignored
        val r = assemble("addd x\nstop")
        assertTrue(r.errors.isNotEmpty())
        assertTrue("did you mean 'add'?" in r.errors[0], "got: ${r.errors[0]}")
        assertTrue("'x' was ignored" in r.errors[0], "got: ${r.errors[0]}")
    }

    @Test
    fun typoWithOperandSuggestsAndNotesIgnored() {
        // 'looad' -> 'load' (dist 1, maxlen 5, ratio 0.8 >= 0.6)
        val r = assemble("looad 5\nstop")
        assertTrue(r.errors.isNotEmpty())
        assertTrue("did you mean 'load'?" in r.errors[0], "got: ${r.errors[0]}")
        assertTrue("'5' was ignored" in r.errors[0], "got: ${r.errors[0]}")
    }

    @Test
    fun tooBigExplainsTwoByteCostAndOverage() {
        // 130 * "load 0" = 260 bytes; 4 over 256; message explains the 2-byte cost
        val r = assemble("load 0\n".repeat(130))
        assertEquals(1, r.errors.size)
        val msg = r.errors[0]
        assertTrue("260 bytes" in msg, "got: $msg")
        assertTrue("4 too many" in msg, "got: $msg")
        assertTrue("2 bytes" in msg, "got: $msg")
    }

    @Test
    fun errorEchoesOffendingSourceLine() {
        val r = assemble("goto nowhere")
        assertTrue(r.errors.isNotEmpty())
        assertTrue("goto nowhere" in r.errors[0])
        assertTrue("line 1:" in r.errors[0])
    }

    @Test
    fun emptyInputIsNotAnError() {
        assertTrue(assemble("").errors.isEmpty())
    }

    @Test
    fun commentOnlyInputIsNotAnError() {
        assertTrue(assemble("# just a comment").errors.isEmpty())
        assertTrue(assemble("# just a comment\n\n   \n").errors.isEmpty())
    }

    // ── dataAddrs / dataStart ───────────────────────────────────────────────

    @Test
    fun dataMarkerSetsDataStartAndDataAddrs() {
        val src = "load v\nstore v\nstop\n# data\nv: 7\n"
        val r = assemble(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        // dataStart is the address right after the code (first byte after the marker)
        assertEquals(r.syms["v"], r.dataStart)
        // the data byte's address is in dataAddrs
        assertTrue(r.syms["v"]!! in r.dataAddrs)
    }

    @Test
    fun noDataMarkerGivesNullDataStart() {
        val r = assemble("load v\nstop\nv: 7\n")
        assertEquals(null, r.dataStart)
    }

    // ── parseVal edge cases: match Python int(s, 0) exactly ──────────────────

    @Test
    fun parseValOctalAndUnderscoreAndSignedHex() {
        assertEquals(15, parseVal("0o17"))
        assertEquals(1000, parseVal("1_000"))
        assertEquals(16, parseVal("+0x10"))
        assertEquals(-6, parseVal("-6"))
    }

    @Test
    fun parseValRejectsDecimalLeadingZero() {
        // Python int("010", 0) raises; "007" too. "0" and "00" are fine.
        assertTrue(runCatching { parseVal("010") }.isFailure)
        assertTrue(runCatching { parseVal("007") }.isFailure)
        assertEquals(0, parseVal("0"))
        assertEquals(0, parseVal("00"))
    }

    // ── did-you-mean: match difflib (Ratcliff/Obershelp ratio >= 0.6) ────────

    @Test
    fun didYouMeanMatchesDifflib() {
        // transposition: difflib ratio 0.75 -> suggests 'stop' (Levenshtein would miss it)
        assertTrue(assemble("stpo\nstop").errors[0].contains("did you mean 'stop'?"))
        // tie broken toward the FIRST opcode in order: 'adn' -> 'and', not 'add'
        assertTrue(assemble("adn\nstop").errors[0].contains("did you mean 'and'?"))
        // single char: 'o' -> 'or'
        assertTrue(assemble("o\nstop").errors[0].contains("did you mean 'or'?"))
        // and the already-covered simple typos still work
        assertTrue(assemble("addd x\nstop").errors[0].contains("did you mean 'add'?"))
        assertTrue(assemble("looad 5\nstop").errors[0].contains("did you mean 'load'?"))
    }
}
