package org.bytefred.ktoy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The cell editor's two views. Saves and restores the real preference around each test. */
@OptIn(ExperimentalTestApi::class)
class EditValueDialogTest {
    private var savedPref: String? = null

    @BeforeTest
    fun rememberPref() {
        savedPref = loadPref(EDIT_VIEW_PREF)
        savePref(EDIT_VIEW_PREF, "select")
    }

    @AfterTest
    fun restorePref() = savePref(EDIT_VIEW_PREF, savedPref ?: "select")

    private var picked: Int? = null
    private var dismissed = false

    private fun ComposeUiTest.dialog() {
        setContent {
            MaterialTheme {
                EditValueDialog(3, 7, onDismiss = { dismissed = true }, onConfirm = { picked = it })
            }
        }
        waitForIdle()
    }

    @Test
    fun selectIsTheDefaultWithoutATextField() = runComposeUiTest {
        savePref(EDIT_VIEW_PREF, "")
        dialog()
        onNodeWithTag("pick-op-20").assertExists()
        onNodeWithTag("pick-val-255").assertExists()
        assertEquals(0, onAllTag("edit-value"))
        assertEquals(0, onAllText("Save"))
    }

    @Test
    fun allOperationsAreLabelledWithTheirCodeInOpcodeOrder() = runComposeUiTest {
        dialog()
        val ops = listOf(
            "STOP(0)" to 0, "RIGHT(1)" to 1, "LEFT(2)" to 2, "NOT(15)" to 15, "AND(17)" to 17,
            "OR(18)" to 18, "XOR(19)" to 19, "LOAD(20)" to 20, "STORE(21)" to 21, "ADD(22)" to 22,
            "SUB(23)" to 23, "GOTO(24)" to 24, "IFZERO(25)" to 25, "NOP(128)" to 128,
        )
        for ((label, _) in ops) onNodeWithText(label).assertExists()
        var last = -1f to -1f
        for ((_, op) in ops) {
            val b = onNodeWithTag("pick-op-$op").getUnclippedBoundsInRoot()
            val here = b.top.value to b.left.value
            assertTrue(here.first > last.first || (here.first == last.first && here.second > last.second), "op $op")
            last = here
        }
    }

    @Test
    fun allValuesAreAGridOf16By16InOrder() = runComposeUiTest {
        dialog()
        for (v in 0..255) {
            val b = onNodeWithTag("pick-val-$v").getUnclippedBoundsInRoot()
            val first = onNodeWithTag("pick-val-${v / 16 * 16}").getUnclippedBoundsInRoot()
            val col0 = onNodeWithTag("pick-val-${v % 16}").getUnclippedBoundsInRoot()
            assertEquals(first.top, b.top, "value $v row")
            assertEquals(col0.left, b.left, "value $v column")
            if (v % 16 != 0) assertTrue(b.left > onNodeWithTag("pick-val-${v - 1}").getUnclippedBoundsInRoot().left)
            if (v >= 16) assertTrue(b.top > onNodeWithTag("pick-val-${v - 16}").getUnclippedBoundsInRoot().top)
        }
        onNodeWithText("255").assertExists()
    }

    @Test
    fun clickingAValueWritesItAtOnce() = runComposeUiTest {
        dialog()
        onNodeWithTag("pick-val-200").performClick()
        waitForIdle()
        assertEquals(200, picked)
    }

    @Test
    fun clickingAnOperationWritesItsCodeAtOnce() = runComposeUiTest {
        dialog()
        onNodeWithTag("pick-op-21").performClick()
        waitForIdle()
        assertEquals(21, picked)
    }

    @Test
    fun cancelWritesNothing() = runComposeUiTest {
        dialog()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertTrue(dismissed)
        assertNull(picked)
    }

    @Test
    fun theViewIsRemembered() = runComposeUiTest {
        dialog()
        onNodeWithTag("edit-view-type").performClick()
        waitForIdle()
        assertEquals("type", loadPref(EDIT_VIEW_PREF))
        onNodeWithTag("edit-value").assertExists()
        assertEquals(0, onAllTag("pick-val-0"))
        onNodeWithTag("edit-view-select").performClick()
        waitForIdle()
        assertEquals("select", loadPref(EDIT_VIEW_PREF))
        onNodeWithTag("pick-val-0").assertExists()
        assertEquals(0, onAllTag("edit-value"))
    }

    @Test
    fun aRememberedTypeViewOpensWithTheField() = runComposeUiTest {
        savePref(EDIT_VIEW_PREF, "type")
        dialog()
        onNodeWithTag("edit-value").assertExists()
        assertEquals(0, onAllTag("pick-val-0"))
    }

    @Test
    fun typeViewStillSavesAndWarnsAboutWrapping() = runComposeUiTest {
        savePref(EDIT_VIEW_PREF, "type")
        dialog()
        onNodeWithTag("edit-value").performTextReplacement("nonsense")
        onNodeWithText("Save").performClick()
        waitForIdle()
        onNodeWithText("Could not parse", substring = true).assertExists()
        assertNull(picked)
        onNodeWithTag("edit-value").performTextReplacement("300")
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertNull(picked)
        onNodeWithText("Save anyway").performClick()
        waitForIdle()
        assertEquals(44, picked)
    }

    @Test
    fun onAPhoneTheValuesAre8PerRowAndEverythingFits() = runDesktopComposeUiTest(360, 640) {
        dialog()
        for (v in 0..255) {
            val b = onNodeWithTag("pick-val-$v").getUnclippedBoundsInRoot()
            assertEquals(onNodeWithTag("pick-val-${v / 8 * 8}").getUnclippedBoundsInRoot().top, b.top, "value $v row")
            assertEquals(onNodeWithTag("pick-val-${v % 8}").getUnclippedBoundsInRoot().left, b.left, "value $v column")
            assertTrue(b.width.value >= 28 && b.height.value >= 32, "tap target of $v: ${b.width} x ${b.height}")
        }
        for (op in listOf(0, 1, 2, 15, 17, 18, 19, 20, 21, 22, 23, 24, 25, 128)) {
            val b = onNodeWithTag("pick-op-$op").getUnclippedBoundsInRoot()
            assertTrue(b.left.value >= 0 && b.right.value <= 360, "op $op: ${b.left}..${b.right}")
        }
        for (v in 0..255) {
            val b = onNodeWithTag("pick-val-$v").getUnclippedBoundsInRoot()
            assertTrue(b.left.value >= 0 && b.right.value <= 360, "value $v: ${b.left}..${b.right}")
        }
        onNodeWithTag("pick-val-255").performScrollTo().assertIsDisplayed()
        onNodeWithTag("pick-val-255").performClick()
        waitForIdle()
        assertEquals(255, picked)
    }

    @Test
    fun aSmallPhoneStillFits() = runDesktopComposeUiTest(320, 568) {
        dialog()
        for (v in listOf(0, 7, 8, 255)) {
            val b = onNodeWithTag("pick-val-$v").getUnclippedBoundsInRoot()
            assertTrue(b.left.value >= 0 && b.right.value <= 320, "value $v: ${b.left}..${b.right}")
        }
        onNodeWithTag("pick-val-255").performScrollTo().performClick()
        waitForIdle()
        assertEquals(255, picked)
    }

    @Test
    fun aLandscapePhoneScrollsToTheLastRowAndCancel() = runDesktopComposeUiTest(800, 360) {
        dialog()
        onNodeWithTag("pick-val-255").performScrollTo().assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun theLabelsFitTheirButtons() = runDesktopComposeUiTest(360, 640) {
        dialog()
        val cell = onNodeWithTag("pick-val-255").getUnclippedBoundsInRoot()
        val text = onNode(androidx.compose.ui.test.hasText("255"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(text.width <= cell.width && text.height <= cell.height, "$text in $cell")
    }

    private fun ComposeUiTest.onAllTag(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    private fun ComposeUiTest.onAllText(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
