package org.bytefred.ktoy.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the opt-in `# @c:N` C-line annotation of compileSource (ported from
 * pytoy's annotate_c_lines). The marker ties each emitted asm line back to the
 * 1-based C source line of the statement it came from. It is inert: an annotated
 * .toys assembles to the same memory image as the unannotated one.
 */
class CLineAnnotationTest {

    private val MARKER = Regex("""#.*@c:(\d+)""")

    /** For a line, the @c:N value or null (header/data/blank carry none). */
    private fun markerOf(line: String): Int? =
        MARKER.find(line)?.groupValues?.get(1)?.toInt()

    /** {C line N -> [asm lines tagged @c:N]} for every annotated line. */
    private fun markerLines(asm: String): Map<Int, List<String>> {
        val out = linkedMapOf<Int, MutableList<String>>()
        for (ln in asm.lines()) {
            val m = markerOf(ln) ?: continue
            out.getOrPut(m) { mutableListOf() }.add(ln)
        }
        return out
    }

    @Test
    fun defaultOutputHasNoMarkers() {
        val src = """
            // expect: 7
            int main() {
                int x = 3;
                int y = 4;
                return x + y;
            }
        """.trimIndent()
        val plain = compileSource(src, "sample.toyc")
        assertFalse(
            "@c:" in plain,
            "default (annotateCLines=false) must not emit any @c: marker",
        )
    }

    @Test
    fun defaultStaysByteIdenticalToPytoyGolden() {
        // The already-pinned pytoy goldens are compiled with the default (no
        // annotation); re-assert here that the new parameter left them untouched.
        for (ex in C_EXAMPLES) {
            val got = compileSource(ex.cSource, ex.name)
            assertEquals(
                ex.expectedToys, got,
                "'${ex.name}': default output diverged after adding annotateCLines",
            )
        }
    }

    @Test
    fun annotatedLinesCarryCorrectCLine() {
        // Line numbers (1-based):
        //   1: // expect: 7
        //   2: int main() {
        //   3:     int x = 3;
        //   4:     int y = 4;
        //   5:     return x + y;
        //   6: }
        val src = """
            // expect: 7
            int main() {
                int x = 3;
                int y = 4;
                return x + y;
            }
        """.trimIndent()
        val asm = compileSource(src, "sample.toyc", annotateCLines = true)
        val lines = asm.lines()

        // Blank lines never carry a marker.
        for (line in lines) {
            if (line.isBlank()) assertFalse("@c:" in line, "blank line must not be annotated")
        }
        // The three generated header comment lines carry no marker.
        assertFalse("@c:" in lines[0], "file header line must not be annotated")
        assertFalse("@c:" in lines[1], "file header line must not be annotated")
        assertFalse("@c:" in lines[2], "file header line must not be annotated")

        // Collect which C lines actually appear as markers.
        val seen = lines.mapNotNull { markerOf(it) }.toSet()
        assertTrue(3 in seen, "statement `int x = 3;` (C line 3) should be annotated")
        assertTrue(4 in seen, "statement `int y = 4;` (C line 4) should be annotated")
        assertTrue(5 in seen, "statement `return x + y;` (C line 5) should be annotated")

        // Sanity: the `store` for x = 3 sits on a line tagged @c:3.
        val storeX = lines.first { it.contains("store v_x") }
        assertEquals(3, markerOf(storeX), "store for x should be tagged C line 3")
        val storeY = lines.first { it.contains("store v_y") }
        assertEquals(4, markerOf(storeY), "store for y should be tagged C line 4")

        // No marker refers to a line outside the C source (1..6).
        for (m in seen) assertTrue(m in 1..6, "marker @c:$m out of C source range")
    }

    @Test
    fun nestedIfWhileKeepTheirOwnLine() {
        // Line numbers (1-based):
        //   1: // expect: 10
        //   2: int main() {
        //   3:     int i = 0;
        //   4:     int s = 0;
        //   5:     while (i < 5) {
        //   6:         if (i > 2) {
        //   7:             s = s + i;
        //   8:         }
        //   9:         i = i + 1;
        //  10:     }
        //  11:     return s;
        //  12: }
        val src = """
            // expect: 7
            int main() {
                int i = 0;
                int s = 0;
                while (i < 5) {
                    if (i > 2) {
                        s = s + i;
                    }
                    i = i + 1;
                }
                return s;
            }
        """.trimIndent()
        val asm = compileSource(src, "nested.toyc", annotateCLines = true)
        val seen = asm.lines().mapNotNull { markerOf(it) }.toSet()
        // The while header (5), the if header (6), the inner assign (7), the
        // loop step (9) and the return (11) each contribute at least one line.
        assertTrue(5 in seen, "while header (C line 5) should be annotated")
        assertTrue(6 in seen, "nested if header (C line 6) should be annotated")
        assertTrue(7 in seen, "inner assignment (C line 7) should be annotated")
        assertTrue(9 in seen, "loop step (C line 9) should be annotated")
        assertTrue(11 in seen, "return (C line 11) should be annotated")
    }

    @Test
    fun annotationIsInertSameMemImage() {
        val src = """
            // expect: 7
            int main() {
                int i = 0;
                int s = 0;
                while (i < 5) {
                    if (i > 2) {
                        s = s + i;
                    }
                    i = i + 1;
                }
                return s;
            }
        """.trimIndent()
        val plain = compileSource(src, "inert.toyc")
        val annotated = compileSource(src, "inert.toyc", annotateCLines = true)

        // Different text (the annotated form carries markers)...
        assertTrue("@c:" in annotated, "annotated output should contain markers")
        assertFalse("@c:" in plain, "plain output should contain no markers")

        // ...but the same assembled memory image (comments are inert).
        val a = assemble(plain)
        val b = assemble(annotated)
        assertTrue(a.errors.isEmpty(), "plain assemble failed: ${a.errors}")
        assertTrue(b.errors.isEmpty(), "annotated assemble failed: ${b.errors}")
        assertContentEquals(
            a.mem, b.mem,
            "annotated .toys must assemble to the identical memory image",
        )
    }

    /**
     * Ported from pytoy's test_toycc_recursive_save_restore_lines_point_at_call.
     * The expanded call sequence (push/assign/setmark/call_jump/capture/pop) must
     * carry the C line of the statement that made the call — not stay unannotated.
     * This guards the CallLifter fix that threads curCLine through liftCall /
     * expandCall so the whole call setup/save-restore inherits the call's line.
     */
    @Test
    fun recursiveSaveRestoreLinesPointAtCall() {
        // Line numbers (1-based):
        //   1: int f(int n) {
        //   2:     if (n == 0) return 0;
        //   3:     return f(n - 1) + 1;   <- the recursive call
        //   4: }
        //   5: int main(void) {
        //   6:     return f(3);           <- main's call
        //   7: }
        val src = (
            "int f(int n) {\n" +
                "    if (n == 0) return 0;\n" +
                "    return f(n - 1) + 1;\n" +
                "}\n" +
                "int main(void) {\n" +
                "    return f(3);\n" +
                "}\n"
            )
        val asm = compileSource(src, "t.toyc", annotateCLines = true)
        val byLine = markerLines(asm)

        // The recursive call's setmark / goto-body land on line 3.
        val tagged3 = byLine[3].orEmpty().joinToString(" ")
        assertTrue(
            "f__mark" in tagged3 || "f__body" in tagged3,
            "recursive call setup (C line 3) should carry its marker, got: $byLine",
        )
        // The self-modifying-code save/restore (push/pop of f__mark) is part of
        // the recursive call and must also sit on line 3 — the core of the fix.
        assertTrue("push f__mark" in tagged3, "push save must be tagged C line 3: $byLine")
        assertTrue("pop -> f__mark" in tagged3, "pop restore must be tagged C line 3: $byLine")
        assertTrue("store v_arg__u3" in tagged3, "arg store must be tagged C line 3: $byLine")

        // main's call (line 6): arg store, param assign, setmark, goto body and
        // the callret capture all carry @c:6 (non-recursive, so no push/pop).
        val tagged6 = byLine[6].orEmpty().joinToString(" ")
        assertTrue("f__mark" in tagged6 && "f__body" in tagged6, "main call setup -> C line 6: $byLine")
        assertTrue("store v_callret__u2" in tagged6, "callret capture -> C line 6: $byLine")
    }

    /**
     * Direct cross-check against the pytoy oracle: the exact set of asm lines
     * tagged with each C line must match what pytoy's compiler.py produces for
     * the same recursive program (oracle captured from
     * compile_source(..., annotate_c_lines=True)). Comparing the stripped,
     * marker-tagged lines per C line asserts byte-for-byte marker parity.
     */
    @Test
    fun recursiveMarkersMatchPytoyOracle() {
        val src = (
            "int f(int n) {\n" +
                "    if (n == 0) return 0;\n" +
                "    return f(n - 1) + 1;\n" +
                "}\n" +
                "int main(void) {\n" +
                "    return f(3);\n" +
                "}\n"
            )
        val asm = compileSource(src, "t.toyc", annotateCLines = true)
        val got = markerLines(asm).mapValues { (_, v) -> v.map { it.trim() } }

        // Expected markers, copied verbatim from the pytoy oracle output.
        val expected = mapOf(
            2 to listOf(
                "# if @c:2",
                "load  f__p_n  # @c:2",
                "sub   c_0  # @c:2",
                "ifzero L2_cond_true  # @c:2",
                "goto  L1_if_end  # @c:2",
                "L2_cond_true: nop  # @c:2",
                "# f__ret = ... @c:2",
                "load  c_0  # @c:2",
                "store f__ret  # @c:2",
                "goto  f__dispatch  # @c:2",
                "L1_if_end: nop  # @c:2",
            ),
            3 to listOf(
                "# int arg__u3 = ... @c:3",
                "load  f__p_n  # @c:3",
                "sub   c_1  # @c:3",
                "store v_arg__u3  # @c:3",
                "load  sp         # push f__mark @c:3",
                "sub   c_1  # @c:3",
                "store sp         # sp -= 1 @c:3",
                "store L3_push_addr       # patch store-address to sp @c:3",
                "load  f__mark  # @c:3",
                "21               # STORE opcode (raw) @c:3",
                "L3_push_addr: 0                # <- mem[sp] := f__mark @c:3",
                "# f__p_n = ... @c:3",
                "load  v_arg__u3  # @c:3",
                "store f__p_n  # @c:3",
                "load  c_1  # @c:3",
                "store f__mark  # @c:3",
                "goto  f__body  # @c:3",
                "f__cont_1: nop  # @c:3",
                "# int callret__u4 = ... @c:3",
                "load  f__ret  # @c:3",
                "store v_callret__u4  # @c:3",
                "load  sp         # pop -> f__mark @c:3",
                "store L4_pop_addr       # patch load-address to sp @c:3",
                "20               # LOAD opcode (raw) @c:3",
                "L4_pop_addr: 0                # <- acc := mem[sp] @c:3",
                "store f__mark  # @c:3",
                "load  sp  # @c:3",
                "add   c_1  # @c:3",
                "store sp         # sp += 1 @c:3",
                "# f__ret = ... @c:3",
                "load  v_callret__u4  # @c:3",
                "add   c_1  # @c:3",
                "store f__ret  # @c:3",
                "goto  f__dispatch  # @c:3",
            ),
            6 to listOf(
                "# int arg__u1 = ... @c:6",
                "load  c_3  # @c:6",
                "store v_arg__u1  # @c:6",
                "# f__p_n = ... @c:6",
                "store f__p_n  # @c:6",
                "load  c_0  # @c:6",
                "store f__mark  # @c:6",
                "goto  f__body  # @c:6",
                "f__cont_0: nop  # @c:6",
                "# int callret__u2 = ... @c:6",
                "load  f__ret  # @c:6",
                "store v_callret__u2  # @c:6",
                "# return @c:6",
                "stop  # @c:6",
            ),
        )
        assertEquals(expected, got, "ktoy @c markers must match the pytoy oracle")
    }

    // ktoy ported only the full toycc compiler (no minicc equivalent exists in
    // this module), so annotation is implemented for toycc only; there is nothing
    // else to port here.
}
