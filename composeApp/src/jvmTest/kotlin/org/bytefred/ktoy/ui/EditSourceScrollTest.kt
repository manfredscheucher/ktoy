package org.bytefred.ktoy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The multiline edit dialogs scroll with a visible scrollbar. */
@OptIn(ExperimentalTestApi::class)
class EditSourceScrollTest {
    private val lines256 = (0 until 256).joinToString("\n") { (it % 256).toString() }
    private var saved: String? = null

    private fun ComposeUiTest.dialog(text: String, check: (String) -> String? = { null }) {
        setContent {
            MaterialTheme {
                EditSourceDialog("Edit bytecode", "One value per line", text, firstLineNumber = 0, check = check, onSave = { saved = it }, onDismiss = {})
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.thumbTop() = onNodeWithTag("vscroll-thumb").getUnclippedBoundsInRoot().top.value

    @Test
    fun aLongTextShowsTheScrollbar() = runComposeUiTest {
        dialog(lines256)
        onNodeWithTag("vscroll-thumb").assertIsDisplayed()
    }

    @Test
    fun onAPhoneToo() = runDesktopComposeUiTest(360, 640) {
        dialog(lines256)
        onNodeWithTag("vscroll-thumb").assertIsDisplayed()
        val bottom = onNodeWithTag("vscroll-thumb").getUnclippedBoundsInRoot().bottom.value
        assertTrue(bottom <= 640, "thumb ends at $bottom")
    }

    @Test
    fun aShortTextHasNone() = runComposeUiTest {
        dialog("1\n2\n3")
        onNodeWithTag("vscroll-thumb").assertDoesNotExist()
    }

    @Test
    fun theMouseWheelMovesTheThumb() = runComposeUiTest {
        dialog(lines256)
        val start = thumbTop()
        onNodeWithTag("edit-source").performMouseInput { moveTo(center); scroll(20f) }
        waitForIdle()
        assertTrue(thumbTop() > start, "thumb stayed at $start")
    }

    @Test
    fun theCursorStaysInViewWithTheArrowKeys() = runComposeUiTest {
        dialog(lines256)
        onNodeWithTag("edit-source").performClick()
        waitForIdle()
        val start = thumbTop()
        onNodeWithTag("edit-source").performKeyInput { repeat(80) { pressKey(Key.DirectionDown) } }
        waitForIdle()
        assertTrue(thumbTop() > start, "thumb stayed at $start after 80 x Down")
    }

    @Test
    fun theCursorGoesBackUpWithTheArrowKeys() = runComposeUiTest {
        dialog(lines256)
        onNodeWithTag("edit-source").performClick()
        waitForIdle()
        onNodeWithTag("edit-source").performKeyInput { repeat(80) { pressKey(Key.DirectionDown) } }
        waitForIdle()
        val low = thumbTop()
        onNodeWithTag("edit-source").performKeyInput { repeat(79) { pressKey(Key.DirectionUp) } }
        waitForIdle()
        assertTrue(thumbTop() < low, "thumb stayed at $low after going up")
    }

    @Test
    fun theCursorStaysInViewOnALongLine() = runComposeUiTest {
        dialog("load x # " + "a long comment ".repeat(20) + "\nstop")
        onNodeWithTag("edit-source").performClick()
        waitForIdle()
        onNodeWithTag("edit-source").performTextInputSelection(TextRange(0))
        waitForIdle()
        val start = onNodeWithTag("hscroll-thumb").getUnclippedBoundsInRoot().left.value
        onNodeWithTag("edit-source").performKeyInput { repeat(150) { pressKey(Key.DirectionRight) } }
        waitForIdle()
        val moved = onNodeWithTag("hscroll-thumb").getUnclippedBoundsInRoot().left.value
        assertTrue(moved > start, "thumb stayed at $start")
    }

    @Test
    fun scrollingWithTheWheelIsNotPulledBackToTheCursor() = runComposeUiTest {
        dialog(lines256)
        onNodeWithTag("edit-source").performClick()
        waitForIdle()
        onNodeWithTag("edit-source").performMouseInput { moveTo(center); scroll(40f) }
        waitForIdle()
        val scrolled = thumbTop()
        mainClock.advanceTimeBy(500)
        waitForIdle()
        assertEquals(scrolled, thumbTop())
    }

    @Test
    fun theCursorStaysInViewWhenTypingNewLines() = runComposeUiTest {
        dialog("1\n2")
        onNodeWithTag("edit-source").performClick()
        waitForIdle()
        onNodeWithTag("edit-source").performKeyInput { repeat(60) { pressKey(Key.Enter) } }
        waitForIdle()
        val start = onNodeWithTag("vscroll-thumb").getUnclippedBoundsInRoot()
        assertTrue(start.top.value > onNodeWithText("Edit bytecode").getUnclippedBoundsInRoot().bottom.value)
        assertTrue(thumbTop() > 100, "thumb at ${thumbTop()}: not following the cursor")
    }

    @Test
    fun theErrorIsInViewWithoutScrolling() = runComposeUiTest {
        dialog(lines256, check = { "at most 256 lines" })
        onNodeWithText("Save").performClick()
        waitForIdle()
        onNodeWithText("at most 256 lines").assertIsDisplayed()
        onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun aLongLineShowsTheHorizontalScrollbarAndDoesNotWrap() = runComposeUiTest {
        val long = "load x # " + "a long comment ".repeat(20)
        dialog("$long\nstop")
        onNodeWithTag("hscroll-thumb").assertIsDisplayed()
        // unwrapped, the line makes the field wider than the dialog
        val field = onNodeWithTag("edit-source").getUnclippedBoundsInRoot()
        val dialog = onNodeWithTag("big-dialog").getUnclippedBoundsInRoot()
        assertTrue(field.right - field.left > dialog.right - dialog.left, "wrapped: $field in $dialog")
    }

    @Test
    fun shortLinesHaveNoHorizontalScrollbar() = runComposeUiTest {
        dialog(lines256)
        onNodeWithTag("hscroll-thumb").assertDoesNotExist()
    }

    @Test
    fun saveAndBuildChecksFirst() = runComposeUiTest {
        var built = false
        setContent {
            MaterialTheme {
                EditSourceDialog("t", "l", "x", buildLabel = "Disassemble", check = { "bad value" },
                    onSave = { saved = it }, onBuild = { built = true }, onDismiss = {})
            }
        }
        waitForIdle()
        onNodeWithText("Save & Disassemble").performClick()
        waitForIdle()
        onNodeWithText("bad value").assertIsDisplayed()
        assertEquals(null, saved)
        assertTrue(!built)
    }

    @Test
    fun theMouseWheelDoesNotMoveALongTextSideways() = runComposeUiTest {
        dialog("load x # " + "a long comment ".repeat(20) + "\n" + lines256)
        val startX = onNodeWithTag("hscroll-thumb").getUnclippedBoundsInRoot().left
        val startY = thumbTop()
        onNodeWithTag("edit-source").performMouseInput { moveTo(center); scroll(20f) }
        waitForIdle()
        assertTrue(thumbTop() > startY, "no vertical scroll")
        assertEquals(startX, onNodeWithTag("hscroll-thumb").getUnclippedBoundsInRoot().left)
    }

    @Test
    fun itOpensAtTheTopLeft() = runComposeUiTest {
        dialog("load x # " + "a long comment ".repeat(20) + "\n" + lines256)
        val area = onNodeWithTag("scroll-area").getUnclippedBoundsInRoot()
        assertEquals(area.left, onNodeWithTag("hscroll-thumb").getUnclippedBoundsInRoot().left)
        assertEquals(area.top, onNodeWithTag("vscroll-thumb").getUnclippedBoundsInRoot().top)
    }

    @Test
    fun theErrorIsInViewInAShortWindow() = runDesktopComposeUiTest(640, 360) {
        dialog(lines256, check = { "at most 256 lines" })
        onNodeWithText("Save").performClick()
        waitForIdle()
        onNodeWithText("at most 256 lines").assertIsDisplayed()
        val error = onNodeWithText("at most 256 lines").getUnclippedBoundsInRoot()
        assertTrue(error.bottom.value - error.top.value > 5, "error has no height")
    }

    @Test
    fun theCursorStaysInViewAfterPastingAtTheEnd() = runComposeUiTest {
        dialog(lines256)
        onNodeWithTag("edit-source").performClick()
        onNodeWithTag("edit-source").performTextInputSelection(TextRange(lines256.length))
        waitForIdle()
        val before = thumbTop()
        onNodeWithTag("edit-source").performTextInput("\n1".repeat(100))
        waitForIdle()
        val area = onNodeWithTag("scroll-area").getUnclippedBoundsInRoot()
        val thumb = onNodeWithTag("vscroll-thumb").getUnclippedBoundsInRoot()
        assertEquals(area.bottom.value, thumb.bottom.value, 2f, "thumb at the end after a paste (was $before)")
    }

    @Test
    fun saveKeepsTheText() = runComposeUiTest {
        dialog(lines256)
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals(lines256, saved)
    }
}
