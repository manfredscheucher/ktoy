package org.bytefred.ktoy.ui

import org.bytefred.ktoy.core.parseToyo
import org.bytefred.ktoy.ui.SimViewModel.BuildAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Save changes?" before Load / Examples / Reload / close, and what counts as an edit. */
class UnsavedEditsTest {

    private fun asmVm() = SimViewModel().apply { loadFromFile("prog.toys", "load x\nstop\nx: 42\n") }
    private fun cVm() = SimViewModel().apply { loadFromFile("prog.toyc", "int main(void){return 42;}\n") }

    @Test
    fun aRunIsNoEdit() {
        val vm = asmVm()
        vm.step(); vm.step()
        assertFalse(vm.unsavedEdits)
        var ran = false
        vm.leave { ran = true }
        assertTrue(ran, "nothing to save: no question")
        assertNull(vm.pendingLeave)
    }

    @Test
    fun textAndProgramByteEditsCount() {
        assertTrue(asmVm().apply { saveAsmDraft("stop\n") }.unsavedEdits)
        assertTrue(cVm().apply { saveCDraft("int main(void){return 1;}\n") }.unsavedEdits)
        assertTrue(asmVm().apply { editCell(3, 9) }.unsavedEdits)
        // unchanged text is no draft, so no edit
        assertFalse(asmVm().apply { saveAsmDraft(asmText!!) }.unsavedEdits)
    }

    @Test
    fun ioInputIsNoEdit() {
        val vm = asmVm()
        vm.editCell(240, 65)
        assertFalse(vm.unsavedEdits)
    }

    @Test
    fun buildingAnEditKeepsItUnsaved() {
        val vm = asmVm()
        vm.saveAsmDraft("load x\nstop\nx: 43\n")
        vm.request(BuildAction.Assemble)
        assertNull(vm.asmDraft)
        assertTrue(vm.unsavedEdits, "assembled, but not saved to a file")

        val c = cVm()
        c.saveCDraft("int main(void){return 7;}\n")
        c.request(BuildAction.Compile())
        assertTrue(c.unsavedEdits)
    }

    @Test
    fun leaveAsksAndCancelKeepsEverything() {
        val vm = asmVm()
        vm.saveAsmDraft("stop\n")
        var ran = false
        vm.leave { ran = true }
        assertFalse(ran)
        assertNotNull(vm.pendingLeave)
        vm.cancelLeave()
        assertFalse(ran)
        assertNull(vm.pendingLeave)
        assertEquals("stop\n", vm.asmDraft)
    }

    @Test
    fun discardGoesOn() {
        val vm = asmVm()
        vm.editCell(3, 9)
        vm.leave { vm.reload() }
        vm.discardAndLeave()
        assertNull(vm.pendingLeave)
        assertEquals(42, vm.machine!!.mem[3], "reloaded")
        assertFalse(vm.unsavedEdits, "a fresh load has no edits")
    }

    @Test
    fun saveThenGoOnButACancelledSaveStops() {
        val vm = asmVm()
        vm.saveAsmDraft("stop\n")
        var ran = false
        vm.leave { ran = true }
        vm.fileSaved(null)          // the file dialog was cancelled
        assertFalse(ran)
        assertNull(vm.pendingLeave)
        assertTrue(vm.unsavedEdits)

        vm.leave { ran = true }
        vm.beginSave(".toys")
        vm.fileSaved("prog.toys")
        assertTrue(ran)
        assertFalse(vm.unsavedEdits)
    }

    @Test
    fun aSaveFromTheFileMenuClearsTheEdits() {
        val vm = asmVm()
        vm.editCell(3, 9)
        vm.beginSave(".toyo")
        vm.fileSaved("prog.toyo")
        assertFalse(vm.unsavedEdits)
        assertEquals("Saved prog.toyo", vm.statusMessage)
    }

    @Test
    fun formatsAreHighestLevelFirst() {
        assertEquals(listOf(".toyc", ".toys", ".toyo"), cVm().saveFormats().map { it.second })
        assertEquals(listOf(".toys", ".toyo"), asmVm().saveFormats().map { it.second })
        val toyo = SimViewModel().apply { loadFromFile("p.toyo", "     0   00000000\n") }
        assertEquals(listOf(".toyo"), toyo.saveFormats().map { it.second })
    }

    @Test
    fun settingACellToItsValueIsNoEdit() {
        val vm = asmVm()
        vm.editCell(3, 42)
        assertFalse(vm.unsavedEdits)
    }

    @Test
    fun aBrokenAsmFileLeavesProgramAndEditsAlone() {
        val vm = asmVm()
        vm.editCell(3, 9)
        vm.loadFromFile("broken.toys", "load nosuchlabel\nstop\n")
        assertNotNull(vm.errorMessage)
        assertEquals("prog.toys", vm.title)
        assertEquals(9, vm.machine!!.mem[3])
        assertTrue(vm.unsavedEdits)
    }

    // ── an edit counts as saved only in the format it was made in ──

    private fun SimViewModel.saveAs(ext: String, name: String = "out$ext", path: String? = null) {
        assertNotNull(beginSave(ext))
        fileSaved(name, path)
    }

    @Test
    fun aCompiledCEditStaysUnsavedUntilCIsSaved() {
        val vm = cVm()
        vm.saveCDraft("int main(void){return 7;}\n")
        vm.request(BuildAction.Compile())
        assertEquals(emptyList(), vm.missingFrom(".toys"), "the asm holds the compiled edit")
        vm.saveAs(".toys")
        assertEquals(listOf(".toyc"), vm.unsavedFormats())
        vm.saveAs(".toyc")
        assertFalse(vm.unsavedEdits)
    }

    @Test
    fun missingFromNamesWhatAFormatLacks() {
        val c = cVm()
        c.saveCDraft("int main(void){return 7;}\n")
        assertEquals(emptyList(), c.missingFrom(".toyc"))
        assertTrue(c.missingFrom(".toys").single().contains("not compiled"))
        assertTrue(c.missingFrom(".toyo").single().contains("not compiled"))

        val asm = asmVm()
        asm.saveAsmDraft("stop\n")
        assertEquals(emptyList(), asm.missingFrom(".toys"))
        assertTrue(asm.missingFrom(".toyo").single().contains("not assembled"))

        val mem = asmVm()
        mem.editCell(3, 9)
        assertEquals(emptyList(), mem.missingFrom(".toyo"))
        assertTrue(mem.missingFrom(".toys").single().contains("only in the bytecode"))
    }

    @Test
    fun savingALackingFormatInTheQuestionAsksAgainNamingTheRest() {
        val vm = cVm()
        vm.saveCDraft("int main(void){return 7;}\n")
        var ran = false
        vm.leave { ran = true }
        vm.saveAs(".toys")
        assertFalse(ran, "the C edit is not saved yet")
        assertNotNull(vm.pendingLeave)
        assertEquals("C source", vm.unsavedDescription())
        vm.saveAs(".toyc")
        assertTrue(ran)
        assertNull(vm.pendingLeave)
    }

    @Test
    fun cAndMemoryEditsNeedTwoSavesAndBothClear() {
        val vm = cVm()
        vm.saveCDraft("int main(void){return 7;}\n")
        vm.editCell(3, 9)
        assertEquals(listOf(".toyc", ".toyo"), vm.unsavedFormats())
        vm.saveAs(".toyc")
        assertEquals(listOf(".toyo"), vm.unsavedFormats())
        vm.saveAs(".toyo")
        assertFalse(vm.unsavedEdits)
    }

    @Test
    fun revertingAnEditLeavesNothingUnsaved() {
        val vm = asmVm()
        val original = vm.asmText!!
        vm.saveAsmDraft("stop\n")
        vm.saveAsmDraft(original)
        assertFalse(vm.unsavedEdits)

        val mem = asmVm()
        mem.editCell(3, 9)
        mem.editCell(3, 42)
        assertFalse(mem.unsavedEdits, "the cell is back at its value")
    }

    @Test
    fun undoAndResetTakeAMemoryEditBack() {
        val undo = asmVm()
        undo.editCell(3, 9)
        undo.stepBack()
        assertFalse(undo.unsavedEdits)

        val reset = asmVm()
        reset.editCell(3, 9)
        reset.reset()
        assertFalse(reset.unsavedEdits)
    }

    @Test
    fun aBuildThatDropsADraftLeavesNoEditBehind() {
        val c = cVm()
        c.saveCDraft("int main(void){return 7;}\n")
        c.request(BuildAction.Assemble)
        c.confirmPending()
        assertFalse(c.unsavedEdits, "the dropped draft was confirmed and never built")
    }

    @Test
    fun aCompiledCEditLivesOnInTheAsmAfterAssemble() {
        val c = cVm()
        c.saveCDraft("int main(void){return 7;}\n")
        c.request(BuildAction.Compile())
        c.request(BuildAction.Assemble)
        c.confirmPending()
        assertEquals(listOf(".toys"), c.unsavedFormats())
        c.saveAs(".toys")
        assertFalse(c.unsavedEdits, "and it can be saved")
    }

    @Test
    fun disassemblingAMemoryEditMovesItToTheAsm() {
        val vm = asmVm()
        vm.editCell(3, 9)
        vm.request(BuildAction.Disassemble)
        vm.confirmPending()
        assertEquals(listOf(".toys"), vm.unsavedFormats())
        vm.saveAs(".toys")
        assertFalse(vm.unsavedEdits)
    }

    @Test
    fun disassemblingWithoutEditsIsNoEdit() {
        val vm = asmVm()
        vm.request(BuildAction.Disassemble)
        vm.confirmPending()
        assertFalse(vm.unsavedEdits)
    }

    // ── bytecode export: as built plus hand edits ──

    @Test
    fun bytecodeHoldsHandEditsButNothingARunStored() {
        val vm = SimViewModel().apply { loadFromFile("p.toys", "load x\nstore y\nstop\nx: 42\ny: 0\n") }
        val y = vm.syms.getValue("y")
        val x = vm.syms.getValue("x")
        vm.step(); vm.step()
        assertEquals(42, vm.machine!!.mem[y], "the run stored it")
        vm.editCell(x, 9)
        val back = parseToyo(vm.exportText(".toyo")!!)
        assertEquals(0, back[y])
        assertEquals(9, back[x])
    }

    @Test
    fun bytecodeHoldsAnEditedCellOutsideTheListing() {
        val vm = asmVm()
        vm.editCell(200, 77)
        assertEquals(77, parseToyo(vm.exportText(".toyo")!!)[200])
    }

    @Test
    fun undoAfterABytecodeSaveIsUnsavedAndResetIsNot() {
        val vm = asmVm()
        vm.editCell(3, 9)
        vm.beginSave(".toyo")
        vm.fileSaved("prog.toyo")
        assertFalse(vm.unsavedEdits)
        vm.stepBack()
        assertEquals(listOf(".toyo"), vm.unsavedFormats(), "the file still holds the edit")
        vm.editCell(3, 9)
        assertFalse(vm.unsavedEdits, "the same edit again matches the file")
        vm.reset()
        assertFalse(vm.unsavedEdits, "Reset never asks")
    }

    @Test
    fun aGarbageBytecodeFileIsAnError() {
        val vm = SimViewModel()
        vm.loadFromFile("p.toyo", "20\n6\n")
        vm.loadFromFile("q.toyo", "20 6\n")
        assertNotNull(vm.errorMessage)
        assertEquals("p.toyo", vm.title)
    }

    @Test
    fun bytecodeFileIsOnlyTheValuesUpToTheLastNonZeroCell() {
        val vm = SimViewModel()
        vm.loadFromFile("p.toyo", "20\n6\n0\n0\n")
        assertEquals("20\n6\n", vm.exportText(".toyo"))
        vm.editCell(4, 7)
        assertEquals("20\n6\n0\n0\n7\n", vm.exportText(".toyo"))
    }

    @Test
    fun aBadBytecodeFileIsAnErrorAndKeepsTheProgram() {
        val vm = SimViewModel()
        vm.loadFromFile("p.toyo", "20\n6\n")
        vm.loadFromFile("q.toyo", "300\n")
        assertTrue(vm.errorMessage!!.contains("0..255"))
        assertEquals(20, vm.machine!!.mem[0])
        assertEquals("p.toyo", vm.title)
    }

    // ── Reload ──

    @Test
    fun reloadReadsTheFileFromDiskAgain() {
        val vm = SimViewModel()
        vm.loadFromFile("p.toys", "load x\nstop\nx: 42\n", "/some/p.toys")
        vm.reload { path ->
            assertEquals("/some/p.toys", path)
            "load x\nstop\nx: 43\n"
        }
        assertEquals(43, vm.machine!!.mem[3])
    }

    @Test
    fun reloadOfAFileThatIsGoneShowsAnErrorAndKeepsTheProgram() {
        val vm = SimViewModel()
        vm.loadFromFile("p.toys", "load x\nstop\nx: 42\n", "/some/p.toys")
        vm.reload { null }
        assertNotNull(vm.errorMessage)
        assertEquals(42, vm.machine!!.mem[3])
    }

    @Test
    fun savingTheHighestLevelMakesThatFileTheOneReloadOpens() {
        val vm = SimViewModel()
        vm.loadFromFile("p.toys", "load x\nstop\nx: 42\n", "/some/p.toys")
        vm.saveAsmDraft("load x\nstop\nx: 43\n")
        vm.saveAs(".toys", "q.toys", "/some/q.toys")
        assertEquals("q.toys", vm.title)
        var read: String? = null
        vm.reload { path -> read = path; "load x\nstop\nx: 43\n" }
        assertEquals("/some/q.toys", read)
        assertEquals(43, vm.machine!!.mem[3])
    }

    @Test
    fun savingALowerLevelIsOnlyAnExport() {
        val vm = SimViewModel()
        vm.loadFromFile("p.toyc", "int main(void){return 42;}\n", "/some/p.toyc")
        vm.saveAs(".toyo", "p.toyo", "/some/p.toyo")
        assertEquals("p.toyc", vm.title)
        var read: String? = null
        vm.reload { path -> read = path; "int main(void){return 42;}\n" }
        assertEquals("/some/p.toyc", read)
        assertTrue(vm.hasCSource, "Reload keeps the C")
    }

    // ── code-review fixes ──

    @Test
    fun compilingAnEditTurnsReloadOff() {
        val vm = cVm()
        vm.request(BuildAction.Compile("minicc"))   // only the compiler: the file still fits
        assertTrue(vm.canReload)
        vm.saveCDraft("int main(void){return 7;}\n")
        vm.request(BuildAction.Compile())
        assertFalse(vm.canReload, "Reload would bring back the old C file")
    }

    @Test
    fun compileDropsAStaleMemorySelection() {
        val vm = cVm()
        vm.select(3, fromMemory = true)
        vm.request(BuildAction.Compile("minicc"))
        assertNull(vm.memSelectedAddr)
    }

    @Test
    fun disassembleOverAnAsmDraftListsBothLosses() {
        val vm = asmVm()
        vm.saveAsmDraft("stop\n")
        val losses = vm.lossesFor(BuildAction.Disassemble)
        assertTrue(losses.any { "labels and comments" in it }, losses.toString())
        assertTrue(losses.any { "assembly edits" in it }, losses.toString())
    }
}
