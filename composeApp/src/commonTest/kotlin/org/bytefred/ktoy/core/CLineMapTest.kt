package org.bytefred.ktoy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * The three-column view's runtime helpers (CLineMap.kt), ported from pytoy's
 * simulator.py transitive helpers + examples_index.py compiler probing:
 *   - parseCLineMap: `# @c:N` markers → bidirectional C↔asm line map.
 *   - the transitive C↔asm↔bytecode selection (compose the two maps).
 *   - detectCompilers: which compilers accept AND fit a source.
 * All pure, so they're tested without any UI.
 */
class CLineMapTest {

    // A tiny program with one statement per C line, so the expected mapping is
    // obvious from the source positions (pytoy's test_three_column PROG).
    private val PROG = """
        int main(void) {
            int x = 5;
            int y = x + 3;
            return y;
        }
    """.trimIndent() + "\n"
    //   line 2: int x = 5;   line 3: int y = x + 3;   line 4: return y;

    /** Compile `src`, returning everything the view keeps for highlighting:
     *  (cToAsm, asmToC, lineToAddr, addrToLine). Built like the VM does. */
    private fun mapsFor(src: String, compiler: String = "toycc"):
        Quad<Map<Int, List<Int>>, Map<Int, Int>, Map<Int, Int>, Map<Int, Int>> {
        val asm = compileWith(compiler, src, "p.toyc", annotateCLines = true)
        val (cToAsm, asmToC) = parseCLineMap(asm)
        val res = assemble(asm)
        assertTrue(res.errors.isEmpty(), "assemble failed: ${res.errors}")
        val lineToAddr = mutableMapOf<Int, Int>()
        val addrToLine = mutableMapOf<Int, Int>()
        res.listing.forEachIndexed { i, l ->
            val a = l.addr ?: return@forEachIndexed
            lineToAddr[i] = a
            addrToLine[a] = i
            if (l.bytes.size == 2) addrToLine[a + 1] = i
        }
        return Quad(cToAsm, asmToC, lineToAddr, addrToLine)
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    // ── parseCLineMap round-trip ─────────────────────────────────────────────

    @Test
    fun parseCLineMapIsBidirectionalAndSorted() {
        val asm = compileWith("toycc", PROG, "p.toyc", annotateCLines = true)
        val (cToAsm, asmToC) = parseCLineMap(asm)

        assertTrue(cToAsm.isNotEmpty(), "annotated asm should produce a C→asm map")
        // every asmToC entry is the inverse of a cToAsm membership
        for ((row, cLine) in asmToC) {
            assertTrue(row in cToAsm.getValue(cLine), "asmToC[$row]=$cLine not in cToAsm[$cLine]")
        }
        // every cToAsm row maps back to the same C line, and rows are sorted
        for ((cLine, rows) in cToAsm) {
            assertEquals(rows.sorted(), rows, "cToAsm[$cLine] rows must be sorted")
            for (row in rows) assertEquals(cLine, asmToC[row], "inverse mismatch at row $row")
        }
        // the three statements (C lines 2,3,4) are all annotated
        assertTrue(2 in cToAsm && 3 in cToAsm && 4 in cToAsm)
    }

    // ── transitive selection ─────────────────────────────────────────────────

    @Test
    fun cLineResolvesToAsmRowsAndBytes() {
        val (cToAsm, asmToC, lineToAddr, addrToLine) = mapsFor(PROG)

        val rows = cLineToAsmRows(2, cToAsm)
        assertEquals(cToAsm.getValue(2).toSet(), rows)
        assertTrue(rows.size >= 2, "a statement owns several asm rows")

        val addrs = asmRowsToAddrs(rows, lineToAddr)
        assertTrue(addrs.isNotEmpty(), "a code statement must own at least one byte")

        val sel = transitiveSelection(2, null, cToAsm, asmToC, lineToAddr, addrToLine)
        assertEquals(setOf(2), sel.cLines)
        assertEquals(rows, sel.asmRows)
        assertEquals(addrs, sel.addrs)
    }

    @Test
    fun asmRowResolvesBackToCLine() {
        val (cToAsm, asmToC, lineToAddr, addrToLine) = mapsFor(PROG)
        // a byte of a C-attributed asm row, resolved up to C, must be line 2
        val row = cToAsm.getValue(2).first { it in lineToAddr }
        val addr = lineToAddr.getValue(row)
        assertEquals(2, addrToCLine(addr, addrToLine, asmToC))

        val sel = transitiveSelection(null, addr, cToAsm, asmToC, lineToAddr, addrToLine)
        assertEquals(setOf(2), sel.cLines)
        assertEquals(cToAsm.getValue(2).toSet(), sel.asmRows)  // whole statement lights
    }

    @Test
    fun memoryCellResolvesUpToC() {
        val (cToAsm, asmToC, lineToAddr, addrToLine) = mapsFor(PROG)
        val row = cToAsm.getValue(3).first { it in lineToAddr }
        val addr = lineToAddr.getValue(row)
        assertEquals(3, addrToCLine(addr, addrToLine, asmToC))

        val sel = transitiveSelection(null, addr, cToAsm, asmToC, lineToAddr, addrToLine)
        assertEquals(setOf(3), sel.cLines)
        assertTrue(addr in sel.addrs)
        assertEquals(cToAsm.getValue(3).toSet(), sel.asmRows)
    }

    @Test
    fun deselectClearsEverything() {
        val (cToAsm, asmToC, lineToAddr, addrToLine) = mapsFor(PROG)
        val sel = transitiveSelection(null, null, cToAsm, asmToC, lineToAddr, addrToLine)
        assertEquals(emptySet(), sel.cLines)
        assertEquals(emptySet(), sel.asmRows)
        assertEquals(emptySet(), sel.addrs)
    }

    @Test
    fun loneByteSelectsItselfButNoCLine() {
        val (cToAsm, asmToC, lineToAddr, addrToLine) = mapsFor(PROG)
        // a byte owned by an asm row with no C origin (a data byte / header)
        val codeAddrs = cToAsm.values.flatten().mapNotNull { lineToAddr[it] }.toSet()
        val lone = addrToLine.keys.first { it !in codeAddrs }
        val sel = transitiveSelection(null, lone, cToAsm, asmToC, lineToAddr, addrToLine)
        assertTrue(lone in sel.addrs)
        assertTrue(addrToLine.getValue(lone) in sel.asmRows)
    }

    // ── detectCompilers ──────────────────────────────────────────────────────

    private fun sourceOf(name: String): String =
        EXAMPLES.first { it.name == name }.source

    @Test
    fun simpleSourceDetectsBothCompilers() {
        // sum3 is a simple non-recursive program both compilers accept + fit.
        val accepted = detectCompilers(sourceOf("sum3.toyc"))
        assertEquals(listOf("minicc", "toycc"), accepted, "sum3 must be accepted by both")
    }

    @Test
    fun recursiveSourceDetectsToyccOnly() {
        // minicc rejects recursion (MiniError at compile); toycc compiles it.
        val accepted = detectCompilers(sourceOf("fibonacci_rec.toyc"))
        assertEquals(listOf("toycc"), accepted, "recursive source must be toycc-only")
    }

    @Test
    fun overflowingSourceDetectsToyccOnly() {
        // bigint_add: minicc compiles it but the inlined result overflows the
        // 240-byte region, so assemblesAndFits rejects minicc; toycc fits.
        val accepted = detectCompilers(sourceOf("bigint_add.toyc"))
        assertEquals(listOf("toycc"), accepted, "overflowing source must be toycc-only")
    }
}
