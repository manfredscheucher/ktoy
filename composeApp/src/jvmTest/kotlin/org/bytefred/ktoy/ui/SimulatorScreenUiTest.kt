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
import androidx.compose.ui.test.assertIsNotDisplayed
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
        assertEquals(null, vm.selectedAddr, "a right-click does not select")
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
        onNodeWithTag("context-edit").performClick()
        waitForIdle()
        assertEquals(3, vm.memSelectedAddr)
        onNodeWithText("address 3", substring = true).assertExists()
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
    fun saveAsPreselectsTheHighestLevel() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("prog.toyc", cSrc) })
        menu("File", "Save…")
        onNodeWithText("Save as").assertExists()
        onNode(hasText("C source (.toyc)") and isSelectable()).assertIsSelected()
        onNode(hasText("Assembly (.toys)") and isSelectable()).assertIsNotSelected()
        onNodeWithText("Cancel").performClick()
    }

    @Test
    fun saveInTheQuestionOpensTheFormatChoice() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("prog.toys", asmSrc) }
        vm.saveAsmDraft("stop\n")
        show(vm)
        onNodeWithText("Reload").performClick()
        onNodeWithText("Save…").performClick()
        waitForIdle()
        onNode(hasText("Assembly (.toys)") and isSelectable()).assertIsSelected()
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
        onNode(hasText("Assembly (.toys)") and isSelectable()).performClick()
        onNodeWithText("Save").performClick()
        waitForIdle()
        onNodeWithText("This file lacks your edits").assertExists()
        onNodeWithText("not compiled yet", substring = true).assertExists()
        onNodeWithText("Back").performClick()
        waitForIdle()
        onNodeWithText("This file lacks your edits").assertDoesNotExist()
        onNodeWithText("Save as").assertExists()
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
        onNodeWithText("C column", substring = true).assertExists()
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
        onNodeWithText("load   D3", substring = true).assertExists()
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
        menu("View", "Auto-scroll to PC")
        assertFalse(vm.autoScroll)
        onNodeWithText("Step").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(2000)
        waitForIdle()
        pcRowIn("Assembly").assertIsNotDisplayed()
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
            KtoyTheme { if (open) ExamplesDialog(state, running = false, onLoad = { open = false }, onDismiss = { open = false }) }
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

    @Test
    fun invalidOpcodeAskDialogAndStopStatus() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("bad.toys", "load x\n3\nadd x\nstop\nx: 21\n") }
        show(vm)
        menu("Execution", "Always ask")
        onNodeWithText("Step").performClick()
        onNodeWithText("Step").performClick()
        waitForIdle()
        onNodeWithText("Invalid opcode").assertExists()
        onNodeWithText("Remember this decision").performClick()
        onNodeWithText("Stop").performClick()
        waitForIdle()
        assertEquals(InvalidOpcodeMode.STOP, vm.invalidOpcodeMode)
        // in the status line (no extra error dialog: the user chose Stop)
        onAllNodesWithText("Error in step 2: invalid opcode 3 at address 2.", substring = true).assertCountEquals(1)
    }

    @Test
    fun askDuringRunPausesAndNopResumes() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("bad.toys", "load x\n3\nadd x\nstop\nx: 21\n") }
        vm.invalidOpcodeMode = InvalidOpcodeMode.ASK
        vm.stepsPerSecond = 1000.0
        show(vm)
        onNodeWithText("Run").performClick()
        waitUntil(timeoutMillis = 5000) { vm.invalidOpcodeQuestion != null }
        assertFalse(vm.running, "paused for the question")
        onNodeWithText("NOP").performClick()
        waitUntil(timeoutMillis = 5000) { vm.machine!!.stopped }
        assertEquals(42, vm.machine!!.acc)
        assertEquals(4, vm.machine!!.stepCount)
        onNodeWithText("Invalid opcode").assertDoesNotExist()
    }

    @Test
    fun choosingStopInTheQuestionShowsNoSecondDialog() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("bad.toys", "load x\n3\nadd x\nstop\nx: 21\n") }
        vm.invalidOpcodeMode = InvalidOpcodeMode.ASK
        show(vm)
        onNodeWithText("Step").performClick()
        onNodeWithText("Step").performClick()
        onNodeWithText("Stop").performClick()
        waitForIdle()
        // only the status line, no error dialog on top
        onAllNodesWithText("Error in step 2", substring = true).assertCountEquals(1)
    }

    @Test
    fun stopModeShowsTheErrorDialogAndExplainsIt() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("bad.toys", "load x\n3\nadd x\nstop\nx: 21\n") }
        vm.invalidOpcodeMode = InvalidOpcodeMode.STOP
        show(vm)
        onNodeWithText("Step").performClick()
        waitForIdle()
        onNodeWithText("invalid: stops", substring = true).assertExists()   // EXPLAIN before the bad byte
        onNodeWithText("Step").performClick()
        waitForIdle()
        // status line + error dialog
        onAllNodesWithText("Error in step 2: invalid opcode 3 at address 2.", substring = true).assertCountEquals(2)
    }
}
