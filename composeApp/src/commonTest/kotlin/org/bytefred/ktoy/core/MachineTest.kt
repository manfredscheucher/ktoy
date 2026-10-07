package org.bytefred.ktoy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the Machine's "# old: N" bookkeeping (changedCellPrev): a STORE records
 * the overwritten cell's previous value for exactly one step, non-STORE steps
 * clear it, and step-back restores it. Ports pytoy's changed_cell_prev behaviour.
 */
class MachineTest {

    /** load v (=7); store dst (was 99); load v again; stop. */
    private val src = """
        load  v
        store dst
        load  v
        stop
# data
v:      7
dst:    99
""".trimIndent()

    private fun machine(): Machine {
        val r = assemble(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        return Machine(r.mem, r.dataAddrs)
    }

    @Test
    fun storeRecordsPreviousValueForOneStep() {
        val m = machine()
        val dst = assemble(src).syms["dst"]!!

        m.step()                           // load v
        assertNull(m.changedCellPrev, "a LOAD is not a STORE")

        m.step()                           // store dst (dst was 99)
        assertEquals(dst, m.changedCell)
        assertEquals(99, m.changedCellPrev, "should hold dst's pre-STORE value")

        m.step()                           // load v -> prev clears
        assertNull(m.changedCellPrev, "non-STORE step clears the old-value marker")
    }

    @Test
    fun stepBackRestoresPreviousValueMarker() {
        val m = machine()
        m.step(); m.step(); m.step()       // load, store, load (prev now null)
        assertNull(m.changedCellPrev)

        m.stepBack()                       // back to just-after the STORE
        assertEquals(99, m.changedCellPrev, "undo must restore the old-value marker")
    }

    @Test
    fun editCellDoesNotShowOldValue() {
        val m = machine()
        m.step(); m.step()                 // STORE sets changedCellPrev = 99
        assertEquals(99, m.changedCellPrev)

        m.editCell(0, 42)                  // a hand edit is not a STORE
        assertNull(m.changedCellPrev, "a hand edit must not surface a '# old' value")
    }
}
