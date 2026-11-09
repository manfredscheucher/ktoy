package org.bytefred.ktoy.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import org.bytefred.ktoy.core.detectCompilers

/** What Help -> C compilers says about mini C and full C, checked against the compilers. */
class HelpClaimsTest {
    private fun accepted(body: String, before: String = "") = detectCompilers("$before\nint main(void) {\n$body\n}\n").toSet()

    private val both = setOf("minicc", "toycc")
    private val fullOnly = setOf("toycc")

    @Test
    fun miniCHasArraysIfWhileAndFunctions() {
        assertEquals(both, accepted("int a[3] = {1, 2, 3}; int i = 0; int s = 0; while (i < 3) { if (a[i] > 1) { s = s + a[i]; } else { s = s - 1; } i = i + 1; } return f(s) * 2;",
            "int f(int x) { return x + 1; }"))
    }

    @Test
    fun fullCAddsRecursion() =
        assertEquals(fullOnly, accepted("return f(3);", "int f(int n) { if (n == 0) { return 0; } return f(n - 1) + 1; }"))

    @Test
    fun fullCAddsPointers() = assertEquals(fullOnly, accepted("int x = 5; int *p = &x; return *p;"))

    @Test
    fun fullCAddsReadAndWrite() = assertEquals(fullOnly, accepted("int d[4]; int k = read(d); write(d, k); return k;"))

    @Test
    fun fullCAddsCompoundAssignment() = assertEquals(fullOnly, accepted("int x = 5; x += 2; return x;"))

    @Test
    fun fullCAddsShiftsByAVariable() = assertEquals(fullOnly, accepted("int x = 5; int n = 2; return x << n;"))

    @Test
    fun aBlockNameStaysVisibleAfterTheBlockInBoth() {
        assertEquals(both, accepted("if (1) { int a = 1; } return a;"))
        assertEquals(emptySet(), accepted("if (1) { int a = 1; } int a = 2; return a;"))
    }

    @Test
    fun accHoldsWhatMainReturnsWithBothCompilers() {
        for (compiler in listOf("toycc", "minicc")) {
            val vm = SimViewModel().apply { loadFromFile("p.toyc", "int main(void) {\n    int x = 100;\n    return x + 23;\n}\n") }
            if (vm.currentCompiler != compiler) vm.request(SimViewModel.BuildAction.Compile(compiler))
            assertEquals(compiler, vm.currentCompiler)
            val m = vm.machine!!
            while (!m.stopped) m.step()
            assertEquals(123, m.acc, compiler)
        }
    }
}
