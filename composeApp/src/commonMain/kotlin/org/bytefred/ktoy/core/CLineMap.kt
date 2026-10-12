package org.bytefred.ktoy.core

/**
 * Runtime helpers for the three-column (C | asm | bytecode) debugger view:
 * the C↔asm line map parsed from the compilers' `# @c:N` annotations, the
 * transitive C↔asm↔bytecode selection resolved on top of it, and the
 * compiler-detection probe. All pure (plain data in, data out), so the mapping
 * and the probe are unit-tested without a UI. Ported from pytoy's simulator.py
 * (parse_c_line_map / the transitive_selection helpers) and examples_index.py
 * (_detect_compilers / _assembles_and_fits).
 */

// Mirror CLineAnnotationTest's marker regex: `# @c:N` (standalone or inside an
// existing comment). N is the 1-based C source line.
private val C_MARKER_RE = Regex("""#.*@c:(\d+)""")

/**
 * Parse the `# @c:N` annotations in annotated assembly into a bidirectional
 * C↔asm line map.
 *
 * `asm` is the output of compileSource/compileSourceMini with annotateCLines =
 * true: each emitted line that came from a C statement carries a `# @c:N`
 * marker (N = 1-based C line). Lines with no C origin (headers, data, blanks)
 * carry no marker and simply don't map.
 *
 * Returns (cToAsm, asmToC):
 *   cToAsm   1-based C line -> sorted list of 0-based asm line indices
 *   asmToC   0-based asm line index -> 1-based C line
 * The asm indices are 0-based (line indices into `asm.lines()`), so a clicked
 * row maps straight through. Mirrors pytoy's parse_c_line_map exactly.
 */
fun parseCLineMap(asm: String): Pair<Map<Int, List<Int>>, Map<Int, Int>> {
    val cToAsm = linkedMapOf<Int, MutableList<Int>>()
    val asmToC = linkedMapOf<Int, Int>()
    asm.split("\n").forEachIndexed { idx, line ->
        val m = C_MARKER_RE.find(line) ?: return@forEachIndexed
        val cLine = m.groupValues[1].toInt()
        asmToC[idx] = cLine
        cToAsm.getOrPut(cLine) { mutableListOf() }.add(idx)
    }
    val sorted = cToAsm.mapValues { (_, rows) -> rows.sorted() }
    return sorted to asmToC
}

// ── Transitive C ↔ asm ↔ bytecode selection ─────────────────────────────────
//
// The three columns are linked by two maps the debugger keeps: cToAsm / asmToC
// (`# @c:N` markers) tie C lines to asm rows, and lineToAddr / addrToLine (the
// listing) tie asm rows to byte addresses. Composing them gives a transitive
// selection: pick anything in any column and the matching lines/cells in the
// others light up. Pure helpers (plain maps in, sets out).

/** The asm rows a C line owns (empty if the line has no asm origin). */
fun cLineToAsmRows(cLine: Int?, cToAsm: Map<Int, List<Int>>): Set<Int> {
    if (cLine == null) return emptySet()
    return cToAsm[cLine]?.toSet() ?: emptySet()
}

/** The byte addresses the given asm rows own. */
fun asmRowsToAddrs(asmRows: Set<Int>, lineToAddr: Map<Int, Int>): Set<Int> =
    asmRows.mapNotNull { lineToAddr[it] }.toSet()

/**
 * The 1-based C line a byte came from, composing addr→asm-row→C-line. Null if
 * the address isn't in any asm row, or its row has no C origin.
 */
fun addrToCLine(addr: Int?, addrToLine: Map<Int, Int>, asmToC: Map<Int, Int>): Int? {
    if (addr == null) return null
    val row = addrToLine[addr] ?: return null
    return asmToC[row]
}

/** The resolved highlight sets across the three columns. */
data class SelectionSets(
    val cLines: Set<Int>,
    val asmRows: Set<Int>,
    val addrs: Set<Int>,
)

/**
 * Resolve a selection (a C line and/or a byte address) into the full set of
 * highlighted things across all three columns.
 *
 * Either anchor may be null. The fan-out depends on WHERE the click came from
 * ([fromC]):
 *   - a C-line click ([fromC] = true) expands DOWN to all the asm rows it owns
 *     and their byte addresses (a C statement normally owns several asm lines) —
 *     the full down-fan;
 *   - an asm-row / memory-cell click ([fromC] = false) resolves UP to that one
 *     row and its own bytes plus the owning C line, but does NOT fan back out to
 *     the C line's sibling asm rows — only the clicked line lights up on the asm
 *     side.
 *
 * Both anchors null (an unmapped/blank click) resolves to empty sets — deselect
 * clears everything. Mirrors pytoy's transitive_selection.
 */
fun transitiveSelection(
    cLine: Int?,
    addr: Int?,
    cToAsm: Map<Int, List<Int>>,
    asmToC: Map<Int, Int>,
    lineToAddr: Map<Int, Int>,
    addrToLine: Map<Int, Int>,
    fromC: Boolean = true,
): SelectionSets {
    val cLines = mutableSetOf<Int>()
    if (cLine != null) cLines.add(cLine)
    // a byte selection resolves up to its C line so the whole statement lights up
    addrToCLine(addr, addrToLine, asmToC)?.let { cLines.add(it) }

    val asmRows = mutableSetOf<Int>()
    // only a C-origin click fans down to all the statement's asm rows; an asm /
    // memory click keeps just the clicked row (resolved below from the address)
    if (fromC) for (cl in cLines) asmRows += cLineToAsmRows(cl, cToAsm)
    // the asm row owning the selected byte (the only row for an asm/mem click)
    if (addr != null) addrToLine[addr]?.let { asmRows.add(it) }

    // asm/mem click lights only the clicked row's own bytes; a C click lights
    // every fanned-out row's bytes. asmRowsToAddrs gives one anchor byte per row
    // via lineToAddr, so add the raw clicked addr too (covers a two-byte row's
    // operand, and a bare memory cell with no asm row).
    val addrs = asmRowsToAddrs(asmRows, lineToAddr).toMutableSet()
    if (addr != null) addrs.add(addr)
    return SelectionSets(cLines, asmRows, addrs)
}

// ── Compiler detection ───────────────────────────────────────────────────────

/** The compilers in preference/probe order (minicc first, like pytoy). */
val C_COMPILERS = listOf("minicc", "toycc")

/**
 * Compiling without error is not enough — minicc inlines everything and can
 * overflow the 240-byte program region (0..239; 240..255 are reserved I/O). A
 * program that won't assemble can't be offered, so assemble the output and
 * reject if the assembler reports any error (the too-big case lands here).
 * Mirrors pytoy's _assembles_and_fits.
 */
private fun assemblesAndFits(asm: String): Boolean =
    try {
        assemble(asm).errors.isEmpty()
    } catch (e: Exception) {
        false
    }

/** Compile `src` with one named compiler, annotating C lines, or null on failure. */
fun compileWith(compiler: String, src: String, sourceName: String, annotateCLines: Boolean): String =
    when (compiler) {
        "minicc" -> compileSourceMini(src, sourceName, annotateCLines)
        "toycc" -> compileSource(src, sourceName, annotateCLines = annotateCLines)
        else -> throw IllegalArgumentException("unknown compiler '$compiler'")
    }

private fun compilerAccepts(compiler: String, src: String): Boolean =
    try {
        assemblesAndFits(compileWith(compiler, src, "probe", annotateCLines = false))
    } catch (e: Exception) {
        // MiniError / CompileError (recursion, unsupported construct, …) → not
        // accepted. Any other failure likewise means the source isn't usable.
        false
    }

/**
 * Which of ["minicc", "toycc"] accept `src` AND whose output assembles within
 * the 240-byte region. minicc first, then toycc (the UI prefers toycc when both
 * work). Mirrors pytoy's _detect_compilers.
 */
fun detectCompilers(src: String): List<String> =
    C_COMPILERS.filter { compilerAccepts(it, src) }
