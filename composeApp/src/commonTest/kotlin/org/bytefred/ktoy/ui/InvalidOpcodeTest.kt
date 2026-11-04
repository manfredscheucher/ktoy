package org.bytefred.ktoy.ui

import org.bytefred.ktoy.core.EXAMPLES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Execution -> Invalid opcode: NOP (default, like toycpu), Stop, Always ask. */
class InvalidOpcodeTest {

    // load x; 3 (not an opcode); add x; stop; x: 21
    private fun vm() = SimViewModel().apply { loadFromFile("bad.toys", "load x\n3\nadd x\nstop\nx: 21\n") }

    @Test
    fun nopIsTheDefaultAndRunsLikeToycpu() {
        val vm = vm()
        assertEquals(InvalidOpcodeMode.NOP, vm.invalidOpcodeMode)
        repeat(10) { vm.step() }
        val m = vm.machine!!
        assertTrue(m.stopped)
        assertNull(m.error)
        assertEquals(42, m.acc)
        assertEquals(4, m.stepCount)
    }

    @Test
    fun stopHaltsWithAnErrorInThatStep() {
        val vm = vm()
        vm.invalidOpcodeMode = InvalidOpcodeMode.STOP
        vm.step()
        vm.step()
        val m = vm.machine!!
        assertTrue(m.stopped)
        assertEquals(2, m.stepCount, "the invalid opcode is step 2")
        assertEquals(2, m.pc, "the PC stays on the bad byte")
        assertEquals("invalid opcode 3 at address 2", m.error)
        assertEquals("Error in step 2: invalid opcode 3 at address 2.", vm.runError)

        vm.stepBack()
        assertFalse(m.stopped)
        assertNull(m.error)
        assertEquals(1, m.stepCount)
    }

    @Test
    fun askPausesAndTheAnswerDecides() {
        val vm = vm()
        vm.invalidOpcodeMode = InvalidOpcodeMode.ASK
        vm.step()
        vm.step()
        assertEquals(2, vm.invalidOpcodeQuestion, "asked about address 2")
        assertEquals(1, vm.machine!!.stepCount, "nothing ran yet")

        vm.answerInvalidOpcode(asNop = true, remember = false)
        assertNull(vm.invalidOpcodeQuestion)
        assertEquals(3, vm.machine!!.pc, "ran as a NOP")
        assertEquals(InvalidOpcodeMode.ASK, vm.invalidOpcodeMode, "not remembered")
    }

    @Test
    fun rememberMakesTheAnswerTheSetting() {
        val vm = vm()
        vm.invalidOpcodeMode = InvalidOpcodeMode.ASK
        vm.step()
        vm.step()
        vm.answerInvalidOpcode(asNop = false, remember = true)
        assertEquals(InvalidOpcodeMode.STOP, vm.invalidOpcodeMode)
        assertNotNull(vm.machine!!.error)
    }

    @Test
    fun theBytecodeExampleShowsIt() {
        val ex = EXAMPLES.single { it.name.startsWith("invalid_opcode") }
        val vm = SimViewModel().apply { load(ex) }
        repeat(10) { vm.step() }
        assertEquals(42, vm.machine!!.acc)
    }
}
