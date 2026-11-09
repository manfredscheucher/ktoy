package org.bytefred.ktoy.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.bytefred.ktoy.core.ExampleKind

/** File -> Empty, the Help pages, the save dialog on a phone, the big dialogs, the auto-build toggles. */
@OptIn(ExperimentalTestApi::class)
class MenuAndDialogsTest {
    private val asmSrc = "        load  x\n        stop\nx:      42\n"

    private fun ComposeUiTest.show(vm: SimViewModel) {
        setContent { KtoyTheme { ProvideMonoStyle { Surface { SimulatorScreen(vm, onLoad = {}) } } } }
        if (onAllNodes(hasText("Welcome to ktoy")).fetchSemanticsNodes().isNotEmpty()) onNodeWithText("Start").performClick()
        waitForIdle()
    }

    // a menu title ignores a click within 500 ms of its menu closing by an outside click (MenuButton)
    private fun ComposeUiTest.openMenu(title: String) {
        Thread.sleep(600)
        onNodeWithTag("menu-$title").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.empty(label: String) {
        onNodeWithTag("menu-File").performClick()
        onNodeWithTag("menu-empty").performClick()
        onNodeWithTag("empty-$label").performClick()
        waitForIdle()
    }

    @Test
    fun emptyFoldsOutThreeEntries() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("p.toys", asmSrc) })
        onNodeWithTag("menu-File").performClick()
        onNodeWithTag("empty-C").assertDoesNotExist()
        onNodeWithTag("menu-empty").performClick()
        for (l in listOf("C", "Assembly", "Bytecode")) onNodeWithTag("empty-$l").assertExists()
    }

    @Test
    fun emptyCIsAMainThatReturnsZero() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("p.toys", asmSrc) }
        show(vm)
        empty("C")
        assertTrue(vm.hasCSource)
        assertEquals("int main(void) {\n    return 0;\n}\n", vm.cText)
        val m = vm.machine!!
        while (!m.stopped) m.step()
        assertEquals(0, m.acc)
        assertEquals("empty.toyc", vm.title)
    }

    @Test
    fun emptyAssemblyIsAStop() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("p.toyc", "int main(void){return 4;}\n") }
        show(vm)
        empty("Assembly")
        assertTrue(!vm.hasCSource)
        assertEquals("        stop\n", vm.asmText)
        assertTrue(vm.machine!!.mem.all { it == 0 })
    }

    @Test
    fun emptyBytecodeIsAllZeros() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("p.toys", asmSrc) }
        show(vm)
        empty("Bytecode")
        assertEquals(null, vm.asmText)
        assertTrue(vm.machine!!.mem.all { it == 0 })
        assertTrue(!vm.unsavedEdits)
    }

    @Test
    fun emptyAsksBeforeItDropsEdits() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("p.toys", asmSrc) }
        vm.saveAsmDraft("stop\n")
        show(vm)
        empty("C")
        onNodeWithText("Save changes?").assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals("stop\n", vm.asmDraft)
    }

    @Test
    fun helpHasGeneralInstructionSetCCompilersAbout() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("p.toys", asmSrc) })
        openMenu("Help")
        val tops = listOf("General", "Instruction set", "C compilers", "About ktoy")
            .map { onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        openMenu("Help")   // closes it
        waitForIdle()
        onNodeWithText("Edit and build").assertDoesNotExist()
        onNodeWithText("shortcuts", substring = true).assertDoesNotExist()
        for ((page, inside) in listOf("General" to "I/O panel", "Instruction set" to "the only conditional", "C compilers" to "Mini C (minicc)")) {
            openMenu("Help")
            onNodeWithText(page).performClick()
            waitForIdle()
            onNodeWithText(inside, substring = true).assertExists()
            onNodeWithText("OK").performClick()
            waitForIdle()
        }
    }

    @Test
    fun theCHelpPutsMiniCFirst() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("p.toys", asmSrc) })
        onNodeWithTag("menu-Help").performClick()
        onNodeWithText("C compilers").performClick()
        waitForIdle()
        val mini = onNodeWithText("Mini C (minicc)", substring = true).fetchSemanticsNode().boundsInRoot.top
        val full = onNodeWithText("Full C (toycc) extends mini C", substring = true).fetchSemanticsNode().boundsInRoot.top
        assertTrue(mini < full)
    }

    // the dialog fills the window but its 16 dp margin, on a desktop and on a phone
    private fun bigDialogFills(w: Int, h: Int, open: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(w, h) {
        show(SimViewModel().apply { loadFromFile("p.toys", asmSrc) })
        open()
        waitForIdle()
        val b = onNodeWithTag("big-dialog").fetchSemanticsNode().boundsInRoot
        assertTrue(b.width >= w - 2 * 16 - 1 && b.height >= h - 2 * 16 - 1, "$b in ${w}x$h")
    }

    @Test
    fun helpFillsTheWindow() {
        for ((w, h) in listOf(1280 to 800, 360 to 640)) bigDialogFills(w, h) {
            onNodeWithTag("menu-Help").performClick()
            onNodeWithText("General").performClick()
        }
    }

    @Test
    fun theEditorFillsTheWindow() {
        for ((w, h) in listOf(1280 to 800, 360 to 640)) bigDialogFills(w, h) {
            onNodeWithTag("menu-Assembly").performClick()
            onNodeWithText("Edit…").performClick()
        }
    }

    @Test
    fun theExamplesFillTheWindow() = runDesktopComposeUiTest(1280, 800) {
        setContent { KtoyTheme { ProvideMonoStyle { ExamplesDialog(ExamplesTreeState(), onLoad = {}, onDismiss = {}) } } }
        waitForIdle()
        val b = onNodeWithTag("big-dialog").fetchSemanticsNode().boundsInRoot
        assertTrue(b.width >= 1280 - 33 && b.height >= 800 - 33, "$b")
        onNodeWithText("Load").assertExists()
    }

    @Test
    fun theSaveDialogFitsAPhone() = runDesktopComposeUiTest(360, 640) {
        val vm = SimViewModel().apply { loadFromFile("p.toyc", "int main(void){return 4;}\n") }
        vm.saveCDraft("int main(void){return 5;}\n")
        show(vm)
        vm.leave { }
        waitForIdle()
        for (l in listOf("Save C", "Save assembly", "Save bytecode", "Discard", "Cancel")) {
            val b = onNodeWithText(l).fetchSemanticsNode().boundsInRoot
            assertTrue(b.left >= 0f && b.right <= 360f, "$l: $b")
        }
    }

    @Test
    fun theTogglesSitInTheirMenusAndOnlyOneIsOn() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("p.toyc", "int main(void){return 4;}\n") }
        show(vm)
        openMenu("C")
        onNodeWithText("Auto compile on edit", substring = true).performClick()
        waitForIdle()
        assertEquals(SimViewModel.AutoBuild.COMPILE, vm.autoBuild)
        openMenu("Assembly")
        onNodeWithText("Auto assemble on edit", substring = true).performClick()
        waitForIdle()
        assertEquals(SimViewModel.AutoBuild.ASSEMBLE, vm.autoBuild)
        openMenu("Bytecode")
        onNodeWithText("Auto disassemble on edit", substring = true).performClick()
        waitForIdle()
        assertEquals(SimViewModel.AutoBuild.DISASSEMBLE, vm.autoBuild)
        openMenu("Assembly")
        onNodeWithText("Auto assemble on edit", substring = true).assertExists()
        onNodeWithText("✓ Auto assemble on edit").assertDoesNotExist()
    }

    @Test
    fun atTheStartTheMemoryShowsItsHeaderAndAddress0() = runDesktopComposeUiTest(1280, 800) {
        show(SimViewModel().apply { loadFromFile("p.toyc", "int main(void) {\n    int x = 5;\n    return x + 2;\n}\n") })
        val explain = onNodeWithText("EXPLAIN", substring = true).fetchSemanticsNode().boundsInRoot
        val header = onNodeWithText("addr (binary)", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(header.top >= explain.bottom - 1, "header $header under $explain")
    }
}
