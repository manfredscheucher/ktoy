package org.bytefred.ktoy.ui

import org.bytefred.ktoy.core.EXAMPLE_SOURCES
import org.bytefred.ktoy.core.EXAMPLES
import org.bytefred.ktoy.core.ExampleKind
import org.bytefred.ktoy.core.decode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Every example states in its header whether it uses I/O, and the I/O panel follows it. */
class ExampleIoTest {

    private val header = Regex("""^(?:#|//)(?:\s+#)?\s*io:\s*(yes|no)\s*$""", RegexOption.MULTILINE)

    /** True if the program, run up to [limit] steps, reads or writes a cell in 240..255. */
    private fun touchesIoWhileRunning(vm: SimViewModel, limit: Int = 5000): Boolean {
        val m = vm.machine!!
        var n = 0
        while (!m.stopped && n < limit) {
            val (instr, arg, _) = decode(m.mem, m.pc)
            if (instr in 17..23 && arg != null && arg >= 240) return true
            m.step(); n++
        }
        return false
    }

    @Test
    fun everyExampleHasAnIoHeaderAndItIsTrue() {
        for (ex in EXAMPLES) {
            val h = header.find(ex.source)
            assertNotNull(h, "${ex.name}: no io header")
            assertEquals(h.groupValues[1] == "yes", ex.io, ex.name)
            val vm = SimViewModel()
            vm.load(ex)
            val uses = vm.usesIo() || touchesIoWhileRunning(vm)
            assertEquals(uses, ex.io, "${ex.subfolder}/${ex.name}: io header vs what the program does")
        }
    }

    @Test
    fun loadingAnExampleShowsTheIoPanelExactlyWhenItsHeaderSaysYes() {
        for (ex in EXAMPLES) {
            val vm = SimViewModel()
            vm.load(ex)
            assertEquals(ex.io, vm.showIo, ex.name)
        }
    }

    @Test
    fun anIoExampleTheStaticCheckMissesStillShowsThePanelAtLoad() {
        // countdown writes io0..io9 through a patched store: usesIo() can't see it
        val ex = EXAMPLES.first { it.name == "countdown" && it.kind == ExampleKind.ASM }
        val vm = SimViewModel()
        vm.load(ex)
        assertFalse(vm.usesIo())
        assertTrue(vm.showIo)
    }

    @Test
    fun anExampleNeverOpensThePanelByItselfWhileRunning() {
        val ex = EXAMPLES.first { it.name == "countdown" && it.kind == ExampleKind.ASM }
        val vm = SimViewModel()
        vm.load(ex)
        vm.toggleIo()   // the user hides it
        repeat(200) { vm.step() }
        assertFalse(vm.showIo)
    }

    @Test
    fun aFileWithoutTheHeaderStillOpensThePanelWhenItTouchesIo() {
        // the same program as the countdown example, loaded as a user file
        val src = EXAMPLES.first { it.name == "countdown" && it.kind == ExampleKind.ASM }.source
        val vm = SimViewModel()
        vm.loadFromFile("mine.toys", src)
        assertFalse(vm.showIo, "usesIo() misses it at load")
        repeat(200) { vm.step() }
        assertTrue(vm.showIo, "opens once the program writes an I/O cell")
    }

    @Test
    fun theStartExampleComesFromTheUncompiledSources() {
        // EXAMPLE_SOURCES holds no compiler lists, so reading it compiles no C.
        assertTrue(EXAMPLE_SOURCES.filter { it.kind == ExampleKind.C }.all { it.compilers.isEmpty() })
        val vm = SimViewModel()
        vm.loadStartExample()
        assertEquals(START_EXAMPLE, vm.title)
    }

    @Test
    fun aCompilerIsListedOnlyIfItsOutputFits() {
        val both = listOf("minicc", "toycc")
        val toycc = listOf("toycc")
        val expected = mapOf(
            "02-extended/bit_operators.toyc" to both,
            "02-extended/if_then_else.toyc" to both,
            "02-extended/msb_shift_vs_mask.toyc" to both,
            "02-extended/multiply_doubling.toyc" to both,
            "02-extended/multiply_operator.toyc" to both,
            "02-extended/multiply_repeated.toyc" to both,
            "02-extended/popcount.toyc" to both,
            "02-extended/times7_operator.toyc" to both,
            "03-programs/bigint_add.toyc" to toycc,
            "03-programs/count_iter.toyc" to both,
            "03-programs/count_rec.toyc" to toycc,
            "03-programs/fibonacci_array.toyc" to both,
            "03-programs/fibonacci_iter.toyc" to both,
            "03-programs/fibonacci_rec.toyc" to toycc,
            "03-programs/gcd_iter.toyc" to both,
            "03-programs/gcd_rec.toyc" to toycc,
            "03-programs/max3.toyc" to both,
            "03-programs/max_array.toyc" to both,
            "03-programs/sort3.toyc" to both,
            "03-programs/sort_array_fn.toyc" to toycc,
            "03-programs/sort_array_inline.toyc" to toycc,
            "03-programs/sum3.toyc" to both,
            "03-programs/sum_array.toyc" to both,
            "03-programs/sum_countdown.toyc" to toycc,
            "03-programs/sum_of_squares.toyc" to both,
            "04-io/greet_name.toyc" to toycc,
            "04-io/hello_world.toyc" to toycc,
            "04-io/pin_check.toyc" to toycc,
            "04-io/pin_check_hardened.toyc" to toycc,
        )
        val actual = EXAMPLES.filter { it.kind == ExampleKind.C }
            .associate { "${it.subfolder}/${it.name}" to it.compilers }
        assertEquals(expected, actual)
    }
}
