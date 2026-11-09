package org.bytefred.ktoy.ui

import org.bytefred.ktoy.core.EXAMPLES
import org.bytefred.ktoy.core.ExampleKind
import org.bytefred.ktoy.ui.SimViewModel.BuildAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edit (draft) -> Compile / Assemble / Disassemble, and the confirm-before-loss rule. */
class EditAndDecompileTest {

    private fun asmVm() = SimViewModel().apply { loadFromFile("prog.toys", "load x\nstop\nx: 42\n") }
    private fun cVm() = SimViewModel().apply { loadFromFile("prog.toyc", "int main(void){return 42;}\n") }

    @Test
    fun saveOnlyStoresTheDraftAssembleBuildsIt() {
        val vm = asmVm()
        vm.saveAsmDraft("load x\nstop\nx: 43\n")
        assertEquals(42, vm.machine!!.mem[3], "Save must not build")
        vm.request(BuildAction.Assemble)
        assertNull(vm.pendingAction)
        assertNull(vm.asmDraft)
        assertEquals(43, vm.machine!!.mem[3])
        assertEquals("prog.toys", vm.title)
    }

    @Test
    fun unchangedTextIsNoDraft() {
        val vm = asmVm()
        vm.saveAsmDraft(vm.asmText!!)
        assertNull(vm.asmDraft)
    }

    @Test
    fun brokenAsmShowsAnErrorAndKeepsEverything() {
        val vm = asmVm()
        vm.saveAsmDraft("lod x\nstop\nx: 1\n")
        vm.request(BuildAction.Assemble)
        val err = assertNotNull(vm.errorMessage)
        assertTrue("did you mean 'load'" in err, err)
        assertEquals(42, vm.machine!!.mem[3])
        assertNotNull(vm.asmDraft, "the draft survives a failed build")
    }

    @Test
    fun assemblingTheAsmOfACProgramAsksThenDropsTheC() {
        val vm = cVm()
        vm.request(BuildAction.Assemble)
        assertEquals(BuildAction.Assemble, vm.pendingAction)
        assertTrue(vm.hasCSource, "nothing happens before the OK")
        vm.cancelPending()
        assertTrue(vm.hasCSource)

        vm.request(BuildAction.Assemble)
        vm.confirmPending()
        assertFalse(vm.hasCSource)
        assertEquals("prog.toys", vm.title)
    }

    @Test
    fun asmDraftInACProgramKeepsTheCUntilAssemble() {
        val vm = cVm()
        vm.saveAsmDraft(vm.asmText!! + "\n# edited\n")
        assertTrue(vm.hasCSource)
        assertNotNull(vm.asmDraft)
    }

    @Test
    fun compileBuildsTheCDraft() {
        val vm = cVm()
        vm.saveCDraft("int main(void){return 7;}\n")
        assertNotNull(vm.cDraft)
        vm.request(BuildAction.Compile())
        assertNull(vm.cDraft)
        assertTrue("return 7" in vm.cText!!)
    }

    @Test
    fun compilerSwitchRecompiles() {
        val vm = cVm()
        assertEquals("toycc", vm.currentCompiler, "full C is the default")
        vm.request(BuildAction.Compile("minicc"))
        assertEquals("minicc", vm.currentCompiler)
    }

    @Test
    fun lossesListWhatWouldBeThrownAway() {
        val vm = asmVm()
        assertEquals(emptyList(), vm.lossesFor(BuildAction.Assemble))
        vm.step()
        assertTrue(vm.lossesFor(BuildAction.Assemble).any { "run" in it })
        vm.editCell(3, 9)
        assertTrue(vm.lossesFor(BuildAction.Assemble).any { "memory edits" in it })
        // disassemble keeps the memory, so no memory-edit loss, but the asm is replaced
        val dis = vm.lossesFor(BuildAction.Disassemble)
        assertFalse(dis.any { "memory edits" in it })
        assertTrue(dis.any { "labels and comments" in it })

        val c = cVm()
        c.saveAsmDraft("stop\n")
        assertTrue(c.lossesFor(BuildAction.Compile()).any { "assembly edits" in it })
        assertTrue(c.lossesFor(BuildAction.Disassemble).any { "C code" in it })
    }

    @Test
    fun theConfirmTextIsOneSentence() {
        assertEquals("Assemble discards the current run.", lossSentence("Assemble", listOf("the current run")))
        assertEquals(
            "Disassemble discards the C code, your C edits and the current run.",
            lossSentence("Disassemble", listOf("the C code", "your C edits", "the current run")),
        )
    }

    @Test
    fun disassembleOpensTheAsmViewForBytecode() {
        val vm = SimViewModel()
        vm.loadFromFile("prog.toyo", "     0   00010100  00000011\n     2   00000000\n     3   00101010\n")
        assertFalse(vm.hasSource)
        val mem = vm.machine!!.mem.copyOf()
        vm.request(BuildAction.Disassemble)
        assertNull(vm.pendingAction, "nothing to lose for plain bytecode")
        assertTrue(vm.hasSource)
        assertEquals("prog.toys", vm.title)
        assertTrue(mem.contentEquals(vm.machine!!.mem))
    }

    @Test
    fun disassembleKeepsLiveMemoryEdits() {
        val vm = asmVm()
        vm.editCell(3, 9)
        vm.request(BuildAction.Disassemble)
        vm.confirmPending()
        assertEquals(9, vm.machine!!.mem[3])
    }

    @Test
    fun reloadCannotSilentlyUndoAnAssemble() {
        val vm = cVm()
        assertTrue(vm.canReload)
        vm.request(BuildAction.Assemble)
        vm.confirmPending()
        assertFalse(vm.canReload, "Reload would bring back the old C file")
    }

    @Test
    fun fileSaveWritesTheDraft() {
        val vm = asmVm()
        vm.saveAsmDraft("stop\n")
        assertEquals("stop\n", vm.exportText(".toys"))
    }

    /** Edit line relies on it: a listing row is its line of the assembly source. */
    @Test
    fun everyListingRowIsItsSourceLine() {
        for (ex in EXAMPLES) {
            if (ex.kind == ExampleKind.TOYO) continue
            val vm = SimViewModel().apply { load(ex) }
            val lines = vm.asmText!!.split("\n")
            vm.listing.forEachIndexed { i, row ->
                assertEquals(lines[i].trimEnd(), row.orig.trimEnd(), "${ex.name} row $i")
            }
        }
    }
}
