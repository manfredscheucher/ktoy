package org.bytefred.ktoy.core

/**
 * minicc — a tiny, readable C-to-Toy-CPU compiler, for learning.
 *
 * A faithful 1:1 Kotlin port of pytoy's minicc.py. It is the stripped-down
 * sibling of the full toycc port (Compiler.kt): it deliberately leaves out
 * everything optional so the whole pipeline fits in one short, linear file you
 * can read top to bottom:
 *
 *   - multiple NON-RECURSIVE functions (main plus helpers), but NO call stack
 *     and NO save/restore — so recursion is impossible and is rejected.
 *   - no optimizations (no constant folding, no peepholes).
 *   - scalars, one-dimensional int arrays, if/else, while, return.
 *   - operators + - * & | ^ ~ << >> and == != < > <= >= .
 *
 * Everything is 8-bit and wraps mod 256, exactly like the Toy CPU. Comparisons
 * use the same sign-bit (bit 7) trick as toycc, so they are correct for operands
 * that differ by less than 128 — plenty for the teaching examples.
 *
 * Public entry point: [compileSourceMini]. On unsupported syntax (recursion,
 * compound assignment, variable shift, …) it throws [MiniError], mirroring
 * pytoy's MiniError.
 *
 * Pipeline: source text -> tokens (lex) -> AST (Parser) -> assembly (Gen).
 */

/** A compile error with a human-readable message (faithful to pytoy MiniError). */
class MiniError(message: String) : Exception(message)

// ── AST ──────────────────────────────────────────────────────────────────────
//
// pytoy represents the AST as tagged tuples. Here MiniExpr and MiniStmt are
// sealed class hierarchies, mirroring the tuple shapes branch-by-branch.
//
//   expressions: Num(v) Var(name) Index(name, idx) UnOp(op, e) BinOp(op, a, b)
//                Call(name, [args])
//   statements:  CallStmt(call)          f(args);  (value discarded)
//                Decl(name, init?)        int x = e;
//                ArrayDecl(name, size, inits)   int a[10] = {...};
//                Assign(name, e)          x = e;
//                Store(name, idx, e)      a[i] = e;
//                If(cond, then, else?)
//                While(cond, body)
//                Return(e)
//                Block([stmts])

private sealed class MiniExpr {
    data class Num(val value: Int) : MiniExpr()
    data class Var(val name: String) : MiniExpr()
    data class Index(val name: String, val idx: MiniExpr) : MiniExpr()
    data class UnOp(val op: String, val operand: MiniExpr) : MiniExpr()
    data class BinOp(val op: String, val left: MiniExpr, val right: MiniExpr) : MiniExpr()
    data class Call(val name: String, val args: List<MiniExpr>) : MiniExpr()
}

private sealed class MiniStmt {
    /**
     * 1-based C source line this statement's first token came from, or null when
     * line tracking is off. Set by the parser (keyed by node identity in pytoy;
     * here a plain mutable field), consumed only when compileSourceMini is called
     * with annotateCLines = true. A `var` outside the constructor so it stays out
     * of data-class equality.
     */
    var cLine: Int? = null

    data class CallStmt(val call: MiniExpr.Call) : MiniStmt()
    data class Decl(val name: String, val init: MiniExpr?) : MiniStmt()
    data class ArrayDecl(val name: String, val size: Int, val inits: List<Int>) : MiniStmt()
    data class Assign(val name: String, val expr: MiniExpr) : MiniStmt()
    data class Store(val name: String, val idx: MiniExpr, val expr: MiniExpr) : MiniStmt()
    data class If(val cond: MiniExpr, val then: MiniStmt, val els: MiniStmt?) : MiniStmt()
    data class While(val cond: MiniExpr, val body: MiniStmt) : MiniStmt()
    data class Return(val expr: MiniExpr) : MiniStmt()
    data class Block(val stmts: List<MiniStmt>) : MiniStmt()
}

/** A parsed function definition: ('func', name, params, body) in pytoy. */
private data class MiniFunc(val name: String, val params: List<String>, val body: MiniStmt)

/**
 * Append a `# @c:N` marker tying this asm line to C source line N. The assembler
 * ignores everything after the first '#', so the marker is inert: an annotated
 * file assembles to the same memory image. If the line already has a comment, the
 * marker is appended inside it (still one comment); otherwise a fresh `  # @c:N`
 * comment is added. Returns the line unchanged when cLine is null (no C origin).
 */
private fun miniAnnotate(line: String, cLine: Int?): String {
    if (cLine == null) return line
    if ('#' in line) return "$line @c:$cLine"
    return "$line  # @c:$cLine"
}

// ── 1. Lexer: text -> list of tokens ─────────────────────────────────────────
// A token is a (kind, value) pair. Kinds: "num", "id", "kw", "op", "punct",
// "eof". Each token also records the 1-based source line it starts on.

private val MINI_KEYWORDS = setOf("int", "void", "return", "if", "else", "while")

// Multi-character operators must be tried before their single-char prefixes.
private val MINI_OPS = listOf(
    "<<", ">>", "==", "!=", "<=", ">=", "+", "-", "*", "&", "|", "^",
    "~", "<", ">", "=",
)
private val MINI_PUNCT = setOf("(", ")", "{", "}", "[", "]", ";", ",")

private data class MiniToken(val kind: String, val value: Any?, val line: Int)

private fun miniLex(src: String): List<MiniToken> {
    // Precompute the line number for any source offset, so each token can record
    // the line it starts on without the lexer tracking newlines by hand.
    val lineAt = IntArray(src.length + 1)
    var line = 1
    for (i in src.indices) {
        lineAt[i] = line
        if (src[i] == '\n') line++
    }
    lineAt[src.length] = line   // one past the end (for the final 'eof' token)

    val out = mutableListOf<MiniToken>()
    var i = 0
    val n = src.length

    fun add(kind: String, value: Any?, start: Int) {
        out.add(MiniToken(kind, value, lineAt[start]))
    }

    fun isDigit(c: Char) = c in '0'..'9'
    fun isAlpha(c: Char) = c in 'a'..'z' || c in 'A'..'Z'
    fun isAlnum(c: Char) = isDigit(c) || isAlpha(c)

    while (i < n) {
        val c = src[i]
        if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
            i++
        } else if (src.startsWith("//", i)) {
            val nl = src.indexOf('\n', i)
            i = if (nl < 0) n else nl
        } else if (src.startsWith("/*", i)) {
            val end = src.indexOf("*/", i + 2)
            if (end < 0) throw MiniError("unterminated /* comment")
            i = end + 2
        } else if (isDigit(c)) {
            var j = i
            // Matches pytoy `int(text, 0) & 0xFF`: fold mod 256 so arbitrarily long
            // literals mask to their low byte (no 64-bit overflow), and base-0's rule
            // rejects a decimal with a leading zero (int("010", 0) raises ValueError).
            val value: Int
            if (src.startsWith("0x", i) || src.startsWith("0X", i)) {       // hex literal
                j = i + 2
                while (j < n && src[j] in "0123456789abcdefABCDEF") j++
                if (j == i + 2) throw MiniError("invalid integer literal '" + src.substring(i, j) + "'")
                var acc = 0
                for (k in i + 2 until j) {
                    val ch = src[k]
                    val d = when (ch) {
                        in '0'..'9' -> ch - '0'
                        in 'a'..'f' -> ch - 'a' + 10
                        else -> ch - 'A' + 10
                    }
                    acc = (acc * 16 + d) and 0xFF
                }
                value = acc
            } else {
                while (j < n && isDigit(src[j])) j++
                val text = src.substring(i, j)
                if (text.length > 1 && text[0] == '0') throw MiniError("invalid integer literal '" + text + "'")
                var acc = 0
                for (ch in text) acc = (acc * 10 + (ch - '0')) and 0xFF
                value = acc
            }
            add("num", value, i)
            i = j
        } else if (isAlpha(c) || c == '_') {
            var j = i
            while (j < n && (isAlnum(src[j]) || src[j] == '_')) j++
            val word = src.substring(i, j)
            add(if (word in MINI_KEYWORDS) "kw" else "id", word, i)
            i = j
        } else {
            var matched: String? = null
            for (op in MINI_OPS) {
                if (src.startsWith(op, i)) { matched = op; break }
            }
            if (matched != null) {
                add("op", matched, i)
                i += matched.length
            } else if (c.toString() in MINI_PUNCT) {
                add("punct", c.toString(), i)
                i++
            } else {
                throw MiniError("unexpected character '$c'")
            }
        }
    }
    add("eof", "", n)
    return out
}

// ── 2. Parser: tokens -> AST ─────────────────────────────────────────────────

private class MiniParser(private val toks: List<MiniToken>) {
    private var i = 0

    private fun curLine(): Int = toks[i].line

    private fun peek(): MiniToken = toks[i]

    private fun next(): MiniToken = toks[i].also { i++ }

    private fun eat(kind: String, value: Any? = null): MiniToken {
        val t = next()
        if (t.kind != kind || (value != null && t.value != value)) {
            val want = value ?: kind
            throw MiniError("expected '$want', got '${t.value}'")
        }
        return t
    }

    /** Consume a matching token if present, returning it; else null (no advance). */
    private fun accept(kind: String, value: Any? = null): MiniToken? {
        val t = peek()
        if (t.kind == kind && (value == null || t.value == value)) return next()
        return null
    }

    /** True if the current token is exactly (kind, value). */
    private fun isTok(kind: String, value: Any?): Boolean {
        val t = peek()
        return t.kind == kind && t.value == value
    }

    // program ::= function+        (exactly one of them must be `main`)
    fun parseProgram(): List<MiniFunc> {
        val funcs = mutableListOf<MiniFunc>()
        while (peek().kind != "eof") funcs.add(parseFunc())
        val names = funcs.map { it.name }
        if ("main" !in names) throw MiniError("no `int main(void)` function found")
        return funcs
    }

    private fun parseFunc(): MiniFunc {
        eat("kw", "int")
        val name = eat("id").value as String
        eat("punct", "(")
        val params = mutableListOf<String>()
        if (accept("kw", "void") == null) {
            if (!isTok("punct", ")")) {
                eat("kw", "int")
                params.add(eat("id").value as String)
                while (accept("punct", ",") != null) {
                    eat("kw", "int")
                    params.add(eat("id").value as String)
                }
            }
        }
        eat("punct", ")")
        val body = parseBlock()
        return MiniFunc(name, params, body)
    }

    private fun parseBlock(): MiniStmt.Block {
        eat("punct", "{")
        val stmts = mutableListOf<MiniStmt>()
        while (accept("punct", "}") == null) stmts.add(parseStmt())
        return MiniStmt.Block(stmts)
    }

    private fun parseStmt(): MiniStmt {
        // Record the source line of the first token of this statement so codegen
        // can tag the asm it emits (see compileSourceMini's annotateCLines).
        val line = curLine()
        val node = parseStmtInner()
        node.cLine = line
        return node
    }

    private fun parseStmtInner(): MiniStmt {
        val t = peek()
        if (t.kind == "kw" && t.value == "int") return parseDecl()
        if (t.kind == "kw" && t.value == "return") {
            next()
            val e = parseExpr()
            eat("punct", ";")
            return MiniStmt.Return(e)
        }
        if (t.kind == "kw" && t.value == "if") return parseIf()
        if (t.kind == "kw" && t.value == "while") return parseWhile()
        if (t.kind == "punct" && t.value == "{") return parseBlock()
        // assignment:  name = e;   or   name[idx] = e;
        return parseAssign()
    }

    private fun parseDecl(): MiniStmt {
        eat("kw", "int")
        val name = eat("id").value as String
        if (accept("punct", "[") != null) {
            val size = eat("num").value as Int
            eat("punct", "]")
            val inits = mutableListOf<Int>()
            if (accept("op", "=") != null) {
                eat("punct", "{")
                if (accept("punct", "}") == null) {
                    inits.add(eat("num").value as Int)
                    while (accept("punct", ",") != null) inits.add(eat("num").value as Int)
                    eat("punct", "}")
                }
            }
            eat("punct", ";")
            return MiniStmt.ArrayDecl(name, size, inits)
        }
        var init: MiniExpr? = null
        if (accept("op", "=") != null) init = parseExpr()
        eat("punct", ";")
        return MiniStmt.Decl(name, init)
    }

    /**
     * Consume `=`. A compound op like `+=` lexes as `+` then `=`; catch it and
     * explain, since minicc has no compound assignment.
     */
    private fun eatAssignOp() {
        val t = peek()
        if (t.kind == "op" && t.value != "=") {
            throw MiniError(
                "minicc has no compound assignment (${t.value}=); " +
                    "write it out, e.g. `x = x ${t.value} ...`.",
            )
        }
        eat("op", "=")
    }

    private fun parseAssign(): MiniStmt {
        val name = eat("id").value as String
        if (isTok("punct", "(")) {              // a bare call statement: f(args);
            val call = parseCall(name)
            eat("punct", ";")
            return MiniStmt.CallStmt(call)
        }
        if (accept("punct", "[") != null) {
            val idx = parseExpr()
            eat("punct", "]")
            eatAssignOp()
            val e = parseExpr()
            eat("punct", ";")
            return MiniStmt.Store(name, idx, e)
        }
        eatAssignOp()
        val e = parseExpr()
        eat("punct", ";")
        return MiniStmt.Assign(name, e)
    }

    private fun parseIf(): MiniStmt {
        eat("kw", "if")
        eat("punct", "(")
        val cond = parseExpr()
        eat("punct", ")")
        val then = parseStmt()
        val els = if (accept("kw", "else") != null) parseStmt() else null
        return MiniStmt.If(cond, then, els)
    }

    private fun parseWhile(): MiniStmt {
        eat("kw", "while")
        eat("punct", "(")
        val cond = parseExpr()
        eat("punct", ")")
        val body = parseStmt()
        return MiniStmt.While(cond, body)
    }

    // Expression grammar, lowest precedence first. Each level parses the next
    // level up, then folds left-associative runs of its own operators.
    private val levels = listOf(
        listOf("==", "!=", "<", ">", "<=", ">="),
        listOf("|"), listOf("^"), listOf("&"), listOf("<<", ">>"),
        listOf("+", "-"), listOf("*"),
    )

    private fun parseExpr(): MiniExpr = binLevel(0)

    private fun binLevel(lvl: Int): MiniExpr {
        if (lvl == levels.size) return parseUnary()
        var left = binLevel(lvl + 1)
        while (peek().kind == "op" && peek().value in levels[lvl]) {
            val op = next().value as String
            val right = binLevel(lvl + 1)
            left = MiniExpr.BinOp(op, left, right)
        }
        return left
    }

    private fun parseUnary(): MiniExpr {
        val t = peek()
        if (t.kind == "op" && (t.value == "-" || t.value == "~")) {
            next()
            return MiniExpr.UnOp(t.value as String, parseUnary())
        }
        return parseAtom()
    }

    private fun parseAtom(): MiniExpr {
        val t = next()
        if (t.kind == "num") return MiniExpr.Num(t.value as Int)
        if (t.kind == "punct" && t.value == "(") {
            val e = parseExpr()
            eat("punct", ")")
            return e
        }
        if (t.kind == "id") {
            if (isTok("punct", "(")) return parseCall(t.value as String)
            if (accept("punct", "[") != null) {
                val idx = parseExpr()
                eat("punct", "]")
                return MiniExpr.Index(t.value as String, idx)
            }
            return MiniExpr.Var(t.value as String)
        }
        throw MiniError("unexpected '${t.value}' in expression")
    }

    private fun parseCall(name: String): MiniExpr.Call {
        eat("punct", "(")
        val args = mutableListOf<MiniExpr>()
        if (!isTok("punct", ")")) {
            args.add(parseExpr())
            while (accept("punct", ",") != null) args.add(parseExpr())
        }
        eat("punct", ")")
        return MiniExpr.Call(name, args)
    }
}

// ── 3. Code generator: AST -> Toy assembly text ──────────────────────────────
// Layout: all code first, then a data section. Scalars and arrays get fixed data
// bytes; every constant and temporary also lives in a data byte (the Toy CPU has
// only the accumulator, so intermediate values are spilled to memory).

private class MiniGen(funcsList: List<MiniFunc>) {
    private val code = mutableListOf<String>()
    // Parallel to `code`: the C source line each asm line came from, or null.
    private val codeLines = mutableListOf<Int?>()
    private var curCLine: Int? = null

    private var vars = mutableMapOf<String, String>()        // name -> data label
    private val consts = linkedMapOf<Int, String>()          // value -> label (dedup)
    private var arrays = mutableMapOf<String, Triple<String, Int, List<Int>>>()  // name -> (base,size,inits)
    private val temps = mutableListOf<String>()              // temp labels, init 0
    private val allVars = mutableListOf<String>()            // every scalar's label
    // every array's (base, data, size, inits):
    private val allArrays = mutableListOf<MiniArray>()
    private var nLabel = 0
    private var nTemp = 0

    // function support (see marker-dispatch comment block below)
    private val funcs: Map<String, MiniFunc> = funcsList.associateBy { it.name }
    private val slots = mutableListOf<String>()              // f__p_*, f__ret, f__mark
    private var curFunc: String? = null
    private val sites = mutableMapOf<String, Int>().apply { funcs.keys.forEach { this[it] = 0 } }

    private data class MiniArray(val base: String, val data: String, val size: Int, val inits: List<Int>)

    private fun emit(line: String) { code.add(line); codeLines.add(curCLine) }

    private fun const(valueIn: Int): String {
        val value = valueIn and 0xFF
        return consts.getOrPut(value) { "c_$value" }
    }

    private fun varLabel(name: String): String {
        if (name !in vars && name !in arrays) throw MiniError("unknown variable '$name'")
        if (name in arrays) throw MiniError("'$name' is an array, not a scalar")
        return vars[name]!!
    }

    private fun newTemp(): String {
        nTemp++
        val label = "t$nTemp"
        temps.add(label)
        return label
    }

    private fun newLabel(stem: String): String {
        nLabel++
        return "$stem$nLabel"
    }

    // -- expressions: leave the result in the accumulator --------------------
    private fun genExpr(e: MiniExpr) {
        when (e) {
            is MiniExpr.Num -> emit("        load  ${const(e.value)}")
            is MiniExpr.Var -> emit("        load  ${varLabel(e.name)}")
            is MiniExpr.Index -> genIndexRead(e)
            is MiniExpr.UnOp -> genUnop(e)
            is MiniExpr.BinOp -> genBinop(e)
            is MiniExpr.Call -> {
                genCall(e)
                emit("        load  ${e.name}__ret")
            }
        }
    }

    private fun genUnop(e: MiniExpr.UnOp) {
        genExpr(e.operand)
        when (e.op) {
            "~" -> emit("        not")
            "-" -> {                 // 0 - operand
                val t = newTemp()
                emit("        store $t")
                emit("        load  ${const(0)}")
                emit("        sub   $t")
            }
        }
    }

    private val memOp = mapOf("+" to "add", "-" to "sub", "&" to "and", "|" to "or", "^" to "xor")

    private fun genBinop(e: MiniExpr.BinOp) {
        val op = e.op
        if (op in setOf("==", "!=", "<", ">", "<=", ">=")) {
            genCompare(op, e.left, e.right)
            return
        }
        if (op == "*") { genMultiply(e.left, e.right); return }
        if (op == "<<" || op == ">>") { genShift(op, e.left, e.right); return }
        // +, -, &, |, ^ : evaluate right into a temp, then left, then apply.
        genExpr(e.right)
        val t = newTemp()
        emit("        store $t")
        genExpr(e.left)
        emit("        ${memOp[op]!!.padEnd(5)} $t")
    }

    private fun genShift(op: String, left: MiniExpr, right: MiniExpr) {
        // Only a constant shift amount is supported (keeps it simple): unroll to
        // that many single-bit shifts.
        if (right !is MiniExpr.Num) throw MiniError("minicc supports only a constant shift amount")
        genExpr(left)
        val instr = if (op == "<<") "left" else "right"
        repeat(right.value and 0xFF) { emit("        $instr") }
    }

    private fun genMultiply(left: MiniExpr, right: MiniExpr) {
        // No multiply opcode: loop `right` times, adding `left` each pass.
        val a = newTemp()
        val cnt = newTemp()
        val res = newTemp()
        genExpr(left)
        emit("        store $a")
        genExpr(right)
        emit("        store $cnt")
        emit("        load  ${const(0)}")
        emit("        store $res")
        val loop = newLabel("mul")
        val end = newLabel("mulend")
        emit("$loop: load  $cnt")
        emit("        ifzero $end")
        emit("        load  $res")
        emit("        add   $a")
        emit("        store $res")
        emit("        load  $cnt")
        emit("        sub   ${const(1)}")
        emit("        store $cnt")
        emit("        goto  $loop")
        emit("$end: load  $res")
    }

    /** Leave (left - right) in the accumulator. */
    private fun genDiff(left: MiniExpr, right: MiniExpr) {
        genExpr(right)
        val t = newTemp()
        emit("        store $t")
        genExpr(left)
        emit("        sub   $t")
    }

    private fun genCompare(op: String, left: MiniExpr, right: MiniExpr) {
        // Produce a 0/1 result. == / != test (left-right) for zero. The relational
        // ops read bit 7 of a difference: for small operands, bit 7 of (a-b) is
        // set exactly when a < b.
        if (op == "==" || op == "!=") {
            genDiff(left, right)
            boolFromZero(oneIfZero = op == "==")
            return
        }
        // a<b and a>=b read (a-b); a>b and a<=b read (b-a). bit 7 set means the
        // difference went negative, i.e. the first operand was the smaller one.
        val oneIfNeg: Boolean
        when (op) {
            "<" -> { genDiff(left, right); oneIfNeg = true }
            ">" -> { genDiff(right, left); oneIfNeg = true }
            ">=" -> { genDiff(left, right); oneIfNeg = false }
            else -> { genDiff(right, left); oneIfNeg = false }  // <=
        }
        emit("        and   ${const(0x80)}")   // isolate bit 7
        boolFromZero(oneIfZero = !oneIfNeg)
    }

    /**
     * ACC is 0 or nonzero; replace it with a clean 0/1. If oneIfZero, result is 1
     * when ACC==0 else 0 (and vice versa).
     */
    private fun boolFromZero(oneIfZero: Boolean) {
        val zero = newLabel("z")
        val end = newLabel("zend")
        emit("        ifzero $zero")
        emit("        load  ${const(if (oneIfZero) 0 else 1)}")
        emit("        goto  $end")
        emit("$zero: load  ${const(if (oneIfZero) 1 else 0)}")
        emit("$end: nop")
    }

    // -- array element access via self-modifying code ------------------------
    /** Leave (base + idx) — the element's address — in the accumulator. */
    private fun genElementAddr(name: String, idx: MiniExpr) {
        val arr = arrays[name] ?: throw MiniError("'$name' is not an array")
        val base = arr.first
        if (idx is MiniExpr.Num) {
            emit("        load  $base")
            if ((idx.value and 0xFF) != 0) emit("        add   ${const(idx.value)}")
            return
        }
        genExpr(idx)
        val t = newTemp()
        emit("        store $t")
        emit("        load  $base")
        emit("        add   $t")
    }

    private fun genIndexRead(e: MiniExpr.Index) {
        // a[i] : compute the address, patch a raw LOAD's operand byte, run it.
        val addr = newLabel("iarg")
        genElementAddr(e.name, e.idx)
        emit("        store $addr")
        emit("        20               # LOAD opcode")
        emit("$addr: 0                # <- a[i]")
    }

    private fun genStoreIndex(name: String, idx: MiniExpr, value: MiniExpr) {
        // a[i] = value : spill value, compute address, patch a raw STORE, run it.
        val valLabel = newTemp()
        genExpr(value)
        emit("        store $valLabel")
        val addr = newLabel("sarg")
        genElementAddr(name, idx)
        emit("        store $addr")
        emit("        load  $valLabel")
        emit("        21               # STORE opcode")
        emit("$addr: 0                # <- a[i] := acc")
    }

    // -- statements ----------------------------------------------------------
    private fun genStmt(s: MiniStmt) {
        // Tag every asm line this statement emits with its C source line. A nested
        // genStmt (then/else/while body) temporarily overwrites curCLine, so save
        // and restore it around the dispatch below.
        val prev = curCLine
        if (s.cLine != null) curCLine = s.cLine
        try {
            genStmtInner(s)
        } finally {
            curCLine = prev
        }
    }

    private fun genStmtInner(s: MiniStmt) {
        when (s) {
            is MiniStmt.Block -> s.stmts.forEach { genStmt(it) }
            is MiniStmt.Decl -> {
                if (s.name in vars || s.name in arrays) throw MiniError("'${s.name}' already declared")
                val label = "v_${curFunc}__${s.name}"   // per-function: no collisions
                vars[s.name] = label
                allVars.add(label)
                if (s.init != null) {
                    genExpr(s.init)
                    emit("        store $label")
                }
            }
            is MiniStmt.ArrayDecl -> {
                if (s.name in vars || s.name in arrays) throw MiniError("'${s.name}' already declared")
                if (s.inits.size > s.size) throw MiniError("too many initialisers for '${s.name}'")
                val tag = "${curFunc}__${s.name}"        // per-function: no collisions
                val base = "abase_$tag"
                val data = "adata_$tag"
                arrays[s.name] = Triple(base, s.size, s.inits)
                allArrays.add(MiniArray(base, data, s.size, s.inits))
            }
            is MiniStmt.Assign -> {
                genExpr(s.expr)
                emit("        store ${varLabel(s.name)}")
            }
            is MiniStmt.Store -> genStoreIndex(s.name, s.idx, s.expr)
            is MiniStmt.CallStmt -> genCall(s.call)   // evaluate for effect; discard f__ret
            is MiniStmt.Return -> {
                genExpr(s.expr)
                if (curFunc == "main") {
                    emit("        stop")
                } else {
                    emit("        store ${curFunc}__ret")
                    emit("        goto  ${curFunc}__dispatch")
                }
            }
            is MiniStmt.If -> genIf(s)
            is MiniStmt.While -> genWhile(s)
        }
    }

    /** Evaluate cond; jump to falseLabel when it is false (0). */
    private fun genCondJump(cond: MiniExpr, falseLabel: String) {
        genExpr(cond)
        emit("        ifzero $falseLabel")
    }

    private fun genIf(s: MiniStmt.If) {
        if (s.els == null) {
            val end = newLabel("endif")
            genCondJump(s.cond, end)
            genStmt(s.then)
            emit("$end: nop")
        } else {
            val elseL = newLabel("else")
            val end = newLabel("endif")
            genCondJump(s.cond, elseL)
            genStmt(s.then)
            emit("        goto  $end")
            emit("$elseL: nop")
            genStmt(s.els)
            emit("$end: nop")
        }
    }

    private fun genWhile(s: MiniStmt.While) {
        val top = newLabel("while")
        val end = newLabel("endwhile")
        emit("$top: nop")
        genCondJump(s.cond, end)
        genStmt(s.body)
        emit("        goto  $top")
        emit("$end: nop")
    }

    // -- functions: global slots + marker-dispatch --------------------------
    // The Toy CPU has no call/return and no stack, so a call cannot push a return
    // address. Instead every function f gets FIXED data bytes and its body is
    // emitted ONCE:
    //     f__p_<param>  one byte per parameter (caller writes the argument here)
    //     f__ret        one byte              (body writes the return value here)
    //     f__mark       one byte              (caller writes which call site called)
    //     f__body:      the single copy of f's body
    //     f__dispatch:  a compare-chain over f__mark that jumps back to the caller
    // These slots are shared across all activations, so a second (recursive) entry
    // would clobber the caller's — that is why recursion is rejected up front
    // (compileSourceMini builds the call graph and refuses any cycle).

    private fun genCall(e: MiniExpr.Call) {
        val name = e.name
        val f = funcs[name]!!            // existence + arg count checked earlier
        val params = f.params
        // Evaluate every argument into a fresh temp FIRST, then copy the temps
        // into the parameter slots. Doing it in one pass would be wrong for a call
        // like f(y, f(x)): evaluating the second argument re-enters f and
        // overwrites f__p_<first> before the jump.
        val argTemps = mutableListOf<String>()
        for (arg in e.args) {
            genExpr(arg)
            val t = newTemp()
            emit("        store $t")
            argTemps.add(t)
        }
        for ((t, p) in argTemps.zip(params)) {
            emit("        load  $t")
            emit("        store ${name}__p_$p")
        }
        // Mark which call site we are, so f__dispatch can return here.
        val k = sites[name]!!
        sites[name] = k + 1
        emit("        load  ${const(k)}")
        emit("        store ${name}__mark")
        emit("        goto  ${name}__body")
        emit("${name}__cont_$k: nop        # return lands here; value in ${name}__ret")
    }

    private fun genFunctionBody(f: MiniFunc) {
        // Emit one function body. Its dispatch chain is added later, once every
        // call site in the whole program has been counted (genDispatch).
        val name = f.name
        curFunc = name
        // Parameters are addressed as the ordinary variable slots f__p_<param>.
        vars = f.params.associateWith { "${name}__p_$it" }.toMutableMap()
        arrays = mutableMapOf()
        if (name != "main") {      // main is never called, so it needs no slots
            for (p in f.params) slots.add("${name}__p_$p")
            slots.add("${name}__ret")
            slots.add("${name}__mark")
        }
        emit("")
        emit("# function $name(...) — single body, marker-dispatch return")
        emit("${name}__body: nop")
        genStmt(f.body)
        // main has no dispatch chain (its caller added a stop guard). For a helper,
        // a fall-through with no explicit return still goes to dispatch (f__ret
        // keeps its last value). Skip it if the body already ends that way.
        if (name != "main" && code.last().trim() != "goto  ${name}__dispatch") {
            emit("        goto  ${name}__dispatch")
        }
        curFunc = null
    }

    private fun genDispatch(name: String) {
        // A compare-chain over f__mark: for each call site k, jump back to its
        // continuation. Emitted after all bodies, so nSites is final.
        emit("")
        emit("${name}__dispatch: nop")
        for (k in 0 until (sites[name] ?: 0)) {
            emit("        load  ${name}__mark")
            emit("        sub   ${const(k)}")
            emit("        ifzero ${name}__cont_$k")
        }
        emit("        stop            # unreachable: bad $name marker")
    }

    // -- assemble the final .toys text --------------------------------------
    private fun finish(sourceName: String, annotateCLines: Boolean): String {
        val lines = mutableListOf(
            "# Generated by minicc from $sourceName",
            "# Toy CPU assembly. 8-bit values, wrap mod 256.",
            "# Result ends up in the accumulator at STOP.",
            "",
        )
        val body: List<String> = if (annotateCLines) {
            code.mapIndexed { idx, ln -> miniAnnotate(ln, codeLines[idx]) }
        } else code
        lines.addAll(body)
        lines.add("")
        lines.add("# data")
        for (label in allVars) lines.add("$label: 0")
        for (slot in slots) lines.add("$slot: 0")          // f__p_*, f__ret, f__mark
        for (t in temps) lines.add("$t: 0")
        for (value in consts.keys.sorted()) lines.add("${consts[value]}: $value")
        // arrays last: a base byte holding the array address, then its elements.
        for (arr in allArrays) {
            lines.add("${arr.base}: ${arr.data}")
            val cells = arr.inits + List(arr.size - arr.inits.size) { 0 }
            lines.add("${arr.data}: ${cells[0]}")
            for (idx in 1 until cells.size) lines.add("        ${cells[idx]}")
        }
        return lines.joinToString("\n") + "\n"
    }

    fun run(sourceName: String, annotateCLines: Boolean): String {
        val funcList = funcs.values.toList()
        // main first (its `return` halts); then each helper body; then every
        // helper's dispatch chain, by which time all call sites have been counted.
        val main = funcList.first { it.name == "main" }
        if (main.params.isNotEmpty()) {
            throw MiniError("main must take no parameters: `int main(void)`")
        }
        val helpers = funcList.filter { it.name != "main" }
        genFunctionBody(main)
        emit("        stop            # end of main (halt if it falls through)")
        for (f in helpers) genFunctionBody(f)
        for (f in helpers) genDispatch(f.name)
        return finish(sourceName, annotateCLines)
    }
}

// ── 4. Whole-program checks and driver ───────────────────────────────────────

/** Collect every call name anywhere inside an expression. */
private fun miniCallsExpr(e: MiniExpr, acc: MutableList<String>) {
    when (e) {
        is MiniExpr.Call -> { acc.add(e.name); e.args.forEach { miniCallsExpr(it, acc) } }
        is MiniExpr.UnOp -> miniCallsExpr(e.operand, acc)
        is MiniExpr.BinOp -> { miniCallsExpr(e.left, acc); miniCallsExpr(e.right, acc) }
        is MiniExpr.Index -> miniCallsExpr(e.idx, acc)
        is MiniExpr.Num, is MiniExpr.Var -> {}
    }
}

/** Collect every call name anywhere inside a statement. */
private fun miniCallsStmt(s: MiniStmt, acc: MutableList<String>) {
    when (s) {
        is MiniStmt.Block -> s.stmts.forEach { miniCallsStmt(it, acc) }
        is MiniStmt.Decl -> s.init?.let { miniCallsExpr(it, acc) }
        is MiniStmt.ArrayDecl -> {}
        is MiniStmt.Assign -> miniCallsExpr(s.expr, acc)
        is MiniStmt.Store -> { miniCallsExpr(s.idx, acc); miniCallsExpr(s.expr, acc) }
        is MiniStmt.CallStmt -> miniCallsExpr(s.call, acc)
        is MiniStmt.Return -> miniCallsExpr(s.expr, acc)
        is MiniStmt.If -> { miniCallsExpr(s.cond, acc); miniCallsStmt(s.then, acc); s.els?.let { miniCallsStmt(it, acc) } }
        is MiniStmt.While -> { miniCallsExpr(s.cond, acc); miniCallsStmt(s.body, acc) }
    }
}

private fun miniBodyCalls(body: MiniStmt): List<String> {
    val acc = mutableListOf<String>()
    miniCallsStmt(body, acc)
    return acc
}

/**
 * Reject undefined calls and wrong argument counts, at every call site. pytoy's
 * check_calls walks each ('call', name, args) tuple and raises on an unknown
 * callee or a mismatched argument count; this reproduces that traversal.
 */
private fun miniCheckCalls(funcs: List<MiniFunc>) {
    val arity = funcs.associate { it.name to it.params.size }
    fun checkExpr(e: MiniExpr) {
        when (e) {
            is MiniExpr.Call -> {
                val expected = arity[e.name]
                    ?: throw MiniError("call to undefined function '${e.name}'")
                if (e.args.size != expected) {
                    throw MiniError("${e.name}() takes $expected argument(s), got ${e.args.size}")
                }
                e.args.forEach { checkExpr(it) }
            }
            is MiniExpr.UnOp -> checkExpr(e.operand)
            is MiniExpr.BinOp -> { checkExpr(e.left); checkExpr(e.right) }
            is MiniExpr.Index -> checkExpr(e.idx)
            is MiniExpr.Num, is MiniExpr.Var -> {}
        }
    }
    fun checkStmt(s: MiniStmt) {
        when (s) {
            is MiniStmt.Block -> s.stmts.forEach { checkStmt(it) }
            is MiniStmt.Decl -> s.init?.let { checkExpr(it) }
            is MiniStmt.ArrayDecl -> {}
            is MiniStmt.Assign -> checkExpr(s.expr)
            is MiniStmt.Store -> { checkExpr(s.idx); checkExpr(s.expr) }
            is MiniStmt.CallStmt -> checkExpr(s.call)
            is MiniStmt.Return -> checkExpr(s.expr)
            is MiniStmt.If -> { checkExpr(s.cond); checkStmt(s.then); s.els?.let { checkStmt(it) } }
            is MiniStmt.While -> { checkExpr(s.cond); checkStmt(s.body) }
        }
    }
    for (f in funcs) checkStmt(f.body)
}

/**
 * Build the call graph and reject any cycle: minicc is stackfree, so a function
 * cannot call itself directly or indirectly (there is nowhere to save the
 * caller's slots). DFS from each function looking for a path back to it.
 */
private fun miniCheckNoRecursion(funcs: List<MiniFunc>) {
    val graph = funcs.associate { f -> f.name to miniBodyCalls(f.body).toSet() }

    fun reachesSelf(start: String): Boolean {
        val seen = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        stack.addAll(graph[start] ?: emptySet())
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n == start) return true
            if (n !in seen) {
                seen.add(n)
                stack.addAll(graph[n] ?: emptySet())
            }
        }
        return false
    }

    val bad = graph.keys.filter { reachesSelf(it) }.sorted()
    if (bad.isNotEmpty()) {
        throw MiniError(
            "recursion is not supported by minicc (stackfree): " +
                "function(s) ${bad.joinToString(", ")} call themselves directly or " +
                "indirectly. Use the full toycc for recursion.",
        )
    }
}

/**
 * Compile C source text to Toy CPU assembly text using the minimal compiler
 * (minicc). Faithful port of pytoy's minicc.compile_source(src, source_name).
 *
 * Throws [MiniError] on unsupported syntax (recursion, compound assignment,
 * variable shift amount, pointers, unknown variable, …).
 *
 * When annotateCLines is true, each emitted asm line that came from a C statement
 * gets a trailing `# @c:N` comment (N = C source line). The marker is a comment,
 * so the annotated text assembles to the identical memory image. The default
 * (false) is byte-for-byte the plain output.
 */
fun compileSourceMini(
    src: String,
    sourceName: String = "minicc",
    annotateCLines: Boolean = false,
): String {
    val toks = miniLex(src)
    val funcs = MiniParser(toks).parseProgram()
    miniCheckCalls(funcs)
    miniCheckNoRecursion(funcs)
    val gen = MiniGen(funcs)
    return gen.run(sourceName, annotateCLines)
}
