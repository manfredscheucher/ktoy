package org.bytefred.ktoy.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * loadFromFile must infer the program kind from the file name's EXTENSION, so a
 * .toyo is parsed as bytecode and a .toyc is compiled — not assembled as source.
 * This guards the bug where the picker stripped the extension before the VM saw
 * it, making every disk load fall back to ASM.
 */
class LoadFromFileTest {

    @Test
    fun toysLoadsAsAssembly() {
        val vm = SimViewModel()
        vm.loadFromFile("prog.toys", "load x\nstop\nx: 42\n")
        assertTrue(vm.hasSource, "a .toys should load with source")
        assertFalse(vm.hasCSource)
        assertEquals(null, vm.errorMessage)
    }

    @Test
    fun toyoLoadsAsBytecode() {
        val vm = SimViewModel()
        // export a real .toyo, then load it by its .toyo name
        val res = org.bytefred.ktoy.core.assemble("load x\nstop\nx: 42\n")
        val toyo = org.bytefred.ktoy.core.exportToyo(res.listing, res.syms)
        vm.loadFromFile("prog.toyo", toyo)
        // bytecode has no source view (hasSource=false), unlike a .toys assembled
        assertFalse(vm.hasSource, "a .toyo should load as bytecode, no source")
        assertEquals(null, vm.errorMessage)
    }

    @Test
    fun toycLoadsAsCProgram() {
        val vm = SimViewModel()
        vm.loadFromFile("prog.toyc", "int main(void){return 42;}\n")
        assertTrue(vm.hasCSource, "a .toyc should load as a C program")
        assertEquals(null, vm.errorMessage)
    }

    @Test
    fun reloadReRunsTheLastFile() {
        val vm = SimViewModel()
        assertFalse(vm.canReload, "nothing loaded from disk yet")
        vm.loadFromFile("prog.toys", "load x\nstop\nx: 42\n")
        assertTrue(vm.canReload)
        vm.reload()   // must not throw and must keep the program
        assertTrue(vm.hasSource)
    }

    @Test
    fun saveFormatsFollowTheProgram() {
        val vm = SimViewModel()
        vm.loadFromFile("prog.toys", "load x\nstop\nx: 42\n")
        val asmExts = vm.saveFormats().map { it.second }
        assertTrue(".toyo" in asmExts && ".toys" in asmExts && ".toyc" !in asmExts)

        vm.loadFromFile("prog.toyc", "int main(void){return 42;}\n")
        val cExts = vm.saveFormats().map { it.second }.toSet()
        assertEquals(setOf(".toyo", ".toys", ".toyc"), cExts)
    }
}
