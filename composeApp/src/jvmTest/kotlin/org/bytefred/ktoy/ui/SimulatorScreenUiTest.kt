package org.bytefred.ktoy.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives the real simulator screen on desktop: dividers, the C / Assembly /
 * Bytecode menus, the edit dialogs (menu and right-click / long-press), the
 * confirm-before-loss dialog, "Save changes?" and Disassemble.
 */
@OptIn(ExperimentalTestApi::class)
class SimulatorScreenUiTest {
    private var savedEditView: String? = null

    @kotlin.test.BeforeTest
    fun rememberEditView() { savedEditView = loadPref(EDIT_VIEW_PREF) }

    @kotlin.test.AfterTest
    fun restoreEditView() = savePref(EDIT_VIEW_PREF, savedEditView ?: "select")


    private val asmSrc = "load x\nstop\nx: 42\n"
    private val cSrc = "int main(void){return 42;}\n"

    private fun ComposeUiTest.show(
        vm: SimViewModel,
        width: Dp? = null,
        gestures: EditGestures = platformEditGestures,
    ) {
        setContent {
            KtoyTheme {
                ProvideMonoStyle {
                    val size = if (width != null) Modifier.width(width).fillMaxHeight() else Modifier.fillMaxSize()
                    CompositionLocalProvider(LocalEditGestures provides gestures) {
                        Surface(size) { SimulatorScreen(vm, onLoad = {}) }
                    }
                }
            }
        }
        // the welcome dialog shows unless the user turned it off on this machine
        if (onAllNodes(hasText("Welcome to ktoy")).fetchSemanticsNodes().isNotEmpty())
            onNodeWithText("Start").performClick()
    }

    private fun ComposeUiTest.menu(title: String, item: String) {
        onNodeWithTag("menu-$title").performClick()
        // substring: check items carry a "✓ " / "   " prefix
        onNodeWithText(item, substring = true).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.typeInEditDialog(text: String) {
        onNodeWithTag("edit-source").performTextReplacement(text)
        onNodeWithText("Save").performClick()
        waitForIdle()
    }

    @Test
    fun draggingTheDividerChangesTheSplit() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithTag("divider-0").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(15f, 0f)) }
            up()
        }
        waitForIdle()
        val asm = vm.wideWeights.getValue(Panel.ASM)
        val mem = vm.wideWeights.getValue(Panel.MEMORY)
        assertTrue(asm > mem, "asm $asm should have grown against memory $mem")
        assertEquals(2f, asm + mem, 1e-3f, "a drag only moves share between the two neighbours")
    }

    @Test
    fun theFirstStepIsStepOne() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithText("Step #1:", substring = true).assertExists()
        vm.step()
        waitForIdle()
        onNodeWithText("Step #2:", substring = true).assertExists()
    }

    @Test
    fun editAsmViaMenuThenAssemble() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        menu("Assembly", "Edit…")
        typeInEditDialog("load x\nstop\nx: 43\n")
        onNodeWithText("Assembly (modified)").assertExists()
        assertEquals(42, vm.machine!!.mem[3], "Save alone must not build")

        menu("Assembly", "Assemble")
        onNodeWithText("Assembly (modified)").assertDoesNotExist()
        assertEquals(43, vm.machine!!.mem[3])
    }

    @Test
    fun rightClickOnAnAsmLineOffersEdit() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithText("load x", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        assertEquals(vm.machine!!.mem.indexOf(20), vm.selectedAddr, "a right-click highlights the row")
        onNodeWithTag("context-edit").performClick()
        waitForIdle()
        onNodeWithText("Edit assembly").assertExists()
    }

    @Test
    fun aDoubleClickNoLongerEdits() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithText("load x", substring = true).performTouchInput { doubleClick() }
        waitForIdle()
        onNodeWithText("Edit assembly").assertDoesNotExist()
        onNodeWithTag("context-edit").assertDoesNotExist()
    }

    @Test
    fun rightClickOnTheCSourceOffersEdit() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyc", cSrc) }
        show(vm)
        onNodeWithText("int main", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag("context-edit").performClick()
        waitForIdle()
        onNodeWithText("Edit C source").assertExists()
    }

    @Test
    fun rightClickOnAMemoryCellEditsThatCell() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        // the memory row of address 3 (x: 42)
        onNodeWithText("3 (00000011) ->", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag("context-edit-line").performClick()
        waitForIdle()
        assertEquals(3, vm.memSelectedAddr)
        onNodeWithText("address 3", substring = true).assertExists()
        onNodeWithText("Save & ", substring = true).assertDoesNotExist()
        onNodeWithTag("edit-view-type").performClick()
        waitForIdle()
        onNodeWithTag("edit-value").performTextReplacement("9")
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals(9, vm.machine!!.mem[3])
    }

    @Test
    fun longPressOffersEditOnTouchPlatforms() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm, gestures = EditGestures(rightClick = false, longPress = true))
        onNodeWithText("load x", substring = true).performTouchInput { longClick() }
        waitForIdle()
        onNodeWithTag("context-edit").performClick()
        waitForIdle()
        onNodeWithText("Edit assembly").assertExists()
    }

    @Test
    fun noLongPressMenuOnTheDesktop() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithText("load x", substring = true).performTouchInput { longClick() }
        waitForIdle()
        onNodeWithTag("context-edit").assertDoesNotExist()
    }

    @Test
    fun memoryRowsHaveNoExtraPadding() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("prog.toys", asmSrc) })
        onNodeWithText(">>   0 (00000000) -> ", substring = true).assertExists()
        onNodeWithText("    addr (binary) -> value (binary)").assertExists()
    }

    @Test
    fun escapeClosesAnOpenMenu() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("prog.toys", asmSrc) })
        onNodeWithTag("menu-File").performClick()
        waitForIdle()
        onNodeWithText("Examples…").assertExists()
        onNodeWithTag("menu-bar").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithText("Examples…").assertDoesNotExist()
    }

    @Test
    fun reloadWithEditsAsksAndDiscardReloads() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        vm.editCell(3, 9)
        show(vm)
        onNodeWithText("Reload").performClick()
        onNodeWithText("Save changes?").assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals(9, vm.machine!!.mem[3], "Cancel keeps the edit")

        onNodeWithText("Reload").performClick()
        onNodeWithText("Discard").performClick()
        waitForIdle()
        onNodeWithText("Save changes?").assertDoesNotExist()
        assertEquals(42, vm.machine!!.mem[3])
    }

    @Test
    fun reloadAfterARunDoesNotAsk() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithText("Step").performClick()
        onNodeWithText("Reload").performClick()
        onNodeWithText("Save changes?").assertDoesNotExist()
        assertEquals(0, vm.machine!!.stepCount)
    }

    @Test
    fun saveOffersEachFormatHighestFirst() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("prog.toyc", cSrc) })
        menu("File", "Save…")
        val tops = listOf("Save C", "Save assembly", "Save bytecode").map { onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops, "highest level on top")
        onNodeWithText("Discard").assertDoesNotExist()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        onNodeWithText("Save C").assertDoesNotExist()
    }

    @Test
    fun saveOffersOnlyTheFormatsTheProgramHas() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("prog.toys", asmSrc) })
        menu("File", "Save…")
        onNodeWithText("Save assembly").assertExists()
        onNodeWithText("Save bytecode").assertExists()
        onNodeWithText("Save C").assertDoesNotExist()
        onNodeWithText("Cancel").performClick()
    }

    @Test
    fun theQuestionOffersTheFormatsDiscardAndCancel() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        vm.saveAsmDraft("stop\n")
        show(vm)
        onNodeWithText("Reload").performClick()
        onNodeWithText("Save assembly").assertExists()
        onNodeWithText("Discard").assertExists()
        onNodeWithText("Cancel").performClick()   // cancels the reload too
        waitForIdle()
        onNodeWithText("Save changes?").assertDoesNotExist()
        assertEquals("stop\n", vm.asmDraft)
    }

    @Test
    fun savingAFormatThatLacksTheEditAsksFirst() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyc", cSrc) }
        vm.saveCDraft("int main(void){return 7;}\n")
        show(vm)
        menu("File", "Save…")
        onNodeWithText("Save assembly").performClick()
        waitForIdle()
        onNodeWithText("This file lacks your edits").assertExists()
        onNodeWithText("not compiled yet", substring = true).assertExists()
        onNodeWithText("Back").performClick()
        waitForIdle()
        onNodeWithText("This file lacks your edits").assertDoesNotExist()
        onNodeWithText("Save C").assertExists()
    }

    @Test
    fun theQuestionNamesWhatIsNotSaved() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        vm.saveAsmDraft("stop\n")
        show(vm)
        onNodeWithText("Reload").performClick()
        waitForIdle()
        onNodeWithText("Not saved to a file yet: assembly.").assertExists()
    }

    @Test
    fun reloadingABrokenBytecodeFileShowsAnErrorAndKeepsTheProgram() = runComposeUiTest {
        val file = java.io.File.createTempFile("prog", ".toyo").apply { deleteOnExit() }
        file.writeText("20\n6\n")
        val vm = SimViewModel().apply { loadFromFile("prog.toyo", file.readText(), file.path) }
        show(vm)
        val broken = listOf(
            "-1\n".toByteArray(), "abc\n".toByteArray(), "20 6\n".toByteArray(), "300\n".toByteArray(),
            "1\n".repeat(257).toByteArray(), "0x14\n".toByteArray(), byteArrayOf(0, 1, -1, -2),
        )
        for (bytes in broken) {
            file.writeBytes(bytes)
            onNodeWithText("Reload").performClick()
            waitForIdle()
            assertTrue(vm.errorMessage != null, "no error for ${bytes.decodeToString()}")
            onNodeWithText(vm.errorMessage!!).assertExists()
            onNodeWithText("OK").performClick()
            waitForIdle()
            assertEquals(null, vm.errorMessage)
            assertEquals(20, vm.machine!!.mem[0], "the program stays")
        }
        file.writeText("# fixed\n7\n")
        onNodeWithText("Reload").performClick()
        waitForIdle()
        assertEquals(null, vm.errorMessage)
        assertEquals(7, vm.machine!!.mem[0])
    }

    @Test
    fun aSavedBytecodeFileIsOnlyValuesAndLoadsBack() = runComposeUiTest {
        val file = java.io.File.createTempFile("saved", ".toyo").apply { deleteOnExit() }
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        vm.editCell(3, 9)
        file.writeText(vm.beginSave(".toyo")!!)
        vm.fileSaved(file.name, file.path)
        assertEquals("20\n3\n0\n9\n", file.readText())

        val back = SimViewModel().apply { loadFromFile(file.name, file.readText(), file.path) }
        show(back)
        assertEquals(null, back.errorMessage)
        assertFalse(back.hasSource)
        assertEquals(vm.machine!!.programImage().toList(), back.machine!!.mem.toList())
    }

    @Test
    fun undoAfterABytecodeSaveAsksAndResetDoesNot() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        vm.editCell(3, 9)
        vm.beginSave(".toyo")
        vm.fileSaved("prog.toyo")
        show(vm)

        onNodeWithText("Undo").performClick()
        waitForIdle()
        onNodeWithText("Reload").performClick()
        waitForIdle()
        onNodeWithText("Not saved to a file yet: memory edits.").assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()

        vm.editCell(3, 9)
        waitForIdle()
        onNodeWithText("Reset").performClick()
        waitForIdle()
        onNodeWithText("Reload").performClick()
        waitForIdle()
        onNodeWithText("Save changes?").assertDoesNotExist()
    }

    @Test
    fun loadAndExamplesAreOnlyInTheFileMenu() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("prog.toys", asmSrc) })
        onNodeWithText("Load").assertDoesNotExist()
        onNodeWithText("Examples").assertDoesNotExist()
        onNodeWithTag("menu-Execution").assertDoesNotExist()
        onNodeWithTag("menu-File").performClick()
        waitForIdle()
        onNodeWithText("Load…").assertExists()
        onNodeWithText("Examples…").assertExists()
    }

    @Test
    fun editLineChangesOnlyThatAsmLine() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithText("x: 42", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag("context-edit-line").performClick()
        waitForIdle()
        onNodeWithText("Edit line 3").assertExists()
        onNodeWithTag("edit-source").assertTextContains("x: 42")
        onNodeWithTag("edit-source").performTextReplacement("x: 7")
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals("load x\nstop\nx: 7\n", vm.asmDraft)
        assertEquals(42, vm.machine!!.mem[3], "Save does not build")

        onNodeWithText("x: 7", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag("context-edit-line").performClick()
        waitForIdle()
        onNodeWithTag("edit-source").performTextReplacement("x: 8")
        onNodeWithText("Save & Assemble").performClick()
        waitForIdle()
        assertEquals(null, vm.asmDraft)
        assertEquals(8, vm.machine!!.mem[3])
    }

    @Test
    fun editLineChangesOnlyThatCLine() = runComposeUiTest {
        val src = "int main(void){\n  return 42;\n}\n"
        val vm = SimViewModel().apply { loadFromFile("prog.toyc", src) }
        show(vm)
        onNodeWithText("return 42", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag("context-edit-line").performClick()
        waitForIdle()
        onNodeWithText("Edit line 2").assertExists()
        onNodeWithTag("edit-source").performTextReplacement("  return 7;")
        onNodeWithText("Save & Compile").performClick()
        waitForIdle()
        assertEquals("int main(void){\n  return 7;\n}\n", vm.cText)
    }

    @Test
    fun editTheWholeBytecodeShows256LinesAndMissingLinesAreZero() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithText("3 (00000011) ->", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag("context-edit").performClick()
        waitForIdle()
        onNodeWithText("Edit bytecode").assertExists()
        val shown = onNodeWithTag("edit-source").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        assertEquals(256, shown.split("\n").size)
        assertEquals(listOf("  0 | 20", "  1 | 3", "  2 | 0", "  3 | 42"), shown.split("\n").take(4))
        assertEquals("255 | 0", shown.split("\n").last())
        onNodeWithText("Save & Disassemble").assertExists()

        onNodeWithTag("edit-source").performTextReplacement("1\n".repeat(257))
        onNodeWithText("Save").performClick()
        waitForIdle()
        onNodeWithText("expected at most 256", substring = true).assertExists()
        assertEquals(20, vm.machine!!.mem[0], "an error changes nothing")

        onNodeWithTag("edit-source").performTextReplacement("abc\n")
        onNodeWithText("Save").performClick()
        waitForIdle()
        onNodeWithText("Edit bytecode").assertExists()

        onNodeWithTag("edit-source").performTextReplacement("20\n3\n0\n9\n")
        onNodeWithText("Save").performClick()
        waitForIdle()
        onNodeWithText("Edit bytecode").assertDoesNotExist()
        assertEquals(listOf(20, 3, 0, 9, 0), vm.machine!!.mem.take(5))
        assertEquals(mapOf(3 to 9), vm.machine!!.handEdits)
        assertEquals(listOf(".toyo"), vm.unsavedFormats())

        onNodeWithText("Undo").performClick()
        waitForIdle()
        assertEquals(42, vm.machine!!.mem[3], "one Undo takes the whole edit back")
        assertFalse(vm.unsavedEdits)
    }

    @Test
    fun theSourceEditsShowLineNumbersThatAreNotPartOfTheText() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithTag("menu-Assembly").performClick()
        onNodeWithText("Edit…").performClick()
        onNodeWithTag("edit-source").assertTextContains("1 | load x", substring = true)
        onNodeWithTag("edit-source").assertTextContains("3 | x: 42", substring = true)
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals(null, vm.asmDraft, "the numbers did not end up in the text")
    }

    @Test
    fun typingInANumberedEditGoesWhereTheCursorIs() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithTag("menu-Assembly").performClick()
        onNodeWithText("Edit…").performClick()
        onNodeWithTag("edit-source").performTextInputSelection(androidx.compose.ui.text.TextRange(5))
        onNodeWithTag("edit-source").performTextInput("#")
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals("load #x\nstop\nx: 42\n", vm.asmDraft)
    }

    @Test
    fun lineNumberOffsetsMapBothWaysForEveryPosition() {
        for ((text, first) in listOf("a\nbc\n\nd" to 1, "" to 0, "\n" to 0, ("1\n".repeat(120)) to 0)) {
            val t = LineNumbers(first).filter(androidx.compose.ui.text.AnnotatedString(text))
            for (o in 0..text.length) {
                val shown = t.offsetMapping.originalToTransformed(o)
                assertTrue(shown in 0..t.text.length, "'$text' $o -> $shown")
                assertEquals(o, t.offsetMapping.transformedToOriginal(shown), "'$text' at $o")
            }
            for (shown in 0..t.text.length)
                assertTrue(t.offsetMapping.transformedToOriginal(shown) in 0..text.length, "'$text' shown $shown")
        }
        assertEquals("1 | a\n2 | bc", LineNumbers(1).filter(androidx.compose.ui.text.AnnotatedString("a\nbc")).text.text)
    }

    @Test
    fun theWholeBytecodeEditTakesDecimalBinaryAndHexPerLine() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        assertEquals(null, vm.bytecodeError("20\nb101\n0x1f\n# note\n0b11 # three\n00000011\n"))
        vm.saveBytecode("20\nb101\n0x1f\n0b11\n00000011\n")
        assertEquals(listOf(20, 5, 31, 3, 3, 0), vm.machine!!.mem.take(6))
        for (bad in listOf("300", "0x100", "b111111111", "-1", "20 6", "b102", "abc"))
            assertTrue(vm.bytecodeError(bad) != null, bad)
        assertTrue(vm.bytecodeError("1\n".repeat(257))!!.contains("at most 256"))
    }

    @Test
    fun theSelectViewWritesTheClickedOperationIntoTheCell() = runComposeUiTest {
        savePref(EDIT_VIEW_PREF, "select")
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        val before = vm.machine!!.mem[3]
        onNodeWithText("3 (00000011) ->", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag("context-edit-line").performClick()
        waitForIdle()
        onNodeWithTag("pick-op-21").performClick()
        waitForIdle()
        assertEquals(21, vm.machine!!.mem[3])
        onNodeWithTag("pick-op-21").assertDoesNotExist()
        onNodeWithText("Undo").performClick()
        waitForIdle()
        assertEquals(before, vm.machine!!.mem[3])
    }

    @Test
    fun saveAndDisassembleInTheBytecodeEdit() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyo", "20\n6\n0\n") }
        show(vm)
        onNodeWithText("Bytecode").performClick()
        waitForIdle()
        onNodeWithText("Edit…").performClick()
        waitForIdle()
        onNodeWithText("Save", substring = false).assertExists()
        onNodeWithTag("edit-source").performTextReplacement("20\n7\n0\n")
        onNodeWithText("Save & Disassemble").performClick()
        waitForIdle()
        onAllNodesWithText("Disassemble").fetchSemanticsNodes().let { if (it.isNotEmpty()) onAllNodesWithText("Disassemble").onFirst().performClick() }
        waitForIdle()
        assertEquals(7, vm.machine!!.mem[1])
        assertTrue(vm.hasSource, "assembly column is there")
    }

    @Test
    fun aBrokenBytecodeEditIsNotSavedByDisassemble() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyo", "20\n6\n0\n") }
        show(vm)
        onNodeWithText("Bytecode").performClick()
        waitForIdle()
        onNodeWithText("Edit…").performClick()
        waitForIdle()
        onNodeWithTag("edit-source").performTextReplacement("20\nabc\n")
        onNodeWithText("Save & Disassemble").performClick()
        waitForIdle()
        assertEquals(6, vm.machine!!.mem[1])
        assertTrue(!vm.hasSource)
        onNodeWithText("Save & Disassemble").assertExists()
    }

    @Test
    fun aRightClickHighlightsTheMemoryRow() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithText("3 (00000011) ->", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        assertEquals(3, vm.selectedAddr)
        assertEquals(3, vm.memSelectedAddr)
    }

    @Test
    fun aRightClickHighlightsTheCLine() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyc", "int main() {\n  return 42;\n}\n") }
        show(vm)
        onNodeWithText("return 42", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        assertEquals(2, vm.selectedCLine)
    }

    @Test
    fun aLongPressHighlightsTheRowOnTouch() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm, gestures = EditGestures(rightClick = false, longPress = true))
        onNodeWithText("3 (00000011) ->", substring = true).performTouchInput { longClick() }
        waitForIdle()
        assertEquals(3, vm.selectedAddr)
        onNodeWithTag("context-edit").assertExists()
    }

    @Test
    fun theSingleCellEditTakesBinaryWithB() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithText("3 (00000011) ->", substring = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag("context-edit-line").performClick()
        waitForIdle()
        onNodeWithTag("edit-view-type").performClick()
        waitForIdle()
        onNodeWithTag("edit-value").performTextReplacement("b1001")
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals(9, vm.machine!!.mem[3])
    }

    private fun ComposeUiTest.barTop(label: String) = onNodeWithText(label).getUnclippedBoundsInRoot().top

    @Test
    fun aNarrowBottomBarPutsRunOnItsOwnLine() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }, width = 360.dp)
        waitForIdle()
        assertEquals(barTop("Reset"), barTop("Step"))
        assertEquals(barTop("Undo"), barTop("Step"))
        assertTrue(barTop("Run") > barTop("Step"), "Run wraps")
        onNodeWithText("steps/sec:").assertIsDisplayed()
        assertTrue(onNodeWithText("Run").getUnclippedBoundsInRoot().right <= 360.dp)
    }

    @Test
    fun aRoomyBottomBarKeepsEverythingOnOneLine() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }, width = 780.dp)
        waitForIdle()
        assertEquals(barTop("Reset"), barTop("Step"))
        val sameLine = barTop("Run") - barTop("Step")
        assertTrue(sameLine.value == 0f, "Run is $sameLine below Step")
    }

    @Test
    fun deletedLinesAtTheEndAreZeroInTheWholeBytecodeEdit() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyo", "1\n2\n3\n4\n5\n") }
        vm.saveBytecode("1\n2\n")
        assertEquals(listOf(1, 2, 0, 0, 0), vm.machine!!.mem.take(5))
        assertEquals(mapOf(2 to 0, 3 to 0, 4 to 0), vm.machine!!.handEdits)
    }

    @Test
    fun saveAndAssembleBuildsAtOnce() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        onNodeWithTag("menu-Assembly").performClick()
        onNodeWithText("Edit…").performClick()
        onNodeWithTag("edit-source").performTextReplacement("load x\nstop\nx: 7\n")
        onNodeWithText("Save & Assemble").performClick()
        waitForIdle()
        onNodeWithText("Edit assembly").assertDoesNotExist()
        assertEquals(null, vm.asmDraft)
        assertEquals(7, vm.machine!!.mem[3])
    }

    @Test
    fun saveAndCompileBuildsAtOnceAndAFailureKeepsTheText() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyc", cSrc) }
        show(vm)
        onNodeWithTag("menu-C").performClick()
        onNodeWithText("Edit…").performClick()
        onNodeWithTag("edit-source").performTextReplacement("int main(void){return 7;}\n")
        onNodeWithText("Save & Compile").performClick()
        waitForIdle()
        assertEquals("int main(void){return 7;}\n", vm.cText)
        assertEquals(null, vm.cDraft)

        onNodeWithTag("menu-C").performClick()
        onNodeWithText("Edit…").performClick()
        onNodeWithTag("edit-source").performTextReplacement("not c\n")
        onNodeWithText("Save & Compile").performClick()
        waitForIdle()
        onNodeWithText("OK").assertExists()
        assertEquals("not c\n", vm.cDraft)
    }

    @Test
    fun saveAndAssembleOfACProgramStillAsksAboutTheCColumn() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyc", cSrc) }
        show(vm)
        onNodeWithTag("menu-Assembly").performClick()
        onNodeWithText("Edit…").performClick()
        onNodeWithTag("edit-source").performTextReplacement("stop\n")
        onNodeWithText("Save & Assemble").performClick()
        waitForIdle()
        onNodeWithText("Assemble?").assertExists()
        assertTrue(vm.hasCSource, "nothing happens before the answer")
    }

    @Test
    fun theInstructionSetHelpHasAScrollbarAndNoLongLines() = androidx.compose.ui.test.runDesktopComposeUiTest(640, 360) {
        show(SimViewModel().apply { loadFromFile("prog.toys", asmSrc) })
        val text = org.bytefred.ktoy.core.referenceTable()
        assertTrue(text.lines().all { it.length <= 56 }, text.lines().maxBy { it.length })
        assertTrue(text.lines().none { it.startsWith("  ") }, "no indentation")
        val before = onAllNodesWithTag("vscroll-thumb").fetchSemanticsNodes().size
        onNodeWithTag("menu-Help").performClick()
        onNodeWithText("Instruction set", substring = true).performClick()
        waitForIdle()
        onNodeWithText("the only conditional", substring = true).assertExists()
        assertEquals(before + 1, onAllNodesWithTag("vscroll-thumb").fetchSemanticsNodes().size, "the dialog has a scrollbar")
    }

    @Test
    fun theCMenuFollowsALoad() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        show(vm)
        vm.loadFromFile("prog.toyc", cSrc)
        waitForIdle()
        onNodeWithTag("menu-C").performClick()
        waitForIdle()
        onNodeWithText("Compile").assertExists()
    }

    @Test
    fun assembleInACProgramAsksThenDropsTheC() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyc", cSrc) }
        show(vm)
        onNodeWithText("C source").assertExists()

        menu("Assembly", "Assemble")
        onNodeWithText("Assemble?").assertExists()
        onNodeWithText("the C code", substring = true).assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertTrue(vm.hasCSource, "Cancel keeps the C")

        menu("Assembly", "Assemble")
        // the dialog's confirm button (the menu is closed by now)
        onNodeWithText("Assemble").performClick()
        waitForIdle()
        assertFalse(vm.hasCSource)
        onNodeWithText("C source").assertDoesNotExist()
    }

    @Test
    fun editCThenCompile() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyc", cSrc) }
        show(vm)
        menu("C", "Edit…")
        typeInEditDialog("int main(void){return 7;}\n")
        onNodeWithText("C source (modified)").assertExists()
        menu("C", "Compile")
        onNodeWithText("C source").assertExists()
        assertTrue("return 7" in vm.cText!!)
    }

    @Test
    fun compilerChoiceRecompiles() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyc", cSrc) }
        show(vm)
        assertEquals("toycc", vm.currentCompiler)
        menu("C", "Mini C (minicc)")
        assertEquals("minicc", vm.currentCompiler)
    }

    @Test
    fun compilerButtonInTheCPanelSwitches() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyc", cSrc) }
        show(vm)
        onNodeWithText("Switch to mini C").performClick()
        waitForIdle()
        assertEquals("minicc", vm.currentCompiler)
        onNodeWithText("Switch to full C").performClick()
        waitForIdle()
        assertEquals("toycc", vm.currentCompiler)
    }

    @Test
    fun disassembleBytecodeOpensTheAsmColumn() = runComposeUiTest {
        val vm = SimViewModel().apply {
            loadFromFile("prog.toyo", "     0   00010100  00000011\n     2   00000000\n     3   00101010\n")
        }
        show(vm)
        assertFalse(vm.hasSource)
        menu("Bytecode", "Disassemble")
        assertTrue(vm.hasSource)
        onNodeWithText("load  D3", substring = true).assertExists()
    }

    @Test
    fun cAndAssemblyMenusAreDisabledForBytecode() = runComposeUiTest {
        val vm = SimViewModel().apply {
            loadFromFile("prog.toyo", "     0   00000000\n")
        }
        show(vm)
        onNodeWithTag("menu-C").performClick()
        waitForIdle()
        onNodeWithText("Compile").assertDoesNotExist()
        onNodeWithTag("menu-Assembly").performClick()
        waitForIdle()
        onNodeWithText("Assemble").assertDoesNotExist()
    }

    @Test
    fun aSingleStopShowsStoppedAfterOneStep() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("stop.toys", "stop\n") }
        show(vm)
        onNodeWithText("Step").performClick()
        waitForIdle()
        onNodeWithText("Stopped after 1 steps.", substring = true).assertExists()
    }

    @Test
    fun aTooWideLineGetsADraggableHorizontalScrollbar() = runComposeUiTest {
        val long = "# " + "x".repeat(300)
        val vm = SimViewModel().apply { loadFromFile("prog.toys", "load x\nstop\n$long\nx: 42\n") }
        show(vm)
        val thumb = onAllNodesWithTag("hscroll-thumb").onFirst()
        val before = thumb.fetchSemanticsNode().boundsInRoot.left
        thumb.performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(10f, 0f)) }
            up()
        }
        waitForIdle()
        val after = thumb.fetchSemanticsNode().boundsInRoot.left
        assertTrue(after > before, "thumb should have moved right: $before -> $after")
    }

    @Test
    fun aNarrowScreenStacksThePanelsWithoutTabs() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toyc", cSrc) }
        show(vm, width = 400.dp)
        onNodeWithText("C source").assertExists()
        onNodeWithText("CPU & memory").assertExists()
        onAllNodesWithTag("divider-1").onFirst().assertExists()   // 3 stacked panels, 2 dividers
        val c = onNodeWithText("C source").fetchSemanticsNode().boundsInRoot
        val mem = onNodeWithText("CPU & memory").fetchSemanticsNode().boundsInRoot
        assertTrue(mem.top > c.bottom, "memory sits below C")
    }

    private fun ComposeUiTest.assertStepsFieldAsHighAsStep(width: Dp?) {
        show(SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }, width)
        // layout size, not the on-screen bounds: on a phone the run row scrolls
        // sideways and the field may be off-screen
        val field = onNodeWithTag("steps-per-sec").fetchSemanticsNode().size
        val step = onNodeWithText("Step").fetchSemanticsNode().size
        assertEquals(step.height, field.height)
    }

    @Test
    fun stepsPerSecFieldIsAsHighAsTheButtons() = runComposeUiTest { assertStepsFieldAsHighAsStep(null) }

    @Test
    fun stepsPerSecFieldIsAsHighAsTheCompactButtons() = runComposeUiTest { assertStepsFieldAsHighAsStep(400.dp) }

    @Test
    fun oneClickSwitchesFromOneMenuToAnother() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("prog.toys", asmSrc) })
        onNodeWithTag("menu-File").performClick()
        waitForIdle()
        onNodeWithText("Examples…").assertExists()
        onNodeWithTag("menu-View").performClick()   // one click, while File is open
        waitForIdle()
        onNodeWithText("Examples…").assertDoesNotExist()
        onNodeWithText("Line numbers", substring = true).assertExists()
        onNodeWithTag("menu-View").performClick()   // the open menu's title closes it
        waitForIdle()
        onNodeWithText("Line numbers", substring = true).assertDoesNotExist()
    }

    @Test
    fun backgroundIsNotTinted() = runComposeUiTest {
        var bg = Color.Unspecified
        var dialog = Color.Unspecified
        setContent {
            KtoyTheme {
                bg = MaterialTheme.colorScheme.background
                dialog = MaterialTheme.colorScheme.surfaceContainerHigh
            }
        }
        waitForIdle()
        assertEquals(Color.White, bg)
        assertTrue(dialog.red == dialog.green && dialog.green == dialog.blue, "dialog colour $dialog is not grey")
    }

    // goto over 80 nops, so the PC lands far below the first screenful; data
    // lines after it, so it is not the last line (which can't be centred)
    private val longAsm = "goto end\n" + "nop\n".repeat(80) + "end: stop\n" + "0\n".repeat(60)

    private fun ComposeUiTest.pcRowIn(panel: String) =
        onNode(hasAnyAncestor(hasTestTag("panel-$panel")) and hasText(">>", substring = true))

    /** The visible scrolling area of [panel] (below its header / status block). */
    private fun ComposeUiTest.viewportOf(panel: String) =
        onNode(hasTestTag("scroll-area") and hasAnyAncestor(hasTestTag("panel-$panel"))).fetchSemanticsNode().boundsInRoot

    /** The PC row's centre is within a row or two of the viewport's centre. */
    private fun ComposeUiTest.assertPcRowCentred(panel: String) {
        val row = pcRowIn(panel).fetchSemanticsNode().boundsInRoot
        val box = viewportOf(panel)
        val diff = kotlin.math.abs(row.center.y - box.center.y)
        assertTrue(diff < 2.5f * row.height, "$panel: PC row at ${row.center.y}, viewport ${box.top}..${box.bottom}")
    }

    @Test
    fun stepScrollsThePcLineToTheMiddleOfAsmAndMemory() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("long.toys", longAsm) }
        show(vm)
        onNodeWithText("Step").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(2000)
        waitForIdle()
        assertEquals(82, vm.machine!!.pc)   // goto (2 bytes) + 80 one-byte nops
        assertPcRowCentred("Assembly")
        assertPcRowCentred("CPU & memory")
    }

    @Test
    fun runningFollowsThePcInTheCColumn() = runComposeUiTest {
        // blank lines cost no bytes, so the return line sits far down the column
        val vm = SimViewModel().apply { loadFromFile("long.toyc", "int main(void){\n    int a = 5;\n" + "\n".repeat(80) + "    return a;\n" + "\n".repeat(60) + "}\n") }
        assertTrue(vm.hasCSource, vm.errorMessage ?: "")
        show(vm)
        val returnLine = 83
        repeat(50) { if (vm.pcCLine() != returnLine && !vm.machine!!.stopped) vm.step() }
        assertEquals(returnLine, vm.pcCLine(), "PC should be on the return line")
        waitForIdle()
        mainClock.advanceTimeBy(2000)
        waitForIdle()
        val row = onNode(hasAnyAncestor(hasTestTag("panel-C source")) and hasText("return a;", substring = true))
            .fetchSemanticsNode().boundsInRoot
        val box = viewportOf("C source")
        assertTrue(kotlin.math.abs(row.center.y - box.center.y) < 2.5f * row.height, "C row ${row.center.y} vs ${box.center.y}")
    }

    @Test
    fun autoScrollCanBeTurnedOff() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("long.toys", longAsm) }
        show(vm)
        assertTrue(vm.autoScroll, "on by default")
        try {
            menu("View", "Auto-scroll to PC")
            assertFalse(vm.autoScroll)
            onNodeWithText("Step").performClick()
            waitForIdle()
            mainClock.advanceTimeBy(2000)
            waitForIdle()
            pcRowIn("Assembly").assertIsNotDisplayed()
        } finally {
            savePref(AUTO_SCROLL_PREF, "true")
        }
    }

    @Test
    fun bottomBarOrderIsResetUndoStepRunAndNoEdit() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("prog.toys", asmSrc) })
        val x = listOf("Reset", "Undo", "Step", "Run").map { onNodeWithText(it).fetchSemanticsNode().boundsInRoot.left }
        assertEquals(x.sorted(), x, "left to right: Reset Undo Step Run")
        assertTrue(onNodeWithTag("steps-per-sec").fetchSemanticsNode().boundsInRoot.left > x.last())
        onNodeWithText("Edit").assertDoesNotExist()
    }

    @Test
    fun examplesDialogReopensAsItWasLeft() = runComposeUiTest {
        val state = ExamplesTreeState()
        var open by mutableStateOf(true)
        setContent {
            KtoyTheme { if (open) ExamplesDialog(state, onLoad = { open = false }, onDismiss = { open = false }) }
        }
        onNodeWithText("03-programs").assertDoesNotExist()        // starts with the groups only
        onNodeWithText("Assembly").performClick()
        onNodeWithText("03-programs").performClick()
        onNodeWithText("fibonacci_iter").performClick()
        onNodeWithText("Load").performClick()
        waitForIdle()
        assertFalse(open)

        open = true
        waitForIdle()
        onNodeWithText("fibonacci_iter").assertExists().assertIsSelected()
        onNodeWithText("02-extended").assertExists()               // Assembly still open
    }
}
