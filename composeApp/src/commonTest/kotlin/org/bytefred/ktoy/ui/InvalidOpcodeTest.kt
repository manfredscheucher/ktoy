package org.bytefred.ktoy.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvalidOpcodeTest {

    @Test
    fun anInvalidOpcodeDoesNothingLikeToycpu() {
        // load x; 3 (not an opcode); add x; stop; x: 21
        val vm = SimViewModel().apply { loadFromFile("bad.toys", "load x\n3\nadd x\nstop\nx: 21\n") }
        repeat(10) { vm.step() }
        val m = vm.machine!!
        assertTrue(m.stopped)
        assertEquals(42, m.acc)
        assertEquals(4, m.stepCount)
    }
}
