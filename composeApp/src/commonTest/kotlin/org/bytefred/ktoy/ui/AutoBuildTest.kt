package org.bytefred.ktoy.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.bytefred.ktoy.ui.SimViewModel.AutoBuild
import org.bytefred.ktoy.ui.SimViewModel.BuildAction

/** The Auto compile / assemble / disassemble on edit toggles. */
class AutoBuildTest {
    private val asm = "        load  x\n        stop\nx:      42\n"
    private val c = "int main(void) {\n    return 42;\n}\n"

    @Test
    fun offAtStartAndAtMostOneOn() {
        val vm = SimViewModel()
        assertNull(vm.autoBuild)
        vm.toggleAutoBuild(AutoBuild.COMPILE)
        assertEquals(AutoBuild.COMPILE, vm.autoBuild)
        vm.toggleAutoBuild(AutoBuild.ASSEMBLE)
        assertEquals(AutoBuild.ASSEMBLE, vm.autoBuild)
        vm.toggleAutoBuild(AutoBuild.ASSEMBLE)
        assertNull(vm.autoBuild)
    }

    @Test
    fun withoutAToggleASaveOnlyStoresTheDraft() {
        val vm = SimViewModel().apply { loadFromFile("p.toyc", c) }
        vm.saveCDraft(c.replace("42", "7"))
        assertNotEquals(null, vm.cDraft)
        assertTrue("42" in vm.cText!!)
    }

    @Test
    fun autoCompileBuildsTheSavedC() {
        val vm = SimViewModel().apply { loadFromFile("p.toyc", c) }
        vm.toggleAutoBuild(AutoBuild.COMPILE)
        vm.saveCDraft(c.replace("42", "7"))
        assertNull(vm.cDraft)
        assertTrue("return 7" in vm.cText!!)
    }

    @Test
    fun autoAssembleBuildsTheSavedAssembly() {
        val vm = SimViewModel().apply { loadFromFile("p.toys", asm) }
        vm.toggleAutoBuild(AutoBuild.ASSEMBLE)
        vm.saveAsmDraft(asm.replace("42", "7"))
        assertNull(vm.asmDraft)
        assertEquals(7, vm.machine!!.mem[3])
    }

    @Test
    fun autoAssembleDoesNotCompile() {
        val vm = SimViewModel().apply { loadFromFile("p.toyc", c) }
        vm.toggleAutoBuild(AutoBuild.ASSEMBLE)
        vm.saveCDraft(c.replace("42", "7"))
        assertNotEquals(null, vm.cDraft)
    }

    @Test
    fun autoDisassembleFollowsABytecodeEdit() {
        val vm = SimViewModel().apply { loadFromFile("p.toyo", "20\n3\n0\n42\n") }
        vm.toggleAutoBuild(AutoBuild.DISASSEMBLE)
        vm.saveBytecode("20\n3\n0\n9\n")
        assertTrue(vm.asmText != null, "disassembled")
        assertEquals(9, vm.machine!!.mem[3])
    }

    @Test
    fun autoDisassembleFollowsACellEditButNotAnIoCell() {
        val vm = SimViewModel().apply { loadFromFile("p.toyo", "20\n3\n0\n42\n") }
        vm.toggleAutoBuild(AutoBuild.DISASSEMBLE)
        vm.editCell(241, 65)
        assertNull(vm.asmText)
        assertNull(vm.pendingAction)
        vm.editCell(3, 9)
        // the I/O value would be lost, so it asks first
        assertEquals(BuildAction.Disassemble, vm.pendingAction)
        vm.confirmPending()
        assertTrue(vm.asmText != null)
    }

    @Test
    fun anAutoBuildThatLosesSomethingAsksFirst() {
        val vm = SimViewModel().apply { loadFromFile("p.toys", asm) }
        vm.toggleAutoBuild(AutoBuild.DISASSEMBLE)
        vm.editCell(3, 9)
        assertEquals(BuildAction.Disassemble, vm.pendingAction)
    }

    @Test
    fun saveAndBuildDoesNotBuildTwice() {
        val vm = SimViewModel().apply { loadFromFile("p.toys", asm) }
        vm.toggleAutoBuild(AutoBuild.ASSEMBLE)
        vm.saveAsmDraft(asm.replace("42", "7"))
        vm.machine!!.step()
        vm.buildAfterSave(BuildAction.Assemble)
        assertEquals(1, vm.machine!!.stepCount, "no second assemble")
        vm.toggleAutoBuild(AutoBuild.ASSEMBLE)
        vm.buildAfterSave(BuildAction.Assemble)
        assertTrue(vm.pendingAction != null || vm.machine!!.stepCount == 0, "without the toggle it builds")
    }
}
