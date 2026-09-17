package dev.scheucher.ktoy.core

/**
 * Toy CPU assembler — a faithful Kotlin port of pytoy's assembler.py.
 *
 * Two-pass: pass 1 assigns addresses + symbols and checks size; pass 2 emits the
 * 256-byte memory image plus a per-line listing. Errors are collected (never
 * thrown) with line numbers, the offending source line, and did-you-mean hints,
 * exactly as pytoy does.
 */

/** One line of the assembly listing. addr is null for comment/blank lines. */
data class ListingLine(
    val addr: Int?,
    val bytes: List<Int>,
    val orig: String,
    val isData: Boolean,
)

/** The full result of assembling. */
data class AssemblyResult(
    val mem: IntArray,
    val listing: List<ListingLine>,
    val syms: Map<String, Int>,
    val dataAddrs: Set<Int>,
    val errors: List<String>,
    val dataStart: Int?,
)

private data class ParsedRow(
    val label: String?,
    val mn: String?,
    val op: String?,
    val orig: String,
    val lineno: Int,
)

// The canonical "start of data" marker: a comment whose only payload is "data".
private val DATA_MARKER_RE = Regex("""^#\W*data\W*$""", RegexOption.IGNORE_CASE)

private fun isDataMarker(orig: String): Boolean = DATA_MARKER_RE.matches(orig.trim())

private fun parseSource(src: String): List<ParsedRow> {
    val rows = mutableListOf<ParsedRow>()
    // Kotlin lines() splits on \n, \r\n and \r — like Python splitlines() — but
    // keeps a trailing empty line after a final newline; drop that one so a file
    // ending in "\n" doesn't add a phantom blank listing row (matches pytoy).
    val raw = src.lines()
    val lines = if (raw.size > 1 && raw.last().isEmpty()) raw.dropLast(1) else raw
    lines.forEachIndexed { idx, line ->
        val lineno = idx + 1
        val orig = line
        var body = line
        val hash = body.indexOf('#')
        if (hash >= 0) body = body.substring(0, hash)
        body = body.trim()
        var label: String? = null
        val colon = body.indexOf(':')
        if (colon >= 0) {
            label = body.substring(0, colon).trim().lowercase()
            body = body.substring(colon + 1).trim()
        }
        if (body.isEmpty()) {
            rows.add(ParsedRow(label, null, null, orig, lineno))
            return@forEachIndexed
        }
        val tok = body.split(Regex("\\s+")).filter { it.isNotEmpty() }
        rows.add(ParsedRow(label, tok[0].lowercase(), tok.getOrNull(1), orig, lineno))
    }
    return rows
}

/**
 * The total length of the matching blocks between a and b, using the same
 * Ratcliff/Obershelp recursion as Python's difflib.SequenceMatcher: find the
 * longest common contiguous block, then recurse on the parts to its left and
 * right. difflib.ratio() = 2*M / (len(a)+len(b)).
 */
private fun matchingBlocks(a: String, b: String): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    // longest common contiguous substring (block)
    var bestLen = 0
    var bestI = 0
    var bestJ = 0
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            if (a[i - 1] == b[j - 1]) {
                dp[i][j] = dp[i - 1][j - 1] + 1
                if (dp[i][j] > bestLen) {
                    bestLen = dp[i][j]
                    bestI = i - bestLen
                    bestJ = j - bestLen
                }
            }
        }
    }
    if (bestLen == 0) return 0
    return bestLen +
        matchingBlocks(a.substring(0, bestI), b.substring(0, bestJ)) +
        matchingBlocks(a.substring(bestI + bestLen), b.substring(bestJ + bestLen))
}

/**
 * If [word] looks like a mistyped opcode, return " (did you mean 'add'?)", else
 * "". Faithful to Python difflib.get_close_matches(cutoff=0.6): the SequenceMatcher
 * ratio 2*M/(len(a)+len(b)) must be >= 0.6, and among equal top ratios the FIRST
 * opcode in insertion order wins (as difflib preserves the candidate order).
 */
private fun didYouMean(word: String): String {
    val w = word.lowercase()
    var best: String? = null
    var bestRatio = 0.6
    for (op in OPCODES.keys) {
        val m = matchingBlocks(w, op)
        val ratio = 2.0 * m / (w.length + op.length)
        if (ratio > bestRatio) {   // strict > keeps the FIRST of equal-ratio ties
            bestRatio = ratio
            best = op
        }
    }
    return if (best != null) " (did you mean '$best'?)" else ""
}

private fun err(lineno: Int, orig: String, msg: String): String =
    "line $lineno: $msg\n    ${orig.trim()}"

/** Assemble [src] into a 256-byte image, listing, symbols, and any errors. */
fun assemble(src: String): AssemblyResult {
    val parsed = parseSource(src)

    // pass 1 — addresses + symbols, and total size check
    val syms = mutableMapOf<String, Int>()
    var addr = 0
    for (row in parsed) {
        if (row.label != null) syms[row.label] = addr
        val mn = row.mn ?: continue
        val opc = OPCODES[mn]
        addr += if (opc != null && hasOperand(opc)) 2 else 1
    }

    if (addr > 256) {
        val over = addr - 256
        val errors = listOf(
            "Program is too big: it needs $addr bytes, but the Toy CPU has only " +
                "256 ($over too many). Instructions with an operand " +
                "(load/store/add/sub/and/or/xor/goto/ifzero) take 2 bytes; the " +
                "rest take 1. Remove some instructions or data."
        )
        return AssemblyResult(IntArray(256), emptyList(), syms, emptySet(), errors, null)
    }

    // pass 2 — emit
    val mem = IntArray(256)
    val listing = mutableListOf<ListingLine>()
    val errors = mutableListOf<String>()
    val dataAddrs = mutableSetOf<Int>()
    var dataStart: Int? = null
    addr = 0
    for (row in parsed) {
        if (dataStart == null && isDataMarker(row.orig)) dataStart = addr
        val mn = row.mn
        if (mn == null) {
            listing.add(ListingLine(null, emptyList(), row.orig, false))
            continue
        }
        val opc = OPCODES[mn]
        if (opc != null) {
            if (hasOperand(opc)) {
                val v = resolve(row.op, syms, mn, errors, row.lineno, row.orig)
                mem[addr] = opc
                mem[addr + 1] = v and 0xFF
                listing.add(ListingLine(addr, listOf(opc, v and 0xFF), row.orig, false))
                addr += 2
            } else {
                mem[addr] = opc
                listing.add(ListingLine(addr, listOf(opc), row.orig, false))
                addr += 1
            }
        } else {
            // data byte (or a label used as an address constant)
            var v = 0
            val sym = syms[mn]
            if (sym != null) {
                v = sym and 0xFF
            } else {
                val parsed2 = try { parseVal(mn) } catch (e: Exception) { null }
                if (parsed2 != null) {
                    v = parsed2 and 0xFF
                } else if (row.op != null) {
                    // a bare word followed by an operand is almost certainly a
                    // mistyped instruction, not a data byte — and its operand
                    // was about to be silently dropped.
                    errors.add(err(row.lineno, row.orig,
                        "'$mn' is not an instruction${didYouMean(mn)}; " +
                            "its operand '${row.op}' was ignored"))
                } else {
                    errors.add(err(row.lineno, row.orig,
                        "'$mn' is not a valid value or instruction${didYouMean(mn)}"))
                }
            }
            mem[addr] = v
            dataAddrs.add(addr)
            listing.add(ListingLine(addr, listOf(v), row.orig, true))
            addr += 1
        }
    }

    return AssemblyResult(mem, listing, syms, dataAddrs, errors, dataStart)
}

private fun resolve(
    op: String?, syms: Map<String, Int>, ctx: String,
    errors: MutableList<String>, lineno: Int, orig: String,
): Int {
    if (op == null) {
        errors.add(err(lineno, orig, "'$ctx' needs an operand (an address or a label)"))
        return 0
    }
    val k = op.lowercase()
    syms[k]?.let { return it }
    return try {
        parseVal(op)
    } catch (e: Exception) {
        errors.add(err(lineno, orig, "unknown label '$op' used by '$ctx'"))
        0
    }
}
