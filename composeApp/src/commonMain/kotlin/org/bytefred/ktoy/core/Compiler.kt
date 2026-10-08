package org.bytefred.ktoy.core

/**
 * toycc — a tiny C-to-assembly compiler for the Toy CPU.
 *
 * A faithful 1:1 Kotlin port of pytoy's compiler.py. It compiles a small,
 * standard-looking subset of C into a .toys assembly file that the simulator can
 * run. Hand-written lexer + recursive-descent parser + a straightforward code
 * generator that emits Toy assembly text.
 *
 * Target machine: 256 bytes of memory, one 8-bit accumulator (all arithmetic
 * wraps mod 256), only `ifzero` and `goto` for control flow. Every C `int` is an
 * unsigned 8-bit value. The usable program space is 0..239 (240..255 are the
 * memory-mapped I/O region, handled by the assembler/Format).
 *
 * Reuses OPCODES/hasOperand/parseVal (ToyCpu.kt), IO_BASE/IO_READY/IO_SYMBOLS/
 * SP_TOP (Format.kt), and the assembler's `label+N` operand support.
 */

/** The canonical "start of data" marker emitted into the generated assembly. */
private const val DATA_MARKER = "# data"

/** I/O builtins exposed at the C level: name -> argument count. */
private val IO_BUILTINS = mapOf("write" to 2, "read" to 1)

/** Raised for any user-facing compile error (faithful to pytoy CompileError). */
class CompileError(message: String) : Exception(message)

// ── AST ──────────────────────────────────────────────────────────────────────
//
// Python represents the AST as tagged tuples. Here Expr and Stmt are sealed class
// hierarchies. The generic tree walkers in the Python (`_calls_in`, `_rename`,
// liveness, folding) are ported as explicit `when` dispatches that mirror the
// Python branch-by-branch.

sealed class Expr {
    data class Num(val value: Int) : Expr()
    data class Var(val name: String) : Expr()
    data class UnOp(val op: String, val operand: Expr) : Expr()
    data class BinOp(val op: String, val left: Expr, val right: Expr) : Expr()
    data class LogAnd(val left: Expr, val right: Expr) : Expr()
    data class LogOr(val left: Expr, val right: Expr) : Expr()
    data class Index(val name: String, val idx: Expr) : Expr()
    data class AddrOf(val name: String) : Expr()
    data class AddrOfIndex(val name: String, val idx: Expr) : Expr()
    data class Deref(val ptr: Expr) : Expr()
    data class Call(val name: String, val args: List<Expr>) : Expr()
}

sealed class Stmt {
    /**
     * 1-based C source line this statement came from, or null if it has no C
     * origin (nodes synthesised by the lifter/codegen). Set by the parser and
     * carried through fold/rename/lift via [carryLine]. Only consumed when
     * compileSource is called with annotateCLines = true, where codegen tags
     * each emitted asm line with a `# @c:N` marker. A plain `var` (not a
     * constructor param) so it stays out of data-class equality/copy and the
     * many AST-rebuild sites don't have to thread it explicitly.
     */
    var cLine: Int? = null

    data class Block(val stmts: List<Stmt>) : Stmt()
    data class Decl(val name: String, val init: Expr?) : Stmt()
    data class ArrayDecl(val name: String, val size: Int, val init: List<Expr>?) : Stmt()
    data class Assign(val name: String, val expr: Expr) : Stmt()
    data class IdxAssign(val name: String, val idx: Expr, val expr: Expr) : Stmt()
    data class DerefAssign(val ptr: Expr, val expr: Expr) : Stmt()
    data class ExprStmt(val expr: Expr) : Stmt()
    data class Return(val expr: Expr?) : Stmt()
    data class If(val cond: Expr, val then: Stmt, val els: Stmt?) : Stmt()
    data class While(val cond: Expr, val body: Stmt) : Stmt()
    // Nodes introduced by the CallLifter / codegen:
    data class Label(val name: String) : Stmt()
    data class Goto(val name: String) : Stmt()
    data class BuiltinStmt(val name: String, val args: List<Expr>, val cap: String?) : Stmt()
    data class SetMark(val fname: String, val k: Int) : Stmt()
    data class CallJump(val fname: String, val cont: String) : Stmt()
    data class Push(val slot: String) : Stmt()
    data class Pop(val slot: String) : Stmt()
    // Pre-expansion call-site marker (lives only inside the lifter until expanded).
    data class CallSite(
        val name: String,
        val paramPairs: List<Pair<String, String>>,  // (param slot, arg temp)
        val k: Int,
        val cont: String,
        val cap: String,
        val argTemps: List<String>,
    ) : Stmt()
}

/** A parsed function definition. */
data class Func(val name: String, val params: List<String>, val body: Stmt)

// ── Lexer ──────────────────────────────────────────────────────────────────

private val KEYWORDS = setOf("int", "void", "return", "while", "if", "else", "for")

// Multi-character operators must be tried before single-character ones.
private val MULTI_OPS = listOf(
    "<<=", ">>=",
    "==", "!=", "<=", ">=", "<<", ">>",
    "+=", "-=", "*=", "&=", "|=", "^=",
    "&&", "||",
)
private val SINGLE_OPS = "+-*&|^~<>=(){};,![]".toSet()

private data class Token(val kind: String, val value: Any?, val pos: Int, val line: Int)

private fun lex(src: String): List<Token> {
    val toks = mutableListOf<Token>()
    var i = 0
    val n = src.length
    var line = 1
    while (i < n) {
        val c = src[i]
        if (c == ' ' || c == '\t' || c == '\r') { i++; continue }
        if (c == '\n') { line++; i++; continue }
        // line comment //
        if (c == '/' && i + 1 < n && src[i + 1] == '/') {
            while (i < n && src[i] != '\n') i++
            continue
        }
        // block comment /* ... */
        if (c == '/' && i + 1 < n && src[i + 1] == '*') {
            i += 2
            while (i + 1 < n && !(src[i] == '*' && src[i + 1] == '/')) {
                if (src[i] == '\n') line++
                i++
            }
            i += 2
            continue
        }
        // character literal 'x' -> its ASCII code as an int_lit
        if (c == '\'') {
            val start = i
            i++
            if (i >= n) throw CompileError("line $line: unterminated character literal")
            val value: Int
            if (src[i] == '\\') {
                i++
                if (i >= n) throw CompileError("line $line: unterminated character literal")
                val esc = src[i]
                val mapping = mapOf(
                    'n' to 10, 't' to 9, 'r' to 13, '0' to 0,
                    '\\' to 92, '\'' to 39, '"' to 34,
                )
                val m = mapping[esc]
                    ?: throw CompileError("line $line: unknown escape '\\$esc'")
                value = m
                i++
            } else {
                value = src[i].code
                i++
            }
            if (i >= n || src[i] != '\'') {
                throw CompileError("line $line: character literal must be one character")
            }
            i++  // closing quote
            toks.add(Token("int_lit", value and 0xFF, start, line))
            continue
        }
        // numbers (decimal or hex 0x..)
        if (c.isDigit()) {
            val start = i
            // Parse wide (Long) then keep the low byte, matching Python's
            // arbitrary-precision int(...) followed by the later `& 0xFF`. A plain
            // toInt() would throw on a literal > Int.MAX (e.g. 0xFFFFFFFFFF), which
            // Python accepts; Long.toInt() takes the low 32 bits without throwing.
            val value: Int
            if (c == '0' && i + 1 < n && (src[i + 1] == 'x' || src[i + 1] == 'X')) {
                i += 2
                while (i < n && src[i] in "0123456789abcdefABCDEF") i++
                value = src.substring(start + 2, i).toLong(16).toInt()
            } else {
                while (i < n && src[i].isDigit()) i++
                value = src.substring(start, i).toLong(10).toInt()
            }
            toks.add(Token("int_lit", value, start, line))
            continue
        }
        // identifiers / keywords
        if (c.isLetter() || c == '_') {
            val start = i
            while (i < n && (src[i].isLetterOrDigit() || src[i] == '_')) i++
            val word = src.substring(start, i)
            if (word in KEYWORDS) toks.add(Token("kw", word, start, line))
            else toks.add(Token("ident", word, start, line))
            continue
        }
        // operators
        var matched: String? = null
        for (op in MULTI_OPS) {
            if (src.startsWith(op, i)) { matched = op; break }
        }
        if (matched != null) {
            toks.add(Token("op", matched, i, line))
            i += matched.length
            continue
        }
        if (c in SINGLE_OPS) {
            toks.add(Token("op", c.toString(), i, line))
            i++
            continue
        }
        throw CompileError("line $line: unexpected character '$c'")
    }
    toks.add(Token("eof", null, n, line))
    return toks
}

private fun _constName(value: Int): String {
    // A data-byte label for the constant `value` (0..255). Printable ASCII gets a
    // readable `ascii_<char>_<value>` label; the value suffix is REQUIRED because
    // the assembler lowercases labels, so ascii_H_72 vs ascii_h_104 don't collide.
    val v = value and 0xFF
    if (v in 65..90 || v in 97..122 || v in 48..57) {   // A-Z a-z 0-9
        return "ascii_${v.toChar()}_$v"
    }
    val named = mapOf(32 to "space", 33 to "bang", 46 to "dot",
                      44 to "comma", 63 to "qmark", 10 to "nl")
    named[v]?.let { return "ascii_${it}_$v" }
    return "c_$v"
}

/**
 * Append a `# @c:N` marker tying this asm line to C source line N. The assembler
 * ignores everything after the first '#', so the marker is inert: an annotated
 * file assembles to the same memory image. If the line already has a comment the
 * marker is appended inside it (still one comment); otherwise a fresh `  # @c:N`
 * comment is added. Returns the line unchanged when cLine is null (no C origin).
 */
private fun annotateLine(line: String, cLine: Int?): String {
    if (cLine == null) return line
    if ('#' in line) return "$line @c:$cLine"
    return "$line  # @c:$cLine"
}

/**
 * Propagate a statement's recorded C source line from [old] to a rebuilt [new]
 * node (fold / rename / lift all construct fresh Stmt objects). No-op when old
 * has no line. Returns [new] for use inline. Mirrors pytoy's _carry_line.
 */
private fun <T : Stmt> carryLine(old: Stmt, new: T): T {
    if (old !== new) new.cLine = old.cLine
    return new
}

// ── Parser (recursive descent) ─────────────────────────────────────────────

private class Parser(private val toks: List<Token>) {
    private var i = 0

    private fun peek(): Token = toks[i]
    private fun next(): Token = toks[i].also { i++ }

    private fun at(kind: String, value: Any? = null): Boolean {
        val t = peek()
        if (t.kind != kind) return false
        if (value != null && t.value != value) return false
        return true
    }

    private fun eat(kind: String, value: Any? = null): Token {
        val t = peek()
        if (t.kind != kind || (value != null && t.value != value)) {
            val want = value ?: kind
            throw CompileError("line ${t.line}: expected '$want', got '${t.value}'")
        }
        return next()
    }

    fun parseProgram(): List<Func> {
        val funcs = mutableListOf<Func>()
        while (!at("eof")) funcs.add(parseFunc())
        val names = funcs.map { it.name }
        if ("main" !in names) throw CompileError("no 'main' function found")
        for (nm in names) {
            if (names.count { it == nm } > 1) {
                throw CompileError("function '$nm' defined more than once")
            }
        }
        return funcs
    }

    private fun parseFunc(): Func {
        if (at("kw", "int")) next()
        else if (at("kw", "void")) next()
        else {
            val t = peek()
            throw CompileError("line ${t.line}: expected 'int' or 'void', got '${t.value}'")
        }
        val name = eat("ident").value as String
        eat("op", "(")
        val params = parseParams()
        eat("op", ")")
        val body = parseBlock()
        return Func(name, params, body)
    }

    private fun parseParams(): List<String> {
        if (at("op", ")")) return emptyList()
        if (at("kw", "void")) { next(); return emptyList() }
        val params = mutableListOf<String>()
        while (true) {
            eat("kw", "int")
            if (at("op", "*")) next()
            val name = eat("ident").value as String
            if (at("op", "[")) { next(); eat("op", "]") }
            params.add(name)
            if (at("op", ",")) { next(); continue }
            break
        }
        return params
    }

    private fun parseBlock(): Stmt.Block {
        eat("op", "{")
        val stmts = mutableListOf<Stmt>()
        while (!at("op", "}")) stmts.add(parseStatement())
        eat("op", "}")
        return Stmt.Block(stmts)
    }

    private fun parseStatement(): Stmt {
        // Record the C source line of the statement's first token so codegen can
        // emit `# @c:N` markers. carryLine threads it through fold/rename/lift.
        val line = peek().line
        return parseStatementInner().also { it.cLine = it.cLine ?: line }
    }

    private fun parseStatementInner(): Stmt {
        val t = peek()
        if (t.kind == "op" && t.value == "{") return parseBlock()
        if (t.kind == "kw" && t.value == "int") return parseDecl()
        if (t.kind == "kw" && t.value == "return") {
            next()
            val expr = parseExpr()
            eat("op", ";")
            return Stmt.Return(expr)
        }
        if (t.kind == "kw" && t.value == "if") return parseIf()
        if (t.kind == "kw" && t.value == "while") return parseWhile()
        if (t.kind == "kw" && t.value == "for") return parseFor()
        // deref assignment:  *p = expr;
        if (t.kind == "op" && t.value == "*") return parseDerefAssignStmt()
        // bare call statement: f(args);
        if (t.kind == "ident" && toks[i + 1].kind == "op" && toks[i + 1].value == "(") {
            val name = eat("ident").value as String
            eat("op", "(")
            val args = parseArgs()
            eat("op", ")")
            eat("op", ";")
            return Stmt.ExprStmt(Expr.Call(name, args))
        }
        return parseAssignStmt()
    }

    private fun parseDecl(): Stmt {
        eat("kw", "int")
        // pointer declaration: int *p;  or  int *p = expr;
        if (at("op", "*")) {
            next()
            val name = eat("ident").value as String
            var init: Expr? = null
            if (at("op", "=")) { next(); init = parseExpr() }
            eat("op", ";")
            return Stmt.Decl(name, init)
        }
        val name = eat("ident").value as String
        // array declaration
        if (at("op", "[")) {
            next()
            val sizeExpr = parseExpr()
            eat("op", "]")
            val size = constSize(sizeExpr)
            var init: List<Expr>? = null
            if (at("op", "=")) {
                next()
                eat("op", "{")
                val initList = mutableListOf<Expr>()
                if (!at("op", "}")) {
                    initList.add(parseExpr())
                    while (at("op", ",")) { next(); initList.add(parseExpr()) }
                }
                eat("op", "}")
                if (initList.size > size) {
                    throw CompileError(
                        "array '$name' has ${initList.size} initializers but size $size")
                }
                init = initList
            }
            eat("op", ";")
            return Stmt.ArrayDecl(name, size, init)
        }
        var init: Expr? = null
        if (at("op", "=")) { next(); init = parseExpr() }
        eat("op", ";")
        return Stmt.Decl(name, init)
    }

    private fun constSize(expr: Expr): Int {
        if (expr is Expr.Num) {
            if (expr.value == 0) throw CompileError("array size must be non-zero")
            return expr.value
        }
        throw CompileError("array size must be a constant")
    }

    private val compound = mapOf(
        "+=" to "+", "-=" to "-", "*=" to "*",
        "&=" to "&", "|=" to "|", "^=" to "^",
        "<<=" to "<<", ">>=" to ">>",
    )

    private fun parseAssignStmt(): Stmt {
        val name = eat("ident").value as String
        if (at("op", "[")) {
            next()
            val idx = parseExpr()
            eat("op", "]")
            val op = eat("op").value as String
            val expr: Expr = when {
                op == "=" -> parseExpr()
                op in compound -> {
                    val rhs = parseExpr()
                    Expr.BinOp(compound[op]!!, Expr.Index(name, idx), rhs)
                }
                else -> throw CompileError("expected assignment operator, got '$op'")
            }
            eat("op", ";")
            return Stmt.IdxAssign(name, idx, expr)
        }
        val op = eat("op").value as String
        val expr: Expr = when {
            op == "=" -> parseExpr()
            op in compound -> {
                val rhs = parseExpr()
                Expr.BinOp(compound[op]!!, Expr.Var(name), rhs)
            }
            else -> throw CompileError("expected assignment operator, got '$op'")
        }
        eat("op", ";")
        return Stmt.Assign(name, expr)
    }

    private fun parseDerefAssignStmt(): Stmt {
        eat("op", "*")
        val ptr = parseUnary()
        val op = eat("op").value as String
        val expr: Expr = when {
            op == "=" -> parseExpr()
            op in compound -> {
                val rhs = parseExpr()
                Expr.BinOp(compound[op]!!, Expr.Deref(ptr), rhs)
            }
            else -> throw CompileError("expected assignment operator, got '$op'")
        }
        eat("op", ";")
        return Stmt.DerefAssign(ptr, expr)
    }

    private fun parseIf(): Stmt {
        eat("kw", "if")
        eat("op", "(")
        val cond = parseExpr()
        eat("op", ")")
        val thenBody = parseStatement()
        var elseBody: Stmt? = null
        if (at("kw", "else")) { next(); elseBody = parseStatement() }
        return Stmt.If(cond, thenBody, elseBody)
    }

    private fun parseWhile(): Stmt {
        eat("kw", "while")
        eat("op", "(")
        val cond = parseExpr()
        eat("op", ")")
        val body = parseStatement()
        return Stmt.While(cond, body)
    }

    private fun parseFor(): Stmt {
        eat("kw", "for")
        eat("op", "(")
        val init: Stmt?
        if (at("op", ";")) { init = null; next() }
        else { init = parseAssignOrDeclNoSemi(); eat("op", ";") }
        val cond: Expr = if (at("op", ";")) Expr.Num(1) else parseExpr()
        eat("op", ";")
        val post: Stmt? = if (at("op", ")")) null else parseAssignNoSemi()
        eat("op", ")")
        val body = parseStatement()
        val inner = mutableListOf<Stmt>(body)
        if (post != null) inner.add(post)
        val loop = Stmt.While(cond, Stmt.Block(inner))
        val outer = mutableListOf<Stmt>()
        if (init != null) outer.add(init)
        outer.add(loop)
        return Stmt.Block(outer)
    }

    private fun parseAssignOrDeclNoSemi(): Stmt {
        if (at("kw", "int")) {
            eat("kw", "int")
            val name = eat("ident").value as String
            var init: Expr? = null
            if (at("op", "=")) { next(); init = parseExpr() }
            return Stmt.Decl(name, init)
        }
        return parseAssignNoSemi()
    }

    private fun parseAssignNoSemi(): Stmt {
        val name = eat("ident").value as String
        if (at("op", "[")) {
            next()
            val idx = parseExpr()
            eat("op", "]")
            val op = eat("op").value as String
            val expr: Expr = when {
                op == "=" -> parseExpr()
                op in compound -> {
                    val rhs = parseExpr()
                    Expr.BinOp(compound[op]!!, Expr.Index(name, idx), rhs)
                }
                else -> throw CompileError("expected assignment operator, got '$op'")
            }
            return Stmt.IdxAssign(name, idx, expr)
        }
        val op = eat("op").value as String
        val expr: Expr = when {
            op == "=" -> parseExpr()
            op in compound -> {
                val rhs = parseExpr()
                Expr.BinOp(compound[op]!!, Expr.Var(name), rhs)
            }
            else -> throw CompileError("expected assignment operator, got '$op'")
        }
        return Stmt.Assign(name, expr)
    }

    // ── expression grammar (precedence climbing) ──
    private fun parseExpr(): Expr = parseLogor()

    private fun leftAssoc(sub: () -> Expr, ops: Set<String>): Expr {
        var left = sub()
        while (at("op") && peek().value in ops) {
            val op = next().value as String
            val right = sub()
            left = Expr.BinOp(op, left, right)
        }
        return left
    }

    private fun parseLogor(): Expr {
        var left = parseLogand()
        while (at("op", "||")) {
            next()
            val right = parseLogand()
            left = Expr.LogOr(left, right)
        }
        return left
    }

    private fun parseLogand(): Expr {
        var left = parseEquality()
        while (at("op", "&&")) {
            next()
            val right = parseEquality()
            left = Expr.LogAnd(left, right)
        }
        return left
    }

    private fun parseEquality(): Expr = leftAssoc(::parseRelational, setOf("==", "!="))
    private fun parseRelational(): Expr = leftAssoc(::parseBitor, setOf("<", ">", "<=", ">="))
    private fun parseBitor(): Expr = leftAssoc(::parseBitxor, setOf("|"))
    private fun parseBitxor(): Expr = leftAssoc(::parseBitand, setOf("^"))
    private fun parseBitand(): Expr = leftAssoc(::parseShift, setOf("&"))
    private fun parseShift(): Expr = leftAssoc(::parseAdditive, setOf("<<", ">>"))
    private fun parseAdditive(): Expr = leftAssoc(::parseMultiplicative, setOf("+", "-"))
    private fun parseMultiplicative(): Expr = leftAssoc(::parseUnary, setOf("*"))

    private fun parseUnary(): Expr {
        if (at("op") && peek().value in setOf("-", "~", "!")) {
            val op = next().value as String
            val operand = parseUnary()
            return Expr.UnOp(op, operand)
        }
        if (at("op", "&")) {
            next()
            val name = eat("ident").value as String
            if (at("op", "[")) {
                next()
                val idx = parseExpr()
                eat("op", "]")
                return Expr.AddrOfIndex(name, idx)
            }
            return Expr.AddrOf(name)
        }
        if (at("op", "*")) {
            next()
            val operand = parseUnary()
            return Expr.Deref(operand)
        }
        return parsePrimary()
    }

    private fun parsePrimary(): Expr {
        val t = peek()
        if (t.kind == "int_lit") {
            next()
            return Expr.Num((t.value as Int) and 0xFF)
        }
        if (t.kind == "ident") {
            next()
            if (at("op", "(")) {
                next()
                val args = parseArgs()
                eat("op", ")")
                return Expr.Call(t.value as String, args)
            }
            if (at("op", "[")) {
                next()
                val idx = parseExpr()
                eat("op", "]")
                return Expr.Index(t.value as String, idx)
            }
            return Expr.Var(t.value as String)
        }
        if (t.kind == "op" && t.value == "(") {
            next()
            val e = parseExpr()
            eat("op", ")")
            return e
        }
        throw CompileError("line ${t.line}: unexpected token '${t.value}'")
    }

    private fun parseArgs(): List<Expr> {
        if (at("op", ")")) return emptyList()
        val args = mutableListOf(parseExpr())
        while (at("op", ",")) { next(); args.add(parseExpr()) }
        return args
    }
}

// ── Code generator ─────────────────────────────────────────────────────────

private class CodeGen(private val compact: Boolean, private val safeCompare: Boolean) {
    val code = mutableListOf<String>()
    private val vars = mutableMapOf<String, String>()          // C var name -> data label
    private val consts = mutableMapOf<Int, String>()           // value -> label (dedup)
    private val temps = mutableListOf<String>()
    private var labelN = 0
    private var tempN = 0
    private val declaredOrder = mutableListOf<String>()
    private val arrays = mutableMapOf<String, Triple<String, String, Int>>()  // name -> (data,base,size)
    private val arrayData = mutableListOf<Triple<String, List<Int>, String>>() // (data,vals,base)
    private val globalSlots = mutableListOf<Pair<String, Int>>()  // (label, init)
    private val addrofSlots = linkedMapOf<String, String>()       // slot -> addrof byte label
    private var ixtmp: String? = null

    // Parallel to `code`: the C source line each emitted line came from (null for
    // lines with no C origin). Appended on every emit()/comment() from curCLine;
    // only consumed (as `# @c:N`) when annotateCLines is on. peephole() keeps the
    // two lists in lockstep. curCLine is the line of the statement currently being
    // generated, set/restored by genStmt.
    val codeLines = mutableListOf<Int?>()
    private var curCLine: Int? = null

    private fun emit(text: String) { code.add(text); codeLines.add(curCLine) }
    private fun comment(text: String) { code.add("        # $text"); codeLines.add(curCLine) }

    private fun newLabel(base: String): String {
        labelN++
        return "L${labelN}_$base"
    }

    private fun varLabel(name: String): String =
        vars[name] ?: throw CompileError("use of undeclared variable '$name'")

    private fun declare(name: String): String {
        if (name in vars || name in arrays) {
            throw CompileError("variable '$name' declared twice")
        }
        val label = "v_$name"
        vars[name] = label
        declaredOrder.add(label)
        return label
    }

    private fun declareArray(name: String, size: Int, initBytes: List<Int>?): String {
        if (name in arrays || name in vars) {
            throw CompileError("variable '$name' declared twice")
        }
        val dataLabel = "arr_$name"
        val baseLabel = "arrbase_$name"
        val vals = (initBytes?.toMutableList() ?: mutableListOf())
        while (vals.size < size) vals.add(0)
        arrays[name] = Triple(dataLabel, baseLabel, size)
        arrayData.add(Triple(dataLabel, vals, baseLabel))
        return dataLabel
    }

    private fun addGlobalSlot(label: String, init: Int = 0): String {
        if (label !in vars) {
            vars[label] = label
            globalSlots.add(label to init)
        }
        return label
    }

    private fun addrofLabel(name: String): String {
        val slot = varLabel(name)
        return addrofSlots.getOrPut(slot) { "addrof_$slot" }
    }

    private fun constLabel(value: Int): String {
        val v = value and 0xFF
        return consts.getOrPut(v) { _constName(v) }
    }

    private fun newTemp(): String {
        tempN++
        val label = "t$tempN"
        temps.add(label)
        return label
    }

    private fun sharedIndexTemp(): String {
        if (ixtmp == null) ixtmp = newTemp()
        return ixtmp!!
    }

    // -- stack primitives (self-modifying indirect load/store) --
    private fun genPush(slot: String) {
        val addr = newLabel("push_addr")
        emit("        load  sp         # push $slot")
        emit("        sub   ${constLabel(1)}")
        emit("        store sp         # sp -= 1")
        emit("        store $addr       # patch store-address to sp")
        emit("        load  $slot")
        emit("        21               # STORE opcode (raw)")
        emit("$addr: 0                # <- mem[sp] := $slot")
    }

    private fun genPop(slot: String) {
        val addr = newLabel("pop_addr")
        emit("        load  sp         # pop -> $slot")
        emit("        store $addr       # patch load-address to sp")
        emit("        20               # LOAD opcode (raw)")
        emit("$addr: 0                # <- acc := mem[sp]")
        emit("        store $slot")
        emit("        load  sp")
        emit("        add   ${constLabel(1)}")
        emit("        store sp         # sp += 1")
    }

    // -- expression code generation: result left in ACC --
    private fun genExpr(e: Expr) {
        when (e) {
            is Expr.Num -> emit("        load  ${constLabel(e.value)}")
            is Expr.Var -> {
                val name = e.name
                if (name in arrays) {
                    val (_, base, _) = arrays[name]!!
                    emit("        load  $base")
                } else {
                    emit("        load  ${varLabel(name)}")
                }
            }
            is Expr.UnOp -> genUnop(e)
            is Expr.BinOp -> genBinop(e)
            is Expr.LogAnd -> genLogical(e)
            is Expr.LogOr -> genLogical(e)
            is Expr.Index -> genIndexRead(e)
            is Expr.AddrOf -> emit("        load  ${addrofLabel(e.name)}")
            is Expr.AddrOfIndex -> genElementAddr(indexBaseLabel(e.name), e.idx)
            is Expr.Deref -> genDerefRead(e)
            is Expr.Call -> throw CompileError("cannot generate expression $e")
        }
    }

    private fun genElementAddr(baseLabel: String, idxExpr: Expr) {
        if (idxExpr is Expr.Num) {
            emit("        load  $baseLabel")
            if ((idxExpr.value and 0xFF) != 0) {
                emit("        add   ${constLabel(idxExpr.value)}")
            }
            return
        }
        genExpr(idxExpr)
        val t = sharedIndexTemp()
        emit("        store $t")
        emit("        load  $baseLabel")
        emit("        add   $t")
    }

    private fun indexBaseLabel(name: String): String {
        if (name in arrays) {
            val (_, base, _) = arrays[name]!!
            return base
        }
        return varLabel(name)
    }

    private fun constElemLabel(name: String, idx: Expr): String? {
        if (idx !is Expr.Num || name !in arrays) return null
        val (dataLabel, _, size) = arrays[name]!!
        val off = idx.value and 0xFF
        if (off >= size) return null
        return if (off == 0) dataLabel else "$dataLabel+$off"
    }

    private fun genIndexRead(e: Expr.Index) {
        val name = e.name
        val idx = e.idx
        val direct = constElemLabel(name, idx)
        if (direct != null) {
            emit("        load  $direct       # $name[${(idx as Expr.Num).value}]")
            return
        }
        val base = indexBaseLabel(name)
        val addr = newLabel("iload_arg")
        genElementAddr(base, idx)
        emit("        store $addr       # patch LOAD address to &$name[idx]")
        emit("        20               # LOAD opcode (raw)")
        emit("$addr: 0                # <- acc := $name[idx]")
    }

    private fun genDerefRead(e: Expr.Deref) {
        val addr = newLabel("dload_arg")
        genExpr(e.ptr)
        emit("        store $addr       # patch LOAD address to *p")
        emit("        20               # LOAD opcode (raw)")
        emit("$addr: 0                # <- acc := *p")
    }

    private fun genDerefWrite(s: Stmt.DerefAssign) {
        comment("*p = ...")
        val valt = newTemp()
        genExpr(s.expr)
        emit("        store $valt")
        val addr = newLabel("dstore_arg")
        genExpr(s.ptr)
        emit("        store $addr       # patch STORE address to *p")
        emit("        load  $valt")
        emit("        21               # STORE opcode (raw)")
        emit("$addr: 0                # <- *p := acc")
    }

    private fun genUnop(e: Expr.UnOp) {
        when (e.op) {
            "-" -> { genDiff(Expr.Num(0), e.operand); return }
            "~" -> { genExpr(e.operand); emit("        not"); return }
            "!" -> {
                genExpr(e.operand)
                val lZero = newLabel("not_zero")
                val lEnd = newLabel("not_end")
                emit("        ifzero $lZero")
                emit("        load  ${constLabel(0)}")
                emit("        goto  $lEnd")
                emit("$lZero: load  ${constLabel(1)}")
                emit("$lEnd: nop")
                return
            }
            else -> throw CompileError("unknown unary op '${e.op}'")
        }
    }

    private fun genLogical(e: Expr) {
        val kind = if (e is Expr.LogAnd) "logand" else "logor"
        val (left, right) = when (e) {
            is Expr.LogAnd -> e.left to e.right
            is Expr.LogOr -> e.left to e.right
            else -> throw CompileError("not a logical op")
        }
        val lShort = newLabel("${kind}_short")
        val lEnd = newLabel("${kind}_end")
        genExpr(left)
        if (kind == "logand") {
            emit("        ifzero $lShort")
        } else {
            val lEval = newLabel("logor_eval")
            emit("        ifzero $lEval")
            emit("        goto  $lShort")
            emit("$lEval: nop")
        }
        genExpr(right)
        emit("        ifzero $lEnd        # b == 0 -> result already 0")
        emit("        load  ${constLabel(1)}")
        emit("        goto  $lEnd")
        emit("$lShort: load  ${constLabel(if (kind == "logand") 0 else 1)}")
        emit("$lEnd: nop")
    }

    private val memOp = mapOf("+" to "add", "-" to "sub", "&" to "and", "|" to "or", "^" to "xor")

    private fun genBinop(e: Expr.BinOp) {
        val op = e.op
        val left = e.left
        val right = e.right

        if (op in setOf("==", "!=", "<", ">", "<=", ">=")) {
            genComparison(op, left, right)
            return
        }
        if (op == "*") { genMultiply(left, right); return }

        // Fast path: right operand already lives in a fixed data byte.
        if (op in memOp && (right is Expr.Num || right is Expr.Var)) {
            genExpr(left)
            val slot = if (right is Expr.Num) constLabel(right.value)
                       else varLabel((right as Expr.Var).name)
            emit("        ${memOp[op]!!.padEnd(5)} $slot")
            return
        }
        // Constant shift (compact codegen).
        if (compact && op in setOf("<<", ">>") && right is Expr.Num) {
            genExpr(left)
            genShift(op, right, null)
            return
        }
        // General: evaluate right first, spill it, then left, then apply op.
        genExpr(right)
        val t = newTemp()
        emit("        store $t")
        genExpr(left)
        when {
            op in memOp -> emit("        ${memOp[op]!!.padEnd(5)} $t")
            op in setOf("<<", ">>") -> genShift(op, right, t)
            else -> throw CompileError("unknown binary op '$op'")
        }
    }

    private fun genShift(op: String, right: Expr, spilledRight: String?) {
        val instr = if (op == "<<") "left" else "right"
        if (right is Expr.Num) {
            repeat(right.value and 0xFF) { emit("        $instr") }
            return
        }
        val accTmp = newTemp()
        val cntTmp = newTemp()
        val lLoop = newLabel("shift_loop")
        val lEnd = newLabel("shift_end")
        emit("        store $accTmp       # value to shift")
        emit("        load  $spilledRight")
        emit("        store $cntTmp       # shift count")
        emit("$lLoop: load  $cntTmp")
        emit("        ifzero $lEnd")
        emit("        load  $accTmp")
        emit("        $instr")
        emit("        store $accTmp")
        emit("        load  $cntTmp")
        emit("        sub   ${constLabel(1)}")
        emit("        store $cntTmp")
        emit("        goto  $lLoop")
        emit("$lEnd: load  $accTmp")
    }

    private fun genMultiply(left: Expr, right: Expr) {
        comment("multiply by double-and-add")
        val aTmp = newTemp()
        val cntTmp = newTemp()
        val resTmp = newTemp()
        genExpr(left)
        emit("        store $aTmp")
        genExpr(right)
        emit("        store $cntTmp")
        emit("        load  ${constLabel(0)}")
        emit("        store $resTmp")
        val lLoop = newLabel("mul_loop")
        val lSkip = newLabel("mul_skip")
        val lEnd = newLabel("mul_end")
        emit("$lLoop: load  $cntTmp")
        emit("        ifzero $lEnd        # cnt == 0 -> done")
        emit("        load  $cntTmp")
        emit("        and   ${constLabel(1)}  # low bit of cnt set?")
        emit("        ifzero $lSkip       # bit 0 -> don't add this pass")
        emit("        load  $resTmp")
        emit("        add   $aTmp")
        emit("        store $resTmp       # res += a")
        emit("$lSkip: load  $aTmp")
        emit("        left")
        emit("        store $aTmp         # a <<= 1 (double)")
        emit("        load  $cntTmp")
        emit("        right")
        emit("        store $cntTmp       # cnt >>= 1 (halve)")
        emit("        goto  $lLoop")
        emit("$lEnd: load  $resTmp")
    }

    // Produce (left - right) in ACC, used by comparisons.
    private fun genDiff(left: Expr, right: Expr) {
        if (compact && right is Expr.Num && right.value == 0) {
            genExpr(left)
            return
        }
        if (right is Expr.Num || right is Expr.Var) {
            genExpr(left)
            val slot = if (right is Expr.Num) constLabel(right.value)
                       else varLabel((right as Expr.Var).name)
            emit("        sub   $slot")
            return
        }
        genExpr(right)
        val t = newTemp()
        emit("        store $t")
        genExpr(left)
        emit("        sub   $t")
    }

    private fun genCmpSign(x: Expr, y: Expr) {
        if (!safeCompare) {
            genDiff(x, y)
            return
        }
        genExpr(x)
        val xt = newTemp()
        emit("        store $xt")
        genExpr(y)
        val yt = newTemp()
        emit("        store $yt")
        // d = x - y
        emit("        load  $xt")
        emit("        sub   $yt")
        val dt = newTemp()
        emit("        store $dt")
        // term1 = ~x & y
        emit("        load  $xt")
        emit("        not")
        emit("        and   $yt")
        val t1 = newTemp()
        emit("        store $t1")
        // term2 = ~(x ^ y) & d
        emit("        load  $xt")
        emit("        xor   $yt")
        emit("        not")
        emit("        and   $dt")
        // result = term1 | term2
        emit("        or    $t1")
    }

    private fun genComparison(op: String, left: Expr, right: Expr) {
        if (op == "==" || op == "!=") {
            genDiff(left, right)
            val lEq = newLabel("eq")
            val lEnd = newLabel("eq_end")
            emit("        ifzero $lEq")
            emit("        load  ${constLabel(if (op == "!=") 1 else 0)}")
            emit("        goto  $lEnd")
            emit("$lEq: load  ${constLabel(if (op == "!=") 0 else 1)}")
            emit("$lEnd: nop")
            return
        }
        val wantNeg: Boolean
        when (op) {
            "<" -> { genCmpSign(left, right); wantNeg = true }
            ">" -> { genCmpSign(right, left); wantNeg = true }
            ">=" -> { genCmpSign(left, right); wantNeg = false }
            "<=" -> { genCmpSign(right, left); wantNeg = false }
            else -> throw CompileError("unknown comparison '$op'")
        }
        emit("        and   ${constLabel(0x80)}")
        val lNeg = newLabel("cmp_neg")
        val lEnd = newLabel("cmp_end")
        emit("        ifzero $lNeg")
        emit("        load  ${constLabel(if (wantNeg) 1 else 0)}")
        emit("        goto  $lEnd")
        emit("$lNeg: load  ${constLabel(if (wantNeg) 0 else 1)}")
        emit("$lEnd: nop")
    }

    // -- condition for if/while: jump to false_label when cond is false --
    private fun genCondBranch(cond: Expr, falseLabel: String) {
        if (cond is Expr.BinOp && (cond.op == "==" || cond.op == "!=")) {
            genDiff(cond.left, cond.right)
            if (cond.op == "==") {
                val lTrue = newLabel("cond_true")
                emit("        ifzero $lTrue")
                emit("        goto  $falseLabel")
                emit("$lTrue: nop")
            } else {
                emit("        ifzero $falseLabel")
            }
            return
        }
        if (cond is Expr.BinOp && cond.op in setOf("<", ">", "<=", ">=")) {
            val trueIfNeg: Boolean
            when (cond.op) {
                "<" -> { genCmpSign(cond.left, cond.right); trueIfNeg = true }
                ">" -> { genCmpSign(cond.right, cond.left); trueIfNeg = true }
                ">=" -> { genCmpSign(cond.left, cond.right); trueIfNeg = false }
                else -> { genCmpSign(cond.right, cond.left); trueIfNeg = false }
            }
            emit("        and   ${constLabel(0x80)}")
            if (trueIfNeg) {
                emit("        ifzero $falseLabel")
            } else {
                val lTrue = newLabel("cond_true")
                emit("        ifzero $lTrue")
                emit("        goto  $falseLabel")
                emit("$lTrue: nop")
            }
            return
        }
        genExpr(cond)
        emit("        ifzero $falseLabel")
    }

    // -- statements --
    private fun genStmt(s: Stmt) {
        // Tag every line this statement emits with its C source line. Nested
        // statements (Block/If/While recurse into genStmt) set their own line and
        // restore the parent's afterwards, so each line gets the nearest origin.
        val prev = curCLine
        if (s.cLine != null) curCLine = s.cLine
        try {
            genStmtInner(s)
        } finally {
            curCLine = prev
        }
    }

    private fun genStmtInner(s: Stmt) {
        when (s) {
            is Stmt.Block -> s.stmts.forEach { genStmt(it) }
            is Stmt.Decl -> {
                val label = declare(s.name)
                comment("int ${s.name}" + if (s.init != null) " = ..." else "")
                if (s.init != null) {
                    genExpr(s.init)
                    emit("        store $label")
                }
            }
            is Stmt.Assign -> {
                comment("${s.name} = ...")
                genExpr(s.expr)
                emit("        store ${varLabel(s.name)}")
            }
            is Stmt.ArrayDecl -> {
                comment("int ${s.name}[${s.size}]" + if (s.init != null) " = {...}" else "")
                var initBytes: List<Int>? = null
                if (s.init != null) {
                    val bytes = mutableListOf<Int>()
                    for (x in s.init) {
                        if (x !is Expr.Num) {
                            throw CompileError("array '${s.name}' initializers must be constants")
                        }
                        bytes.add(x.value and 0xFF)
                    }
                    initBytes = bytes
                }
                declareArray(s.name, s.size, initBytes)
            }
            is Stmt.IdxAssign -> genIndexWrite(s)
            is Stmt.DerefAssign -> genDerefWrite(s)
            is Stmt.BuiltinStmt -> genBuiltin(s)
            is Stmt.Return -> {
                comment("return")
                genExpr(s.expr!!)
                emit("        stop")
            }
            is Stmt.If -> genIf(s)
            is Stmt.While -> genWhile(s)
            is Stmt.Label -> emit("${s.name}: nop")
            is Stmt.Goto -> emit("        goto  ${s.name}")
            is Stmt.SetMark -> {
                emit("        load  ${constLabel(s.k)}")
                emit("        store ${s.fname}__mark")
            }
            is Stmt.CallJump -> {
                emit("        goto  ${s.fname}__body")
                emit("${s.cont}: nop")
            }
            is Stmt.Push -> genPush(varLabel(s.slot))
            is Stmt.Pop -> genPop(varLabel(s.slot))
            is Stmt.ExprStmt -> throw CompileError("cannot generate statement $s")
            is Stmt.CallSite -> throw CompileError("cannot generate statement $s")
        }
    }

    private fun genIndexWrite(s: Stmt.IdxAssign) {
        val name = s.name
        val idx = s.idx
        val direct = constElemLabel(name, idx)
        if (direct != null) {
            comment("$name[${(idx as Expr.Num).value}] = ...")
            genExpr(s.expr)
            emit("        store $direct       # $name[${(idx as Expr.Num).value}]")
            return
        }
        val base = indexBaseLabel(name)
        comment("$name[idx] = ...")
        val valt = newTemp()
        genExpr(s.expr)
        emit("        store $valt")
        val addr = newLabel("istore_arg")
        genElementAddr(base, idx)
        emit("        store $addr       # patch STORE address to &$name[idx]")
        emit("        load  $valt")
        emit("        21               # STORE opcode (raw)")
        emit("$addr: 0                # <- $name[idx] := acc")
    }

    private fun genIf(s: Stmt.If) {
        comment("if")
        if (s.els == null) {
            val lEnd = newLabel("if_end")
            genCondBranch(s.cond, lEnd)
            genStmt(s.then)
            emit("$lEnd: nop")
        } else {
            val lElse = newLabel("else")
            val lEnd = newLabel("if_end")
            genCondBranch(s.cond, lElse)
            genStmt(s.then)
            emit("        goto  $lEnd")
            emit("$lElse: nop")
            genStmt(s.els)
            emit("$lEnd: nop")
        }
    }

    private fun genWhile(s: Stmt.While) {
        val lTop = newLabel("while_top")
        val lEnd = newLabel("while_end")
        comment("while")
        emit("$lTop: nop")
        genCondBranch(s.cond, lEnd)
        genStmt(s.body)
        emit("        goto  $lTop")
        emit("$lEnd: nop")
    }

    private fun genBuiltin(s: Stmt.BuiltinStmt) {
        when (s.name) {
            "write" -> genWrite(s.args[0], s.args[1])
            "read" -> genRead(s.args[0], s.cap)
            else -> throw CompileError("unknown builtin '${s.name}'")
        }
    }

    private fun genCopyLoop(srcBase: String, dstBase: String, countSlot: String, tag: String) {
        val i = newTemp()
        val lTop = newLabel("${tag}_top")
        val lEnd = newLabel("${tag}_end")
        val ldAddr = newLabel("${tag}_ld")
        val stAddr = newLabel("${tag}_st")
        val one = constLabel(1)

        emit("        load  ${constLabel(0)}")
        emit("        store $i            # i = 0")
        emit("$lTop: nop")
        emit("        load  $i")
        emit("        sub   $countSlot")
        emit("        and   ${constLabel(0x80)}  # sign bit: i < count?")
        emit("        ifzero $lEnd        # i >= count -> done")
        emit("        load  $srcBase")
        emit("        add   $i")
        emit("        store $ldAddr")
        emit("        load  $dstBase")
        emit("        add   $i")
        emit("        store $stAddr")
        emit("        20               # LOAD opcode (raw)")
        emit("$ldAddr: 0                # <- src address patched here")
        emit("        21               # STORE opcode (raw)")
        emit("$stAddr: 0                # <- dst address patched here")
        emit("        load  $i")
        emit("        add   $one")
        emit("        store $i            # i++")
        emit("        goto  $lTop")
        emit("$lEnd: nop")
    }

    private fun ioBaseSlot(): String = constLabel(IO_BASE)

    private fun genWrite(dataExpr: Expr, countExpr: Expr) {
        comment("write(data, k)")
        val src = newTemp()
        genExpr(dataExpr)
        emit("        store $src            # src = &data[0]")
        val k = newTemp()
        genExpr(countExpr)
        emit("        store $k            # k")
        genCopyLoop(src, ioBaseSlot(), k, "write")
    }

    private fun genRead(dataExpr: Expr, cap: String?) {
        comment("read(data)")
        val dst = newTemp()
        genExpr(dataExpr)
        emit("        store $dst            # dst = &data[0]")
        emit("        load  ${constLabel(0)}")
        emit("        store ready")
        val lWait = newLabel("read_wait")
        emit("$lWait: load  ready")
        emit("        ifzero $lWait       # spin until input arrives")
        val nn = newTemp()
        emit("        load  ready")
        emit("        store $nn            # n = ready (count)")
        genCopyLoop(ioBaseSlot(), dst, nn, "read")
        emit("        load  $nn            # result = count read")
        if (cap != null) {
            emit("        store ${varLabel(cap)}")
        }
    }

    private fun registerSlots(name: String, params: List<String>) {
        for (p in params) addGlobalSlot("${name}__p_$p")
        addGlobalSlot("${name}__ret")
        addGlobalSlot("${name}__mark")
    }

    private fun genFunction(name: String, @Suppress("UNUSED_PARAMETER") params: List<String>,
                            bodyBlock: Stmt, nSites: Int) {
        emit("")
        comment("function $name(...) — single body, marker-dispatch return")
        emit("${name}__body: nop")
        genStmt(bodyBlock)
        if (code.last().trim() != "goto  ${name}__dispatch") {
            emit("        goto  ${name}__dispatch")
        }
        emit("${name}__dispatch: nop")
        for (k in 0 until nSites) {
            emit("        load  ${name}__mark")
            if (k != 0) {
                emit("        sub   ${constLabel(k)}")
            }
            emit("        ifzero ${name}__cont_$k")
        }
        emit("        stop            # unreachable: bad $name marker")
    }

    /**
     * Drop a `load X` immediately following `store X` (store leaves ACC
     * unchanged). Only safe when the `load` carries NO label. [srcLines] is the
     * parallel C-source-line list, filtered in lockstep so each annotation stays
     * attached to the correct final line. Returns the filtered (code, lines).
     */
    private fun peephole(src: List<String>, srcLines: List<Int?>): Pair<List<String>, List<Int?>> {
        data class Parts(val label: String?, val mn: String?, val arg: String?)
        fun parts(line: String): Parts {
            var body = line.split("#", limit = 2)[0].trim()
            var label: String? = null
            if (":" in body) {
                val idx = body.indexOf(':')
                label = body.substring(0, idx).trim()
                body = body.substring(idx + 1).trim()
            }
            val toks = body.split(Regex("\\s+")).filter { it.isNotEmpty() }
            return Parts(label, toks.getOrNull(0), toks.getOrNull(1))
        }
        fun nextCode(start: Int): Int? {
            var j = start
            while (j < src.size) {
                if (parts(src[j]).mn != null) return j
                j++
            }
            return null
        }
        val out = mutableListOf<String>()
        val outLines = mutableListOf<Int?>()
        var i = 0
        while (i < src.size) {
            val line = src[i]
            val p = parts(line)
            if (p.mn == "store" && p.arg != null) {
                val j = nextCode(i + 1)
                if (j != null) {
                    val p2 = parts(src[j])
                    if (p2.mn == "load" && p2.arg == p.arg && p2.label == null) {
                        out.addAll(src.subList(i, j))
                        outLines.addAll(srcLines.subList(i, j))
                        i = j + 1
                        continue
                    }
                }
            }
            out.add(line)
            outLines.add(srcLines[i])
            i++
        }
        return out to outLines
    }

    fun generate(mainBody: Stmt, bodies: List<FuncBody>, sourceName: String,
                 noStack: Boolean, annotateCLines: Boolean = false): String {
        for (b in bodies) registerSlots(b.name, b.params)

        if (!noStack) addGlobalSlot("sp", SP_TOP)

        genStmt(mainBody)
        emit("        stop            # fallthrough halt")

        for (b in bodies) genFunction(b.name, b.params, b.body, b.nSites)

        val lines = mutableListOf<String>()
        lines.add("# Generated by toycc from $sourceName")
        lines.add("# Toy CPU assembly. 8-bit values, wraps mod 256.")
        lines.add("# The return value ends up in ACC when the program stops.")
        lines.add("")
        // Peephole first (it drops lines), carrying the parallel C-line list in
        // lockstep so each annotation stays on the correct FINAL line.
        val (peeped, peepedLines) = peephole(code, codeLines)
        if (annotateCLines) {
            peeped.forEachIndexed { k, ln -> lines.add(annotateLine(ln, peepedLines[k])) }
        } else {
            lines.addAll(peeped)
        }
        lines.add("")
        lines.add(DATA_MARKER)
        for (label in declaredOrder) lines.add("$label: 0")
        for ((label, init) in globalSlots) lines.add("$label: $init")
        for (label in temps) lines.add("$label: 0")
        for (value in consts.keys.sorted()) lines.add("${consts[value]}: $value")
        for ((dataLabel, vals, baseLabel) in arrayData) {
            lines.add("$baseLabel: $dataLabel   # address of $dataLabel[0]")
            lines.add("$dataLabel: ${vals[0]}")
            for (idx in 1 until vals.size) lines.add("        ${vals[idx]}")
        }
        for ((slot, alabel) in addrofSlots) {
            lines.add("$alabel: $slot   # address of $slot")
        }
        return lines.joinToString("\n") + "\n"
    }
}

/** A lifted non-main function body ready for codegen. */
data class FuncBody(val name: String, val params: List<String>, val body: Stmt, val nSites: Int)

// ── Call-graph analysis ─────────────────────────────────────────────────────

private fun callsIn(node: Any?): List<String> {
    val acc = mutableListOf<String>()
    fun walkExpr(e: Expr) {
        when (e) {
            is Expr.Call -> { acc.add(e.name); e.args.forEach { walkExpr(it) } }
            is Expr.UnOp -> walkExpr(e.operand)
            is Expr.BinOp -> { walkExpr(e.left); walkExpr(e.right) }
            is Expr.LogAnd -> { walkExpr(e.left); walkExpr(e.right) }
            is Expr.LogOr -> { walkExpr(e.left); walkExpr(e.right) }
            is Expr.Index -> walkExpr(e.idx)
            is Expr.AddrOfIndex -> walkExpr(e.idx)
            is Expr.Deref -> walkExpr(e.ptr)
            is Expr.Num, is Expr.Var, is Expr.AddrOf -> {}
        }
    }
    fun walkStmt(s: Stmt) {
        when (s) {
            is Stmt.Block -> s.stmts.forEach { walkStmt(it) }
            is Stmt.Decl -> s.init?.let { walkExpr(it) }
            is Stmt.ArrayDecl -> s.init?.forEach { walkExpr(it) }
            is Stmt.Assign -> walkExpr(s.expr)
            is Stmt.IdxAssign -> { walkExpr(s.idx); walkExpr(s.expr) }
            is Stmt.DerefAssign -> { walkExpr(s.ptr); walkExpr(s.expr) }
            is Stmt.ExprStmt -> walkExpr(s.expr)
            is Stmt.Return -> s.expr?.let { walkExpr(it) }
            is Stmt.If -> { walkExpr(s.cond); walkStmt(s.then); s.els?.let { walkStmt(it) } }
            is Stmt.While -> { walkExpr(s.cond); walkStmt(s.body) }
            is Stmt.BuiltinStmt -> s.args.forEach { walkExpr(it) }
            else -> {}
        }
    }
    when (node) {
        is Expr -> walkExpr(node)
        is Stmt -> walkStmt(node)
    }
    return acc
}

private fun declaresArray(s: Stmt): Boolean {
    var found = false
    fun walk(st: Stmt) {
        if (found) return
        when (st) {
            is Stmt.ArrayDecl -> found = true
            is Stmt.Block -> st.stmts.forEach { walk(it) }
            is Stmt.If -> { walk(st.then); st.els?.let { walk(it) } }
            is Stmt.While -> walk(st.body)
            else -> {}
        }
    }
    walk(s)
    return found
}

private fun takesAddress(node: Any?): Boolean {
    var found = false
    fun walkExpr(e: Expr) {
        if (found) return
        when (e) {
            is Expr.AddrOf, is Expr.AddrOfIndex -> found = true
            is Expr.UnOp -> walkExpr(e.operand)
            is Expr.BinOp -> { walkExpr(e.left); walkExpr(e.right) }
            is Expr.LogAnd -> { walkExpr(e.left); walkExpr(e.right) }
            is Expr.LogOr -> { walkExpr(e.left); walkExpr(e.right) }
            is Expr.Index -> walkExpr(e.idx)
            is Expr.Deref -> walkExpr(e.ptr)
            is Expr.Call -> e.args.forEach { walkExpr(it) }
            else -> {}
        }
    }
    fun walkStmt(s: Stmt) {
        if (found) return
        when (s) {
            is Stmt.Block -> s.stmts.forEach { walkStmt(it) }
            is Stmt.Decl -> s.init?.let { walkExpr(it) }
            is Stmt.ArrayDecl -> s.init?.forEach { walkExpr(it) }
            is Stmt.Assign -> walkExpr(s.expr)
            is Stmt.IdxAssign -> { walkExpr(s.idx); walkExpr(s.expr) }
            is Stmt.DerefAssign -> { walkExpr(s.ptr); walkExpr(s.expr) }
            is Stmt.ExprStmt -> walkExpr(s.expr)
            is Stmt.Return -> s.expr?.let { walkExpr(it) }
            is Stmt.If -> { walkExpr(s.cond); walkStmt(s.then); s.els?.let { walkStmt(it) } }
            is Stmt.While -> { walkExpr(s.cond); walkStmt(s.body) }
            is Stmt.BuiltinStmt -> s.args.forEach { walkExpr(it) }
            else -> {}
        }
    }
    when (node) {
        is Expr -> walkExpr(node)
        is Stmt -> walkStmt(node)
    }
    return found
}

private fun buildCallGraph(funcs: List<Func>): Map<String, Set<String>> {
    val defined = funcs.map { it.name }.toSet()
    val graph = mutableMapOf<String, Set<String>>()
    for (f in funcs) {
        graph[f.name] = callsIn(f.body).filter { it in defined }.toSet()
    }
    return graph
}

private fun findRecursive(graph: Map<String, Set<String>>): Set<String> {
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
    return graph.keys.filter { reachesSelf(it) }.toSet()
}

// ── Global-slots + marker-dispatch call scheme (CallLifter) ─────────────────

private class CallLifter(funcsList: List<Func>, recursive: Set<String>) {
    private val funcs: Map<String, Func> = funcsList.associateBy { it.name }
    private val recursive = recursive.toSet()
    private val callCounts = linkedMapOf<String, Int>()  // fname -> call sites so far
    private var uid = 0
    private var curCaller: String? = null

    // C line of the statement currently being lifted. Threaded through
    // [liftStmt] so the synthetic nodes built by [liftCall]/[expandCall]
    // (arg-temp Decls, CallSite, Push/Assign/SetMark/CallJump/capture/Pop, the
    // read BuiltinStmt) carry the originating statement's line into codegen.
    // Mirrors pytoy's CallLifter._cur_line / _stamp.
    private var curCLine: Int? = null

    /** Tag a freshly built node with [curCLine] (if any) and return it. */
    private fun <T : Stmt> stamp(node: T): T {
        if (curCLine != null) node.cLine = curCLine
        return node
    }

    private fun nextSite(fname: String): Int {
        val k = callCounts.getOrElse(fname) { 0 }
        callCounts[fname] = k + 1
        return k
    }

    private fun fresh(base: String): String {
        uid++
        return "${base}__u$uid"
    }

    fun run(): Pair<Stmt, List<FuncBody>> {
        val main = funcs["main"]!!
        if (main.params.isNotEmpty()) {
            throw CompileError("main must take no parameters (or void)")
        }
        curCaller = "main"
        val mainOut = mutableListOf<Stmt>()
        liftStmt(main.body, mainOut, null)
        val mainOut2 = insertSaveRestore("main", mainOut)
        val mainBlock = Stmt.Block(mainOut2)

        val lifted = linkedMapOf<String, Pair<List<String>, Stmt>>()
        val pending = ArrayDeque<String>()
        pending.addAll(callCounts.keys.filter { it != "main" })
        while (pending.isNotEmpty()) {
            val name = pending.removeLast()
            if (name in lifted) continue
            val f = funcs[name]!!
            val rename = mutableMapOf<String, String>()
            for (p in f.params) rename[p] = "${name}__p_$p"
            for (local in declaredNames(f.body)) {
                if (local !in rename) rename[local] = "${name}__l_$local"
            }
            val fbody2 = renameStmt(f.body, rename)
            curCaller = name
            val out = mutableListOf<Stmt>()
            liftStmt(fbody2, out, "${name}__ret")
            val out2 = insertSaveRestore(name, out)
            lifted[name] = f.params to Stmt.Block(out2)
            pending.addAll(callCounts.keys.filter { it != "main" && it !in lifted })
        }

        // Emit bodies in `lifted` insertion order — the order functions were
        // popped off the worklist — matching pytoy's `lifted.items()` exactly
        // (its dict preserves insertion order). This is NOT definition order;
        // using definition order here would shift addresses and break
        // byte-identity with pytoy's generated assembly. `lifted` is a
        // LinkedHashMap, so iterating its entries reproduces Python's order.
        val bodies = lifted.entries.map { (name, pair) ->
            val (params, block) = pair
            FuncBody(name, params, block, callCounts[name]!!)
        }
        return mainBlock to bodies
    }

    private fun declaredNames(node: Stmt): Set<String> {
        val names = linkedSetOf<String>()
        fun walk(s: Stmt) {
            when (s) {
                is Stmt.Decl -> names.add(s.name)
                is Stmt.ArrayDecl -> names.add(s.name)
                is Stmt.Block -> s.stmts.forEach { walk(it) }
                is Stmt.If -> { walk(s.then); s.els?.let { walk(it) } }
                is Stmt.While -> walk(s.body)
                else -> {}
            }
        }
        walk(node)
        return names
    }

    private fun renameExpr(e: Expr, rename: Map<String, String>): Expr = when (e) {
        is Expr.Var -> Expr.Var(rename.getOrElse(e.name) { e.name })
        is Expr.Index -> Expr.Index(rename.getOrElse(e.name) { e.name }, renameExpr(e.idx, rename))
        is Expr.AddrOf -> Expr.AddrOf(rename.getOrElse(e.name) { e.name })
        is Expr.AddrOfIndex -> Expr.AddrOfIndex(rename.getOrElse(e.name) { e.name }, renameExpr(e.idx, rename))
        is Expr.Deref -> Expr.Deref(renameExpr(e.ptr, rename))
        is Expr.UnOp -> Expr.UnOp(e.op, renameExpr(e.operand, rename))
        is Expr.BinOp -> Expr.BinOp(e.op, renameExpr(e.left, rename), renameExpr(e.right, rename))
        is Expr.LogAnd -> Expr.LogAnd(renameExpr(e.left, rename), renameExpr(e.right, rename))
        is Expr.LogOr -> Expr.LogOr(renameExpr(e.left, rename), renameExpr(e.right, rename))
        is Expr.Call -> Expr.Call(e.name, e.args.map { renameExpr(it, rename) })
        is Expr.Num -> e
    }

    private fun renameStmt(s: Stmt, rename: Map<String, String>): Stmt = carryLine(s, when (s) {
        is Stmt.Decl -> Stmt.Decl(rename.getOrElse(s.name) { s.name },
            s.init?.let { renameExpr(it, rename) })
        is Stmt.Assign -> Stmt.Assign(rename.getOrElse(s.name) { s.name }, renameExpr(s.expr, rename))
        is Stmt.ArrayDecl -> Stmt.ArrayDecl(rename.getOrElse(s.name) { s.name }, s.size,
            s.init?.map { renameExpr(it, rename) })
        is Stmt.IdxAssign -> Stmt.IdxAssign(rename.getOrElse(s.name) { s.name },
            renameExpr(s.idx, rename), renameExpr(s.expr, rename))
        is Stmt.DerefAssign -> Stmt.DerefAssign(renameExpr(s.ptr, rename), renameExpr(s.expr, rename))
        is Stmt.Block -> Stmt.Block(s.stmts.map { renameStmt(it, rename) })
        is Stmt.If -> Stmt.If(renameExpr(s.cond, rename), renameStmt(s.then, rename),
            s.els?.let { renameStmt(it, rename) })
        is Stmt.While -> Stmt.While(renameExpr(s.cond, rename), renameStmt(s.body, rename))
        is Stmt.Return -> Stmt.Return(s.expr?.let { renameExpr(it, rename) })
        is Stmt.ExprStmt -> Stmt.ExprStmt(renameExpr(s.expr, rename))
        is Stmt.Label, is Stmt.Goto -> s
        else -> s
    })

    // ── statement pass ──
    // Set the "current C line" for everything this statement emits (the nodes
    // appended here, plus any call sequences liftCalls appends via liftCall),
    // and restore it afterwards so a nested liftStmt doesn't leak its line
    // outward. A Block has no line of its own; its children set theirs.
    // Mirrors pytoy's lift_stmt wrapper.
    private fun liftStmt(s: Stmt, out: MutableList<Stmt>, retSlot: String?) {
        val prev = curCLine
        if (s.cLine != null) curCLine = s.cLine
        try {
            liftStmtInner(s, out, retSlot)
        } finally {
            curCLine = prev
        }
    }

    private fun liftStmtInner(s: Stmt, out: MutableList<Stmt>, retSlot: String?) {
        when (s) {
            is Stmt.Block -> s.stmts.forEach { liftStmt(it, out, retSlot) }
            is Stmt.Decl -> {
                val init2 = s.init?.let { liftCalls(it, out) }
                out.add(carryLine(s, Stmt.Decl(s.name, init2)))
            }
            is Stmt.ArrayDecl -> {
                val init2 = s.init?.map { liftCalls(it, out) }
                out.add(carryLine(s, Stmt.ArrayDecl(s.name, s.size, init2)))
            }
            is Stmt.Assign -> out.add(carryLine(s, Stmt.Assign(s.name, liftCalls(s.expr, out))))
            is Stmt.IdxAssign -> {
                val idx2 = liftCalls(s.idx, out)
                val expr2 = liftCalls(s.expr, out)
                out.add(carryLine(s, Stmt.IdxAssign(s.name, idx2, expr2)))
            }
            is Stmt.DerefAssign -> {
                val ptr2 = liftCalls(s.ptr, out)
                val expr2 = liftCalls(s.expr, out)
                out.add(carryLine(s, Stmt.DerefAssign(ptr2, expr2)))
            }
            is Stmt.ExprStmt -> liftCalls(s.expr, out)
            is Stmt.Return -> {
                val expr2 = liftCalls(s.expr!!, out)
                if (retSlot == null) {
                    out.add(carryLine(s, Stmt.Return(expr2)))
                } else {
                    out.add(carryLine(s, Stmt.Assign(retSlot, expr2)))
                    out.add(stamp(Stmt.Goto("${retSlot.dropLast(5)}__dispatch")))
                }
            }
            is Stmt.If -> {
                val cond2 = liftCalls(s.cond, out)
                val then2 = blockOf(s.then, retSlot)
                val else2 = s.els?.let { blockOf(it, retSlot) }
                out.add(carryLine(s, Stmt.If(cond2, then2, else2)))
            }
            is Stmt.While -> {
                val cond = s.cond
                if (callsIn(cond).isEmpty()) {
                    val bodyOut = mutableListOf<Stmt>()
                    liftStmt(s.body, bodyOut, retSlot)
                    out.add(carryLine(s, Stmt.While(cond, Stmt.Block(bodyOut))))
                    return
                }
                val end = fresh("while_end")
                val inner = mutableListOf<Stmt>()
                val cond2 = liftCalls(cond, inner)
                inner.add(stamp(Stmt.If(Expr.UnOp("!", cond2), Stmt.Goto(end), null)))
                liftStmt(s.body, inner, retSlot)
                out.add(carryLine(s, Stmt.While(Expr.Num(1), Stmt.Block(inner))))
                out.add(stamp(Stmt.Label(end)))
            }
            is Stmt.Label, is Stmt.Goto -> out.add(stamp(s))
            else -> throw CompileError("cannot compile statement $s")
        }
    }

    private fun blockOf(s: Stmt, retSlot: String?): Stmt {
        val inner = mutableListOf<Stmt>()
        liftStmt(s, inner, retSlot)
        return carryLine(s, Stmt.Block(inner))
    }

    // ── save/restore insertion (live-variable analysis) ──
    private fun insertSaveRestore(caller: String, stmts: List<Stmt>): List<Stmt> {
        val skip = caller !in recursive
        val liveAtLabel = mutableMapOf<String, Set<String>>()
        val callerSlots = callerSlots(caller, stmts)

        for (pass in 0 until 100) {
            val changed = booleanArrayOf(false)
            liveBlock(stmts, emptySet(), liveAtLabel, callerSlots, changed,
                record = false, caller = caller, skip = skip)
            if (!changed[0]) break
        }
        val (result, _) = liveBlock(stmts, emptySet(), liveAtLabel, callerSlots,
            booleanArrayOf(false), record = true, caller = caller, skip = skip)
        return result!!
    }

    private fun callerSlots(caller: String, stmts: List<Stmt>): Set<String> {
        val slots = mutableSetOf("${caller}__mark")
        val addrTaken = mutableSetOf<String>()
        val f = funcs[caller]!!
        for (p in f.params) slots.add("${caller}__p_$p")
        fun walkExpr(e: Expr) {
            when (e) {
                is Expr.AddrOf -> addrTaken.add(e.name)
                is Expr.AddrOfIndex -> { addrTaken.add(e.name); walkExpr(e.idx) }
                is Expr.UnOp -> walkExpr(e.operand)
                is Expr.BinOp -> { walkExpr(e.left); walkExpr(e.right) }
                is Expr.LogAnd -> { walkExpr(e.left); walkExpr(e.right) }
                is Expr.LogOr -> { walkExpr(e.left); walkExpr(e.right) }
                is Expr.Index -> walkExpr(e.idx)
                is Expr.Deref -> walkExpr(e.ptr)
                is Expr.Call -> e.args.forEach { walkExpr(it) }
                else -> {}
            }
        }
        fun walkStmt(s: Stmt) {
            when (s) {
                is Stmt.Decl -> { slots.add(s.name); s.init?.let { walkExpr(it) } }
                is Stmt.CallSite -> {
                    slots.add(s.cap)
                    s.paramPairs.forEach { }   // pairs are (string,string), no exprs
                    s.argTemps.forEach { }
                }
                is Stmt.Block -> s.stmts.forEach { walkStmt(it) }
                is Stmt.Assign -> walkExpr(s.expr)
                is Stmt.ArrayDecl -> s.init?.forEach { walkExpr(it) }
                is Stmt.IdxAssign -> { walkExpr(s.idx); walkExpr(s.expr) }
                is Stmt.DerefAssign -> { walkExpr(s.ptr); walkExpr(s.expr) }
                is Stmt.ExprStmt -> walkExpr(s.expr)
                is Stmt.Return -> s.expr?.let { walkExpr(it) }
                is Stmt.If -> { walkExpr(s.cond); walkStmt(s.then); s.els?.let { walkStmt(it) } }
                is Stmt.While -> { walkExpr(s.cond); walkStmt(s.body) }
                is Stmt.BuiltinStmt -> s.args.forEach { walkExpr(it) }
                else -> {}
            }
        }
        stmts.forEach { walkStmt(it) }
        return slots - addrTaken
    }

    private fun asStmt(x: Any?): Stmt? = when (x) {
        null -> null
        is List<*> -> @Suppress("UNCHECKED_CAST") Stmt.Block(x as List<Stmt>)
        is Stmt -> x
        else -> throw CompileError("unexpected node $x")
    }

    private fun exprUses(e: Expr, acc: MutableSet<String>) {
        when (e) {
            is Expr.Var -> acc.add(e.name)
            is Expr.Index -> { exprUses(e.idx, acc); acc.add(e.name) }
            is Expr.AddrOf -> acc.add(e.name)
            is Expr.AddrOfIndex -> { exprUses(e.idx, acc); acc.add(e.name) }
            is Expr.UnOp -> exprUses(e.operand, acc)
            is Expr.BinOp -> { exprUses(e.left, acc); exprUses(e.right, acc) }
            is Expr.LogAnd -> { exprUses(e.left, acc); exprUses(e.right, acc) }
            is Expr.LogOr -> { exprUses(e.left, acc); exprUses(e.right, acc) }
            is Expr.Deref -> exprUses(e.ptr, acc)
            is Expr.Call -> e.args.forEach { exprUses(it, acc) }
            is Expr.Num -> {}
        }
    }

    // Backward pass over a statement list. Returns (new_stmts-or-null, live-in).
    private fun liveBlock(
        stmts: List<Stmt>, live: Set<String>, liveAtLabel: MutableMap<String, Set<String>>,
        callerSlots: Set<String>, changed: BooleanArray, record: Boolean,
        caller: String, skip: Boolean,
    ): Pair<List<Stmt>?, Set<String>> {
        var cur = live.toMutableSet()
        val outRev = mutableListOf<Stmt>()
        for (s in stmts.reversed()) {
            val (newS, liveIn) = liveStmt(s, cur, liveAtLabel, callerSlots, changed,
                record, caller, skip)
            cur = liveIn.toMutableSet()
            if (newS is List<*>) {
                @Suppress("UNCHECKED_CAST")
                outRev.addAll((newS as List<Stmt>).reversed())
            } else {
                outRev.add(newS as Stmt)
            }
        }
        val result = outRev.reversed()
        return (if (record) result else null) to cur
    }

    // Returns (replacement: Stmt or List<Stmt>, live-in).
    private fun liveStmt(
        s: Stmt, liveIn: Set<String>, liveAtLabel: MutableMap<String, Set<String>>,
        callerSlots: Set<String>, changed: BooleanArray, record: Boolean,
        caller: String, skip: Boolean,
    ): Pair<Any, Set<String>> {
        val live = liveIn.toMutableSet()
        when (s) {
            is Stmt.Block -> {
                val (new, liveNew) = liveBlock(s.stmts, live, liveAtLabel, callerSlots,
                    changed, record, caller, skip)
                return (if (record) carryLine(s, Stmt.Block(new!!)) else s) to liveNew
            }
            is Stmt.Decl -> {
                live.remove(s.name)
                s.init?.let { exprUses(it, live) }
                return s to live
            }
            is Stmt.Assign -> {
                live.remove(s.name)
                exprUses(s.expr, live)
                return s to live
            }
            is Stmt.ArrayDecl -> {
                s.init?.forEach { exprUses(it, live) }
                return s to live
            }
            is Stmt.IdxAssign -> {
                exprUses(s.idx, live)
                exprUses(s.expr, live)
                live.add(s.name)
                return s to live
            }
            is Stmt.DerefAssign -> {
                exprUses(s.ptr, live)
                exprUses(s.expr, live)
                return s to live
            }
            is Stmt.BuiltinStmt -> {
                s.cap?.let { live.remove(it) }
                s.args.forEach { exprUses(it, live) }
                return s to live
            }
            is Stmt.Return -> {
                s.expr?.let { exprUses(it, live) }
                return s to live
            }
            is Stmt.If -> {
                val (newThen, liveThen) = liveStmt(s.then, live, liveAtLabel, callerSlots,
                    changed, record, caller, skip)
                val newElse: Any?
                val liveElse: Set<String>
                if (s.els == null) {
                    liveElse = live.toSet()
                    newElse = null
                } else {
                    val (ne, le) = liveStmt(s.els, live, liveAtLabel, callerSlots,
                        changed, record, caller, skip)
                    newElse = ne
                    liveElse = le
                }
                val merged = (liveThen + liveElse).toMutableSet()
                exprUses(s.cond, merged)
                val outS: Stmt = if (record) {
                    carryLine(s, Stmt.If(s.cond, asStmt(newThen)!!, asStmt(newElse)))
                } else s
                return outS to merged
            }
            is Stmt.While -> {
                val bodyLiveOut = live.toMutableSet()
                exprUses(s.cond, bodyLiveOut)
                for (pass in 0 until 100) {
                    val (_, liveInner) = liveBlock((s.body as Stmt.Block).stmts, bodyLiveOut,
                        liveAtLabel, callerSlots, changed, false, caller, skip)
                    val liveInner2 = liveInner.toMutableSet()
                    exprUses(s.cond, liveInner2)
                    if (bodyLiveOut.containsAll(liveInner2)) break
                    bodyLiveOut.addAll(liveInner2)
                }
                val outS: Stmt = if (record) {
                    val (newBody, _) = liveBlock((s.body as Stmt.Block).stmts, bodyLiveOut,
                        liveAtLabel, callerSlots, changed, true, caller, skip)
                    carryLine(s, Stmt.While(s.cond, Stmt.Block(newBody!!)))
                } else s
                return outS to bodyLiveOut
            }
            is Stmt.Label -> {
                val old = liveAtLabel.getOrElse(s.name) { emptySet() }
                if (!old.containsAll(live)) {
                    liveAtLabel[s.name] = old + live
                    changed[0] = true
                }
                return s to (liveAtLabel.getOrElse(s.name) { live }).toSet()
            }
            is Stmt.Goto -> {
                return s to (liveAtLabel.getOrElse(s.name) { emptySet() }).toSet()
            }
            is Stmt.CallSite -> {
                return expandCall(s, live, callerSlots, record, caller, skip)
            }
            else -> throw CompileError("liveness: unexpected stmt $s")
        }
    }

    private fun expandCall(
        s: Stmt.CallSite, liveIn: Set<String>, callerSlots: Set<String>,
        record: Boolean, caller: String, skip: Boolean,
    ): Pair<Any, Set<String>> {
        val liveAcross = ((liveIn intersect callerSlots) - setOf(s.cap)).toMutableSet()
        if (caller != "main") liveAcross.add("${caller}__mark")
        val save = liveAcross.sorted()  // Python sorted() => lexicographic string sort

        val live = liveIn.toMutableSet()
        live.remove(s.cap)
        for ((_, tmp) in s.paramPairs) live.add(tmp)
        live.addAll(save)

        if (!record) return s to live

        // Expand: [push saves] param-assigns setmark call_jump capture [pop rev].
        // Every emitted statement belongs to the same C statement as the call,
        // so tag them all with the CallSite node's recorded line. Mirrors
        // pytoy's expandCall `tag(...)` using stmt_lines[id(call_site)].
        val line = s.cLine
        fun <T : Stmt> tag(node: T): T { if (line != null) node.cLine = line; return node }
        val doSave = save.isNotEmpty() && !skip
        val stmts = mutableListOf<Stmt>()
        if (doSave) for (slot in save) stmts.add(tag(Stmt.Push(slot)))
        for ((pslot, tmp) in s.paramPairs) stmts.add(tag(Stmt.Assign(pslot, Expr.Var(tmp))))
        stmts.add(tag(Stmt.SetMark(s.name, s.k)))
        stmts.add(tag(Stmt.CallJump(s.name, s.cont)))
        stmts.add(tag(Stmt.Decl(s.cap, Expr.Var("${s.name}__ret"))))
        if (doSave) for (slot in save.reversed()) stmts.add(tag(Stmt.Pop(slot)))
        return stmts to live
    }

    // ── expression pass ──
    private fun liftCalls(e: Expr, out: MutableList<Stmt>): Expr = when (e) {
        is Expr.Num, is Expr.Var -> e
        is Expr.UnOp -> Expr.UnOp(e.op, liftCalls(e.operand, out))
        is Expr.BinOp -> {
            val l2 = liftCalls(e.left, out)
            val r2 = liftCalls(e.right, out)
            Expr.BinOp(e.op, l2, r2)
        }
        is Expr.Index -> Expr.Index(e.name, liftCalls(e.idx, out))
        is Expr.AddrOf -> e
        is Expr.AddrOfIndex -> Expr.AddrOfIndex(e.name, liftCalls(e.idx, out))
        is Expr.Deref -> Expr.Deref(liftCalls(e.ptr, out))
        is Expr.LogAnd -> {
            if (callsIn(e.left).isNotEmpty() || callsIn(e.right).isNotEmpty()) {
                throw CompileError("function calls inside && / || are not supported " +
                    "(assign the call result to a variable first)")
            }
            e
        }
        is Expr.LogOr -> {
            if (callsIn(e.left).isNotEmpty() || callsIn(e.right).isNotEmpty()) {
                throw CompileError("function calls inside && / || are not supported " +
                    "(assign the call result to a variable first)")
            }
            e
        }
        is Expr.Call -> liftCall(e, out)
    }

    private fun liftCall(call: Expr.Call, out: MutableList<Stmt>): Expr {
        val name = call.name
        val args = call.args

        if (name in IO_BUILTINS) {
            if (args.size != IO_BUILTINS[name]) {
                throw CompileError("$name() takes ${IO_BUILTINS[name]} argument(s), " +
                    "got ${args.size}")
            }
            val args2 = args.map { liftCalls(it, out) }
            if (name == "read") {
                val cap = fresh("readret")
                out.add(stamp(Stmt.Decl(cap, null)))
                out.add(stamp(Stmt.BuiltinStmt(name, args2, cap)))
                return Expr.Var(cap)
            }
            out.add(stamp(Stmt.BuiltinStmt(name, args2, null)))
            return Expr.Num(0)
        }

        val f = funcs[name] ?: throw CompileError("call to undefined function '$name'")
        if (args.size != f.params.size) {
            throw CompileError("call to '$name' passes ${args.size} args, " +
                "expects ${f.params.size}")
        }

        val argTemps = mutableListOf<String>()
        for (arg in args) {
            val arg2 = liftCalls(arg, out)
            val tmp = fresh("arg")
            out.add(stamp(Stmt.Decl(tmp, arg2)))
            argTemps.add(tmp)
        }
        val paramPairs = f.params.zip(argTemps).map { (pname, tmp) ->
            "${name}__p_$pname" to tmp
        }

        val k = nextSite(name)
        val cont = "${name}__cont_$k"
        val cap = fresh("callret")
        out.add(stamp(Stmt.CallSite(name, paramPairs, k, cont, cap, argTemps)))
        return Expr.Var(cap)
    }
}

private fun liftFunctions(funcs: List<Func>, recursive: Set<String>): Pair<Stmt, List<FuncBody>> =
    CallLifter(funcs, recursive).run()

// ── Constant folding + algebraic simplification ─────────────────────────────

private fun foldBinop(op: String, a: Int, b: Int): Int? = when (op) {
    "+" -> (a + b) and 0xFF
    "-" -> (a - b) and 0xFF
    "*" -> (a * b) and 0xFF
    "&" -> a and b
    "|" -> a or b
    "^" -> a xor b
    // Match Python's arbitrary-precision shift then mask. Kotlin's shl/shr only
    // use the low 5 bits of the count, so `1 shl 32` would wrongly be 1; for an
    // 8-bit value any shift of 8+ is 0 after masking, so clamp explicitly.
    "<<" -> if ((b and 0xFF) >= 8) 0 else (a shl (b and 0xFF)) and 0xFF
    ">>" -> if ((b and 0xFF) >= 8) 0 else (a and 0xFF) shr (b and 0xFF)
    "==" -> if (a == b) 1 else 0
    "!=" -> if (a != b) 1 else 0
    "<" -> if (a < b) 1 else 0
    ">" -> if (a > b) 1 else 0
    "<=" -> if (a <= b) 1 else 0
    ">=" -> if (a >= b) 1 else 0
    else -> null
}

private fun foldExpr(e: Expr): Expr {
    when (e) {
        is Expr.UnOp -> {
            val operand = foldExpr(e.operand)
            if (operand is Expr.Num) {
                val v = operand.value
                when (e.op) {
                    "-" -> return Expr.Num((-v) and 0xFF)
                    "~" -> return Expr.Num(v.inv() and 0xFF)
                    "!" -> return Expr.Num(if (v == 0) 1 else 0)
                }
            }
            // ~~x -> x,  --x -> x  (double involution)
            if (operand is Expr.UnOp && operand.op == e.op && (e.op == "~" || e.op == "-")) {
                return operand.operand
            }
            return Expr.UnOp(e.op, operand)
        }
        is Expr.BinOp -> {
            val left = foldExpr(e.left)
            val right = foldExpr(e.right)
            val op = e.op
            if (left is Expr.Num && right is Expr.Num) {
                val folded = foldBinop(op, left.value, right.value)
                if (folded != null) return Expr.Num(folded)
            }
            val ln = (left as? Expr.Num)?.value
            val rn = (right as? Expr.Num)?.value
            if (op in setOf("+", "-", "|", "^", "<<", ">>") && rn == 0) return left
            if (op in setOf("+", "|") && ln == 0) return right
            if (op == "*") {
                if (rn == 1) return left
                if (ln == 1) return right
                if (rn == 0 || ln == 0) return Expr.Num(0)
            }
            if (op == "&" && rn == 0xFF) return left
            if (op == "&" && ln == 0xFF) return right
            if (op == "&" && (rn == 0 || ln == 0)) return Expr.Num(0)
            return Expr.BinOp(op, left, right)
        }
        is Expr.LogAnd -> {
            val left = foldExpr(e.left)
            val right = foldExpr(e.right)
            if (left is Expr.Num && right is Expr.Num) {
                val a = if (left.value != 0) 1 else 0
                val b = if (right.value != 0) 1 else 0
                return Expr.Num(if (a != 0 && b != 0) 1 else 0)
            }
            return Expr.LogAnd(left, right)
        }
        is Expr.LogOr -> {
            val left = foldExpr(e.left)
            val right = foldExpr(e.right)
            if (left is Expr.Num && right is Expr.Num) {
                val a = if (left.value != 0) 1 else 0
                val b = if (right.value != 0) 1 else 0
                return Expr.Num(if (a != 0 || b != 0) 1 else 0)
            }
            return Expr.LogOr(left, right)
        }
        is Expr.Index -> return Expr.Index(e.name, foldExpr(e.idx))
        is Expr.AddrOfIndex -> return Expr.AddrOfIndex(e.name, foldExpr(e.idx))
        is Expr.Deref -> return Expr.Deref(foldExpr(e.ptr))
        is Expr.Call -> return Expr.Call(e.name, e.args.map { foldExpr(it) })
        is Expr.Num, is Expr.Var, is Expr.AddrOf -> return e
    }
}

private fun foldStmt(s: Stmt): Stmt = carryLine(s, when (s) {
    is Stmt.Decl -> Stmt.Decl(s.name, s.init?.let { foldExpr(it) })
    is Stmt.Assign -> Stmt.Assign(s.name, foldExpr(s.expr))
    is Stmt.IdxAssign -> Stmt.IdxAssign(s.name, foldExpr(s.idx), foldExpr(s.expr))
    is Stmt.DerefAssign -> Stmt.DerefAssign(foldExpr(s.ptr), foldExpr(s.expr))
    is Stmt.Return -> Stmt.Return(s.expr?.let { foldExpr(it) })
    is Stmt.ExprStmt -> Stmt.ExprStmt(foldExpr(s.expr))
    is Stmt.If -> Stmt.If(foldExpr(s.cond), foldStmt(s.then), s.els?.let { foldStmt(it) })
    is Stmt.While -> Stmt.While(foldExpr(s.cond), foldStmt(s.body))
    is Stmt.Block -> Stmt.Block(s.stmts.map { foldStmt(it) })
    else -> s
})

private fun foldProgram(funcs: List<Func>): List<Func> =
    funcs.map { Func(it.name, it.params, foldStmt(it.body)) }

// ── Options + entry point ───────────────────────────────────────────────────

/**
 * Compiler options. Everything is OFF by default: the plain output stays a
 * direct, readable translation. Optimizations are opt-in. Faithful to pytoy Opts.
 */
data class Opts(
    val fold: Boolean = false,
    val compact: Boolean = false,
    val stackfree: Boolean = false,
    val safeCompare: Boolean = false,
) {
    companion object {
        /** The --optimize umbrella: fold + compact (NOT stackfree/safeCompare). */
        fun allOn(): Opts = Opts(fold = true, compact = true)
    }
}

/**
 * Compile C source into Toy CPU assembly text (.toys). Faithful port of pytoy's
 * compile_source(src, source_name, opts).
 *
 * When annotateCLines is true, each emitted asm line that originates from a C
 * statement gets a trailing `# @c:N` comment (N = 1-based C source line). The
 * marker is inert (the assembler ignores everything after '#'), so the memory
 * image is identical to the unannotated output. Default false → byte-identical
 * to the plain output.
 */
fun compileSource(
    src: String,
    sourceName: String,
    opts: Opts = Opts(),
    annotateCLines: Boolean = false,
): String {
    val toks = lex(src)
    var funcs = Parser(toks).parseProgram()

    // Validate calls BEFORE folding (folding can drop a subexpression and hide
    // a call to an undefined function).
    val defined = funcs.map { it.name }.toSet()
    for (f in funcs) {
        for (callee in callsIn(f.body)) {
            if (callee !in defined && callee !in IO_BUILTINS) {
                throw CompileError("call to undefined function '$callee'")
            }
        }
    }

    if (opts.fold) funcs = foldProgram(funcs)

    val graph = buildCallGraph(funcs)
    val recursive = findRecursive(graph)

    if (opts.stackfree && recursive.isNotEmpty()) {
        val names = recursive.sorted().joinToString(", ")
        throw CompileError(
            "--stackfree cannot compile recursive functions ($names); " +
                "recursion needs a call stack. Remove the recursion or drop " +
                "--stackfree.")
    }

    for (f in funcs) {
        if (f.name in recursive && declaresArray(f.body)) {
            throw CompileError(
                "function '${f.name}' is recursive and declares a local array; " +
                    "arrays are not saved across recursive calls (hoist the array " +
                    "out of the recursion, or make the function non-recursive)")
        }
    }

    for (f in funcs) {
        if (f.name in recursive && takesAddress(f.body)) {
            throw CompileError(
                "function '${f.name}' is recursive and takes the address of a " +
                    "local (&x or &a[i]); a pointer to the single global slot can't " +
                    "follow the recursion save/restore stack (take the address in a " +
                    "non-recursive function, or pass the value instead)")
        }
    }

    val (mainBlock, bodies) = liftFunctions(funcs, recursive)

    val gen = CodeGen(compact = opts.compact, safeCompare = opts.safeCompare)
    val noStack = opts.stackfree || recursive.isEmpty()
    var asm = gen.generate(mainBlock, bodies, sourceName, noStack = noStack,
                           annotateCLines = annotateCLines)
    asm = passthroughDirectives(src, asm)
    return asm
}

private fun passthroughDirectives(cSrc: String, asm: String): String {
    val directives = cSrc.lines()
        .filter { it.trim().startsWith("//") && "pytoy:" in it }
        .map { it.trim().substring(2).trim() }
    if (directives.isEmpty()) return asm
    // Kotlin lines() keeps a trailing empty element for a final newline; drop it
    // so re-joining reproduces the original text exactly.
    val raw = asm.lines()
    val lines = (if (raw.isNotEmpty() && raw.last().isEmpty()) raw.dropLast(1) else raw)
        .toMutableList()
    var insertAt = 1
    for (d in directives) {
        lines.add(insertAt, "# $d")
        insertAt++
    }
    return lines.joinToString("\n") + "\n"
}
