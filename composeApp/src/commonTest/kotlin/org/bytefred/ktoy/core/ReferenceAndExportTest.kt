package org.bytefred.ktoy.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests the Help reference text and .toyo export, the Kotlin counterparts of
 * pytoy's _reference_text() and assembler.export(). Pure data, no UI.
 */
class ReferenceAndExportTest {

    @Test
    fun referenceListsAll14Instructions() {
        val text = referenceText()
        for (m in listOf("stop", "nop", "load", "store", "not", "and", "or",
                         "xor", "add", "sub", "left", "right", "goto", "ifzero")) {
            assertTrue(m in text, "reference missing $m")
        }
    }

    @Test
    fun referenceMentionsShortcuts() {
        val text = referenceText().lowercase()
        for (s in listOf("space", "backspace", "reload", "save")) {
            assertTrue(s in text, "reference missing shortcut $s")
        }
    }

    @Test
    fun everyInstrOpcodeHasAnEffect() {
        // guard EFFECTS against drifting from INSTR: every opcode listed in the
        // instruction table must have a human-readable effect, or a new opcode
        // would render blank in the Help reference.
        val missing = instrOpcodes() - effectOpcodes()
        assertTrue(missing.isEmpty(), "opcodes without an effect string: $missing")
    }

    @Test
    fun toyoExportRoundTrips() {
        // assemble a small program, export the listing to .toyo, parse it back,
        // and confirm the memory image matches — the files are interchangeable.
        val src = "load x\nstop\nx: 42\n"
        val res = assemble(src)
        assertTrue(res.errors.isEmpty(), "assemble errors: ${res.errors}")
        val toyo = exportToyo(res.listing, res.syms)
        val back = parseToyo(toyo)
        assertTrue(res.mem.contentEquals(back), "toyo round-trip mismatch")
    }

    @Test
    fun toyoExportReflectsLiveBytes() {
        // the GUI Save builds the listing from live memory, so a hand-edited cell
        // must survive the export. Simulate that: patch the data byte `x` (a real
        // listing address), rebuild the listing from mem, export, parse back, and
        // check the edit is there. (parseToyo only restores listed addresses, so
        // the patched cell must be one the listing covers — as the GUI's does.)
        val res = assemble("load x\nstop\nx: 42\n")
        val xAddr = res.syms.getValue("x")
        val mem = res.mem.copyOf()
        mem[xAddr] = 123
        val live = res.listing.map { line ->
            val a = line.addr ?: return@map line
            line.copy(bytes = line.bytes.indices.map { mem[(a + it) % 256] })
        }
        val back = parseToyo(exportToyo(live, res.syms))
        assertEquals(123, back[xAddr], "edited byte not exported")
    }

    @Test
    fun bytecodeFileRoundTripsWithComments() {
        val mem = IntArray(256).also { it[0] = 1; it[2] = 3 }
        assertEquals("1\n0\n3\n", bytecodeText(mem))
        assertEquals("", bytecodeText(IntArray(256)))
        assertEquals(256, bytecodeText(IntArray(256) { 9 }).count { it == '\n' })
        assertContentEquals(mem, parseToyo("# a comment\n1  # one\n0\n3\n"))
    }

    @Test
    fun emptyOrCommentOnlyFileIsAllZero() {
        assertContentEquals(IntArray(256), parseToyo(""))
        assertContentEquals(IntArray(256), parseToyo("# nothing here\n\n"))
    }

    @Test
    fun badBytecodeFilesAreRejected() {
        for (bad in listOf(
            "1\n".repeat(257), "300\n", "99999999999\n", "-1\n", "20\nabc\n", "0x14\n", "20 6\n", "\uFEFF20\n",
        )) assertFailsWith<IllegalArgumentException>(bad) { parseToyo(bad) }
    }
}

/**
 * A byte listing (bytes, source + a symbol table), as pytoy's assembler.export()
 * writes it. Save writes bytecode only; this is here to test reading listings.
 */
internal fun exportToyo(listing: List<ListingLine>, syms: Map<String, Int>): String {
    val out = mutableListOf(
        "# Toy CPU – compiled listing", "#",
        "# " + "ADDR".padStart(4) + "  " + "BYTES".padEnd(22) + "  SOURCE",
        "# " + "─".repeat(56),
    )
    for (line in listing) {
        val addr = line.addr
        if (addr == null) {
            out.add("#        " + "".padEnd(22) + "  " + line.orig.trimEnd())
        } else {
            val bs = line.bytes.joinToString("  ") {
                it.toString(2).padStart(8, '0')
            }
            out.add("  " + addr.toString().padStart(4) + "   " +
                    bs.padEnd(22) + "  " + line.orig.trimEnd())
        }
    }
    out.add("")
    out.add("# SYMBOLS")
    for ((name, addr) in syms.entries.sortedBy { it.value }) {
        out.add("#   " + name.padEnd(15) + " = " + addr.toString().padStart(3) +
                "  (" + addr.toString(2).padStart(8, '0') + ")")
    }
    return out.joinToString("\n") + "\n"
}
