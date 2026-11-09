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
        val vm = SimViewModel().apply { loadFromFile("p.toyc", c) }
        assertNull(vm.autoBuild)
        vm.toggleAutoBuild(AutoBuild.COMPILE)
        assertEquals(AutoBuild.COMPILE, vm.autoBuild)
        vm.toggleAutoBuild(AutoBuild.ASSEMBLE)   // drops the C: asks first
        vm.confirmPending()
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
    fun switchingOnBuildsOnceRightAway() {
        val vm = SimViewModel().apply { loadFromFile("p.toyo", "20\n3\n0\n42\n") }
        vm.toggleAutoBuild(AutoBuild.DISASSEMBLE)
        assertEquals(AutoBuild.DISASSEMBLE, vm.autoBuild)
        assertTrue(vm.asmText != null, "disassembled")
    }

    @Test
    fun autoDisassembleFollowsACellEditButNotAnIoCell() {
        val vm = SimViewModel().apply { loadFromFile("p.toyo", "20\n3\n0\n42\n") }
        vm.toggleAutoBuild(AutoBuild.DISASSEMBLE)
        val before = vm.asmText
        vm.editCell(241, 65)
        assertEquals(before, vm.asmText)
        vm.editCell(3, 9)
        // the I/O value is lost, but switching on was the OK: no dialog
        assertNull(vm.pendingAction)
        assertTrue("9" in vm.asmText!!, vm.asmText)
    }

    @Test
    fun switchingOnAsksFirstIfTheBuildLosesSomething() {
        val vm = SimViewModel().apply { loadFromFile("p.toys", asm) }
        vm.toggleAutoBuild(AutoBuild.DISASSEMBLE)
        assertEquals(BuildAction.Disassemble, vm.pendingAction)
        assertNull(vm.autoBuild)
        vm.cancelPending()
        assertNull(vm.autoBuild)
        assertEquals(asm, vm.asmText)

        vm.toggleAutoBuild(AutoBuild.DISASSEMBLE)
        vm.confirmPending()
        assertEquals(AutoBuild.DISASSEMBLE, vm.autoBuild)
        assertTrue("x:" !in vm.asmText!!, "disassembled right away")
        vm.machine!!.step()
        vm.editCell(3, 9)
        assertNull(vm.pendingAction, "later builds don't ask")
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
