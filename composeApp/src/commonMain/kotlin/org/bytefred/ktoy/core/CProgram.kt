package org.bytefred.ktoy.core

/**
 * A loaded C program and its compiled result: the C source, the compiler it was
 * built with, the assembled image + listing, and the C↔asm line maps. This is
 * the pure, UI-free heart of the three-column view's Part-3 behaviour (compiler
 * switch + C edit), so the recompile-with-rollback semantics are unit-tested
 * without a ViewModel. Ported from pytoy's _switch_compiler / EditCDialog save.
 *
 * Every mutation is "all or nothing": a failed recompile (compile error or a
 * result that overflows the 240-byte region) returns an error string and leaves
 * this object UNCHANGED — the UI then keeps its dialog open and the program is
 * never half-loaded.
 */
class CProgram private constructor(
    val cText: String,
    val compiler: String,
    val asm: String,
    val result: AssemblyResult,
    val cToAsm: Map<Int, List<Int>>,
    val asmToC: Map<Int, Int>,
) {
    /** The compiler a switch would move TO (the one NOT currently active). */
    val otherCompiler: String get() = if (compiler == "toycc") "minicc" else "toycc"

    /**
     * Recompile the SAME C source with [target]. On success returns a fresh
     * CProgram; on a compile error (e.g. minicc + recursion) or an assemble
     * error (e.g. a minicc result overflowing 240 bytes) returns the error
     * message as a [Failure] — nothing about `this` changes (pytoy's rollback).
     */
    fun withCompiler(target: String): BuildOutcome = build(cText, target)

    /**
     * Recompile [newC] with [compiler] when non-null, else detect a compiler for
     * [newC] (preferring toycc). On success returns the fresh CProgram; on any
     * error returns a [Failure]. Mirrors EditCDialog's Save.
     */
    fun withEditedC(newC: String, compiler: String? = null): BuildOutcome {
        val chosen = compiler ?: run {
            val accepted = detectCompilers(newC)
            if ("toycc" in accepted) "toycc" else accepted.firstOrNull() ?: "toycc"
        }
        return build(newC, chosen)
    }

    companion object {
        /**
         * Compile [cText] with [compiler] (annotating C lines) and assemble it.
         * Returns a ready CProgram, or a [Failure] carrying the error message if
         * the compile throws or the result doesn't assemble cleanly.
         */
        fun build(cText: String, compiler: String): BuildOutcome {
            val asm = try {
                compileWith(compiler, cText, "program.toyc", annotateCLines = true)
            } catch (e: Exception) {
                return BuildOutcome.Failure(e.message ?: "compile failed")
            }
            val result = assemble(asm)
            if (result.errors.isNotEmpty()) {
                return BuildOutcome.Failure(result.errors.joinToString("\n"))
            }
            val (cToAsm, asmToC) = parseCLineMap(asm)
            return BuildOutcome.Success(
                CProgram(cText, compiler, asm, result, cToAsm, asmToC)
            )
        }
    }
}

/** The result of building a [CProgram]: either the program or an error message. */
sealed class BuildOutcome {
    data class Success(val program: CProgram) : BuildOutcome()
    data class Failure(val error: String) : BuildOutcome()
}
