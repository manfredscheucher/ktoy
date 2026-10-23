package org.bytefred.ktoy.ui

import org.bytefred.ktoy.core.EXAMPLES
import org.bytefred.ktoy.core.assemble
import org.bytefred.ktoy.core.exportToyo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phone layout logic: the tab fallback when the chosen panel doesn't exist for
 * the loaded program, and the I/O panel being shown automatically only for
 * programs that touch the I/O cells.
 */
class PhoneLayoutTest {

    @Test
    fun tabFallsBackWhenPanelIsMissing() {
        val vm = SimViewModel()
        vm.loadFromFile("prog.toys", "load x\nstop\nx: 42\n")
        vm.phoneTab = PanelTab.C            // no C for an asm program
        assertEquals(PanelTab.ASM, vm.visibleTab())

        val res = assemble("load x\nstop\nx: 42\n")
        vm.loadFromFile("prog.toyo", exportToyo(res.listing, res.syms))
        vm.phoneTab = PanelTab.ASM          // no source for bytecode
        assertEquals(PanelTab.MEMORY, vm.visibleTab())
    }

    @Test
    fun chosenTabIsKeptWhenAvailable() {
        val vm = SimViewModel()
        vm.loadFromFile("prog.toyc", "int main(void){return 42;}\n")
        vm.phoneTab = PanelTab.C
        assertEquals(PanelTab.C, vm.visibleTab())
        vm.phoneTab = PanelTab.MEMORY
        assertEquals(PanelTab.MEMORY, vm.visibleTab())
    }

    @Test
    fun ioPanelShownOnlyForIoPrograms() {
        val vm = SimViewModel()
        vm.loadFromFile("plain.toys", "load x\nstop\nx: 42\n")
        assertFalse(vm.showIo, "a program without I/O keeps the panel hidden")

        vm.loadFromFile("io.toys", "wait: load ready\n ifzero wait\n load io0\n stop\n")
        assertTrue(vm.showIo, "a program reading ready/io0 shows the I/O panel")
    }

    @Test
    fun everyIoExampleOpensTheIoPanel() {
        // Every 04-io example must end up with the I/O panel open: either at load
        // (direct `load ready` / `store ioN`) or once it first touches an I/O cell
        // while running (self-modifying code, C write(), bytecode).
        val io = EXAMPLES.filter { it.subfolder == "04-io" }
        assertTrue(io.isNotEmpty())
        for (ex in io) {
            val vm = SimViewModel()
            vm.load(ex)
            var n = 0
            while (!vm.showIo && vm.machine?.stopped == false && n < 5000) { vm.step(); n++ }
            assertTrue(vm.showIo, "'${ex.name}' (${ex.kind}) never opened the I/O panel")
        }
    }

    @Test
    fun userToggleWinsOverAutoShow() {
        val vm = SimViewModel()
        vm.loadFromFile("io.toys", "wait: load ready\n ifzero wait\n stop\n")
        assertTrue(vm.showIo)
        vm.toggleIo()                        // user hides it
        repeat(5) { vm.step() }              // program keeps polling ready
        assertFalse(vm.showIo, "auto-show must not override the user's choice")
    }
}
