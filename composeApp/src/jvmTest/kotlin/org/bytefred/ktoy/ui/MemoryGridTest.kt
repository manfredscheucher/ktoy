package org.bytefred.ktoy.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** View -> Memory: the 16 / 8 column grids, and the status block's OP= / ARG= lines. */
@OptIn(ExperimentalTestApi::class)
class MemoryGridTest {
    private val asmSrc = "        load  x\n        stop\nx:      42\n"

    @BeforeTest
    @AfterTest
    fun emptyStore() = clearInMemoryPrefs()

    private fun ComposeUiTest.show(vm: SimViewModel) {
        setContent { KtoyTheme { ProvideMonoStyle { Surface { SimulatorScreen(vm, onLoad = {}) } } } }
        if (onAllNodes(hasText("Welcome to ktoy")).fetchSemanticsNodes().isNotEmpty()) onNodeWithText("Start").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.pick(menu: String, item: String) {
        if (onAllNodes(hasText(item, substring = true)).fetchSemanticsNodes().isEmpty()) {
            Thread.sleep(600)
            onNodeWithTag("menu-$menu").performClick()
            waitForIdle()
        }
        onNodeWithText(item, substring = true).performClick()
        waitForIdle()
    }

    @Test
    fun gridRowsAreDecimalValuesWithAStarAtThePc() {
        val mem = IntArray(256).also { it[0] = 20; it[1] = 3; it[3] = 42 }
        val rows = memoryGridRows(mem, pc = 0, columns = 16)
        assertEquals(16, rows.size)
        // no addresses: the row is only 16 cells of 3 digits plus the marker
        assertEquals(16 * 4, rows[0].length)
        assertEquals(" 20*  3   0  42 ", rows[0].substring(0, 16))
        assertEquals(32, memoryGridRows(mem, pc = 0, columns = 8).size)
        assertEquals("  0*", memoryGridCell(0, isPc = true))
        assertEquals("255 ", memoryGridCell(255, isPc = false))
    }

    @Test
    fun singleColumnStaysTheDefault() {
        assertEquals(MemoryView.SINGLE_COLUMN, SimViewModel().memoryView)
    }

    @Test
    fun theGridShowsValuesAndThePcStarAndIsRemembered() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("p.toys", asmSrc) })
        onNodeWithText("addr (binary)", substring = true).assertExists()
        pick("View", "16 columns")
        onNodeWithText("addr (binary)", substring = true).assertDoesNotExist()
        assertEquals(16, onAllNodes(hasTestTag("mem-grid-row")).fetchSemanticsNodes().size)
        // the PC (address 0) holds the load opcode 20, marked with *
        onNodeWithText(" 20*").assertExists()
        assertEquals(MemoryView.COLUMNS_16, SimViewModel().memoryView)
        pick("View", "8 columns")
        assertEquals(32, onAllNodes(hasTestTag("mem-grid-row")).fetchSemanticsNodes().size)
        assertEquals(MemoryView.COLUMNS_8, SimViewModel().memoryView)
    }

    @Test
    fun tappingAGridCellSelectsThatAddress() = runComposeUiTest {
        val vm = SimViewModel().apply { loadFromFile("p.toys", asmSrc); memoryView = MemoryView.COLUMNS_16 }
        show(vm)
        onNodeWithText(" 42 ").performClick()
        waitForIdle()
        assertEquals(3, vm.selectedAddr)
    }

    @Test
    fun theStatusBlockShowsOpAndArgInDecimalAndBinary() = runComposeUiTest {
        show(SimViewModel().apply { loadFromFile("p.toys", asmSrc) })
        onNodeWithText("  OP=20=b00010100").assertExists()
        onNodeWithText("  ARG=3=b00000011").assertExists()
        assertTrue(onAllNodes(hasText("OP2=", substring = true)).fetchSemanticsNodes().isEmpty())
    }
}
