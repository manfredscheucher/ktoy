package org.bytefred.ktoy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** STOP counts as a step, stays on its address, and can be undone. */
class StopStepTest {

    private fun machine(src: String): Machine {
        val r = assemble(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        return Machine(r.mem, r.dataAddrs)
    }

    @Test
    fun aProgramThatIsJustStopTakesOneStep() {
        val m = machine("stop\n")
        assertFalse(m.step())
        assertTrue(m.stopped)
        assertEquals(1, m.stepCount)
        assertEquals(0, m.pc, "the PC stays on the STOP")
        assertFalse(m.step(), "stepping a stopped machine does nothing")
        assertEquals(1, m.stepCount)
    }

    @Test
    fun stopIsCountedAfterTheOtherInstructions() {
        val m = machine("load x\nadd x\nstop\nx: 1\n")
        while (m.step()) { }
        assertEquals(3, m.stepCount)
        assertEquals(4, m.pc)
    }

    @Test
    fun undoTakesTheStopBack() {
        val m = machine("load x\nstop\nx: 5\n")
        while (m.step()) { }
        assertEquals(2, m.stepCount)
        assertTrue(m.stepBack())
        assertFalse(m.stopped)
        assertEquals(1, m.stepCount)
        assertEquals(2, m.pc)
        assertEquals(5, m.acc)
    }

    @Test
    fun aTwoByteInvalidOpcodeRunsAsATwoByteNop() {
        val mem = IntArray(256).also { it[0] = 26; it[1] = 99; it[2] = 20; it[3] = 5; it[4] = 0; it[5] = 7 }
        val m = Machine(mem, emptySet())
        m.step()
        assertEquals(2, m.pc, "skipped the operand byte")
        while (m.step()) { }
        assertEquals(7, m.acc)
        assertEquals(3, m.stepCount)
    }
}
