package org.bytefred.ktoy.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every way into the edit dialogs (menu bar, right-click, long-press) and every
 * button in them, for C, assembly and bytecode, on the real screen.
 */
@OptIn(ExperimentalTestApi::class)
class EditActionsTest {
    private val cSrc = "int main() {\n  int x = 1;\n  return 42;\n}\n"
    private val asmSrc = "# a comment\n        load  x\n        stop\nx:      42\n"
    private val toyo = "20\n3\n0\n42\n"
    private var savedPref: String? = null

    @BeforeTest
    fun rememberPref() {
        savedPref = loadPref(EDIT_VIEW_PREF)
        savePref(EDIT_VIEW_PREF, "select")
    }

    @AfterTest
    fun restorePref() = savePref(EDIT_VIEW_PREF, savedPref ?: "select")

    private fun ComposeUiTest.show(vm: SimViewModel, gestures: EditGestures = EditGestures(rightClick = true, longPress = false)) {
        setContent {
            KtoyTheme {
                ProvideMonoStyle {
                    CompositionLocalProvider(LocalEditGestures provides gestures) {
                        Surface { SimulatorScreen(vm, onLoad = {}) }
                    }
                }
            }
        }
        if (onAllNodes(hasText("Welcome to ktoy")).fetchSemanticsNodes().isNotEmpty()) onNodeWithText("Start").performClick()
        waitForIdle()
    }

    private fun c() = SimViewModel().apply { loadFromFile("p.toyc", cSrc) }
    private fun asm() = SimViewModel().apply { loadFromFile("p.toys", asmSrc) }
    private fun bytecode() = SimViewModel().apply { loadFromFile("p.toyo", toyo) }

    private fun ComposeUiTest.menu(title: String, item: String) {
        onNodeWithTag("menu-$title").performClick()
        waitForIdle()
        onNodeWithText(item, substring = true).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.context(row: String, item: String) {
        onNodeWithText(row, substring = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag(item).performClick()
        waitForIdle()
    }

    /** The field's text, without the line numbers that are only shown. */
    private fun ComposeUiTest.fieldText() =
        onNodeWithTag("edit-source").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
            .replace(Regex("(?m)^ *\\d+ \\| "), "")

    private fun ComposeUiTest.type(text: String) {
        onNodeWithTag("edit-source").performTextReplacement(text)
        waitForIdle()
    }

    private fun ComposeUiTest.press(label: String) {
        onNode(hasText(label)).performClick()
        waitForIdle()
    }

    /** The row shows the given highlight colour. */
    private fun ComposeUiTest.assertColour(row: String, colour: Color, expected: Boolean = true) {
        val px = onNodeWithText(row, substring = true).captureToImage().toPixelMap()
        val found = (0 until px.width).any { x -> (0 until px.height).any { y -> px[x, y] == colour } }
        assertEquals(expected, found, "'$row' has colour $colour")
    }

    // ── Assembly: Edit… ───────────────────────────────────────────────────

    @Test
    fun asmEditFromTheMenuAndFromTheRowShowTheWholeSource() = runComposeUiTest {
        val vm = asm()
        show(vm)
        menu("Assembly", "Edit…")
        assertEquals(asmSrc, fieldText())
        press("Cancel")
        context("load  x", "context-edit")
        assertEquals(asmSrc, fieldText())
    }

    @Test
    fun asmSaveStoresADraftAndKeepsTheRunningProgram() = runComposeUiTest {
        val vm = asm()
        val mem = vm.machine!!.mem.toList()
        show(vm)
        menu("Assembly", "Edit…")
        type(asmSrc.replace("42", "43"))
        press("Save")
        assertEquals(asmSrc.replace("42", "43"), vm.asmDraft)
        assertEquals(mem, vm.machine!!.mem.toList())
    }

    @Test
    fun asmSaveAndAssembleBuildsIt() = runComposeUiTest {
        val vm = asm()
        show(vm)
        menu("Assembly", "Edit…")
        type(asmSrc.replace("42", "43"))
        press("Save & Assemble")
        assertNull(vm.asmDraft)
        assertEquals(43, vm.machine!!.mem[3])
    }

    @Test
    fun asmCancelChangesNothing() = runComposeUiTest {
        val vm = asm()
        show(vm)
        menu("Assembly", "Edit…")
        type("garbage")
        press("Cancel")
        assertNull(vm.asmDraft)
        assertEquals(42, vm.machine!!.mem[3])
    }

    @Test
    fun brokenAsmShowsAnErrorAndKeepsTheDraft() = runComposeUiTest {
        val vm = asm()
        show(vm)
        menu("Assembly", "Edit…")
        type("        nonsense x\n")
        press("Save & Assemble")
        assertNotEquals(null, vm.errorMessage)
        assertEquals("        nonsense x\n", vm.asmDraft)
        assertEquals(42, vm.machine!!.mem[3])
    }

    // ── Assembly: Edit line… ──────────────────────────────────────────────

    @Test
    fun asmEditLineShowsExactlyTheClickedLine() = runComposeUiTest {
        val vm = asm()
        show(vm)
        for ((row, line) in listOf("# a comment" to "# a comment", "load  x" to "        load  x", "x:" to "x:      42")) {
            context(row, "context-edit-line")
            assertEquals(line, fieldText())
            press("Cancel")
        }
    }

    @Test
    fun asmEditLineSaveChangesOnlyThatLine() = runComposeUiTest {
        val vm = asm()
        show(vm)
        context("x:", "context-edit-line")
        type("x:      43")
        press("Save")
        assertEquals("# a comment\n        load  x\n        stop\nx:      43\n", vm.asmDraft)
        assertEquals(42, vm.machine!!.mem[3])
    }

    @Test
    fun asmEditLineSaveAndAssembleBuildsIt() = runComposeUiTest {
        val vm = asm()
        show(vm)
        context("x:", "context-edit-line")
        type("x:      43")
        press("Save & Assemble")
        assertEquals(43, vm.machine!!.mem[3])
        assertNull(vm.asmDraft)
    }

    @Test
    fun asmEditLineWorksOnADraftToo() = runComposeUiTest {
        val vm = asm()
        vm.saveAsmDraft("first\nsecond\nthird\n")
        show(vm)
        context("second", "context-edit-line")
        assertEquals("second", fieldText())
        type("changed")
        press("Save")
        assertEquals("first\nchanged\nthird\n", vm.asmDraft)
    }

    // ── C ─────────────────────────────────────────────────────────────────

    @Test
    fun cEditFromTheMenuAndFromTheRowShowTheWholeSource() = runComposeUiTest {
        val vm = c()
        show(vm)
        menu("C", "Edit…")
        assertEquals(cSrc, fieldText())
        press("Cancel")
        context("return 42", "context-edit")
        assertEquals(cSrc, fieldText())
    }

    @Test
    fun cSaveStoresADraftAndKeepsTheRunningProgram() = runComposeUiTest {
        val vm = c()
        val mem = vm.machine!!.mem.toList()
        show(vm)
        menu("C", "Edit…")
        type(cSrc.replace("42", "43"))
        press("Save")
        assertEquals(cSrc.replace("42", "43"), vm.cDraft)
        assertEquals(mem, vm.machine!!.mem.toList())
    }

    @Test
    fun cSaveAndCompileBuildsIt() = runComposeUiTest {
        val vm = c()
        show(vm)
        menu("C", "Edit…")
        type(cSrc.replace("42", "43"))
        press("Save & Compile")
        assertNull(vm.cDraft)
        assertEquals(cSrc.replace("42", "43"), vm.cText)
        assertTrue(vm.machine!!.mem.contains(43), "43 is in the new image")
    }

    @Test
    fun brokenCShowsAnErrorAndKeepsTheOldProgram() = runComposeUiTest {
        val vm = c()
        val mem = vm.machine!!.mem.toList()
        show(vm)
        menu("C", "Edit…")
        type("int main( {")
        press("Save & Compile")
        assertNotEquals(null, vm.errorMessage)
        assertEquals(mem, vm.machine!!.mem.toList())
    }

    @Test
    fun cCancelChangesNothing() = runComposeUiTest {
        val vm = c()
        show(vm)
        menu("C", "Edit…")
        type("garbage")
        press("Cancel")
        assertNull(vm.cDraft)
        assertEquals(cSrc, vm.cText)
    }

    @Test
    fun cEditLineShowsTheLineAndSavesOnlyIt() = runComposeUiTest {
        val vm = c()
        show(vm)
        context("return 42", "context-edit-line")
        assertEquals("  return 42;", fieldText())
        type("  return 43;")
        press("Save")
        assertEquals(cSrc.replace("42", "43"), vm.cDraft)
    }

    @Test
    fun cEditLineSaveAndCompileBuildsIt() = runComposeUiTest {
        val vm = c()
        show(vm)
        context("return 42", "context-edit-line")
        type("  return 43;")
        press("Save & Compile")
        assertEquals(cSrc.replace("42", "43"), vm.cText)
        assertTrue(vm.machine!!.mem.contains(43))
    }

    // ── Bytecode: Edit… ───────────────────────────────────────────────────

    @Test
    fun bytecodeEditFromTheMenuAndFromTheRowShow256Lines() = runComposeUiTest {
        val vm = bytecode()
        show(vm)
        menu("Bytecode", "Edit…")
        assertEquals(256, fieldText().split("\n").size)
        press("Cancel")
        context("1 (00000001) ->", "context-edit")
        assertEquals(256, fieldText().split("\n").size)
    }

    @Test
    fun bytecodeSaveWritesTheMemoryAndAcceptsAllNumberForms() = runComposeUiTest {
        val vm = bytecode()
        show(vm)
        menu("Bytecode", "Edit…")
        type("20\nb11\n0\n0x2b\n")
        press("Save")
        assertEquals(listOf(20, 3, 0, 43, 0), vm.machine!!.mem.take(5))
    }

    @Test
    fun bytecodeSaveAndDisassembleWritesAndLoadsAssembly() = runComposeUiTest {
        val vm = bytecode()
        show(vm)
        menu("Bytecode", "Edit…")
        type("20\n3\n0\n43\n")
        press("Save & Disassemble")
        assertEquals(43, vm.machine!!.mem[3])
        assertTrue(vm.hasSource, "the assembly column is there")
    }

    @Test
    fun bytecodeDisassembleAsksFirstWhenItReplacesAssembly() = runComposeUiTest {
        val vm = asm()
        show(vm)
        menu("Bytecode", "Edit…")
        press("Save & Disassemble")
        assertNotEquals(null, vm.pendingAction)
        val before = vm.asmText
        press("Cancel")
        assertEquals(before, vm.asmText)
        menu("Bytecode", "Edit…")
        press("Save & Disassemble")
        onNode(hasText("Disassemble")).performClick()
        waitForIdle()
        assertNotEquals(before, vm.asmText)
    }

    @Test
    fun brokenBytecodeIsNotSavedByEitherButton() = runComposeUiTest {
        val vm = bytecode()
        show(vm)
        menu("Bytecode", "Edit…")
        for (button in listOf("Save", "Save & Disassemble")) for (bad in listOf("abc\n", "300\n", "1\n".repeat(257))) {
            type(bad)
            press(button)
            onNodeWithTag("edit-source").assertExists()
            assertEquals(listOf(20, 3, 0, 42), vm.machine!!.mem.take(4), "$button with ${bad.take(5)}")
            assertFalse(vm.hasSource)
        }
    }

    @Test
    fun bytecodeCancelChangesNothing() = runComposeUiTest {
        val vm = bytecode()
        show(vm)
        menu("Bytecode", "Edit…")
        type("1\n2\n")
        press("Cancel")
        assertEquals(listOf(20, 3, 0, 42), vm.machine!!.mem.take(4))
    }

    @Test
    fun openingTheBytecodeEditStopsARun() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("p.toys", "loop:   goto loop\n") }
        show(vm)
        vm.toggleRun()
        waitForIdle()
        menu("Bytecode", "Edit…")
        assertFalse(vm.running)
    }

    @Test
    fun aBuildClearsTheSelection() = runComposeUiTest {
        val vm = asm()
        show(vm)
        context("x:", "context-edit")
        assertNotEquals(null, vm.selectedAddr)
        type("# new line\n$asmSrc")
        press("Save & Assemble")
        assertNull(vm.selectedAddr)
        assertNull(vm.memSelectedAddr)
    }

    @Test
    fun anIoEditDoesNotMoveTheEditLineTarget() = runComposeUiTest {
        val vm = bytecode()
        show(vm)
        vm.select(5, fromMemory = true)
        vm.editCell(240, 65)
        assertNull(vm.memSelectedAddr)
        vm.select(5, fromMemory = true)
        vm.editCell(5, 65)
        assertEquals(5, vm.memSelectedAddr)
    }

    // ── Bytecode: Edit line… ──────────────────────────────────────────────

    @Test
    fun bytecodeEditLineSelectFromTheRowAndTheMenu() = runComposeUiTest {
        val vm = bytecode()
        show(vm)
        context("3 (00000011) ->", "context-edit-line")
        onNodeWithTag("pick-val-99").performClick()
        waitForIdle()
        assertEquals(99, vm.machine!!.mem[3])
        menu("Bytecode", "Edit line…")
        onNodeWithTag("pick-op-21").performClick()
        waitForIdle()
        assertEquals(21, vm.machine!!.mem[3])
    }

    @Test
    fun bytecodeEditLineTypeSaveAndCancel() = runComposeUiTest {
        val vm = bytecode()
        show(vm)
        context("3 (00000011) ->", "context-edit-line")
        onNodeWithTag("edit-view-type").performClick()
        waitForIdle()
        onNodeWithTag("edit-value").performTextReplacement("0x10")
        press("Save")
        assertEquals(16, vm.machine!!.mem[3])
        context("3 (00000011) ->", "context-edit-line")
        onNodeWithTag("edit-value").performTextReplacement("77")
        press("Cancel")
        assertEquals(16, vm.machine!!.mem[3])
    }

    @Test
    fun bytecodeEditLineHasNoBuildButton() = runComposeUiTest {
        val vm = bytecode()
        show(vm)
        context("3 (00000011) ->", "context-edit-line")
        onNodeWithText("Save & ", substring = true).assertDoesNotExist()
    }

    // ── Right-click and long-press highlight the row in orange ────────────

    @Test
    fun aRightClickPaintsTheAsmRowOrange() = runComposeUiTest {
        val vm = asm()
        show(vm)
        assertColour("x:", HL_SELECTED, expected = false)
        onNodeWithText("x:", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        assertColour("x:", HL_SELECTED)
    }

    @Test
    fun aRightClickPaintsTheMemoryRowOrange() = runComposeUiTest {
        val vm = bytecode()
        show(vm)
        assertColour("3 (00000011) ->", HL_SELECTED, expected = false)
        onNodeWithText("3 (00000011) ->", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        assertColour("3 (00000011) ->", HL_SELECTED)
    }

    @Test
    fun aRightClickPaintsTheCRowOrange() = runComposeUiTest {
        val vm = c()
        show(vm)
        assertColour("return 42", HL_SELECTED, expected = false)
        onNodeWithText("return 42", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        assertColour("return 42", HL_SELECTED)
    }

    @Test
    fun aLongPressPaintsTheRowOrangeOnTouch() = runComposeUiTest {
        val vm = asm()
        show(vm, EditGestures(rightClick = false, longPress = true))
        onNodeWithText("x:", substring = true).performTouchInput { longClick() }
        waitForIdle()
        assertColour("x:", HL_SELECTED)
        onNodeWithTag("context-edit").assertExists()
    }

    @Test
    fun theHighlightStaysWhileTheEditLineDialogIsOpen() = runComposeUiTest {
        val vm = bytecode()
        show(vm)
        context("3 (00000011) ->", "context-edit-line")
        assertEquals(3, vm.selectedAddr)
        press("Cancel")
        assertEquals(3, vm.selectedAddr)
    }

    @Test
    fun aRightClickPaintsACommentRowOrange() = runComposeUiTest {
        val vm = asm()
        show(vm)
        onNodeWithText("# a comment", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag("context-edit-line").assertExists()
        assertColour("# a comment", HL_SELECTED)
    }

    @Test
    fun aRightClickPaintsACLineWithoutCodeOrange() = runComposeUiTest {
        val vm = c()
        show(vm)
        onNodeWithText("| }", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        assertColour("| }", HL_SELECTED)
        onNodeWithTag("context-edit-line").performClick()
        waitForIdle()
        press("Cancel")
        assertColour("| }", HL_SELECTED)
        onNodeWithText("return 42", substring = true).performClick()
        waitForIdle()
        assertColour("| }", HL_SELECTED, expected = false)
    }

    @Test
    fun aTapMovesTheHighlightAwayFromAMarkedRow() = runComposeUiTest {
        val vm = asm()
        show(vm)
        onNodeWithText("# a comment", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        assertColour("# a comment", HL_SELECTED)
        onNodeWithTag("context-edit-line").performClick()
        waitForIdle()
        press("Cancel")
        onNodeWithText("x:", substring = true).performClick()
        waitForIdle()
        assertColour("# a comment", HL_SELECTED, expected = false)
        assertColour("x:", HL_SELECTED)
    }

    @Test
    fun aRightClickOnADraftLineOpensTheMenu() = runComposeUiTest {
        val vm = asm()
        vm.saveAsmDraft("first\nsecond\n")
        show(vm)
        onNodeWithText("second", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag("context-edit-line").assertExists()
        assertColour("second", HL_SELECTED)
    }
}
