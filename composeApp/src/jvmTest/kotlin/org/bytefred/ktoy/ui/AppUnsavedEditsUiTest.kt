package org.bytefred.ktoy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Save changes?" in the whole app: closing the window and loading an example. */
@OptIn(ExperimentalTestApi::class)
class AppUnsavedEditsUiTest {

    private fun ComposeUiTest.dismissWelcome() {
        waitForIdle()
        if (onAllNodes(hasText("Welcome to ktoy")).fetchSemanticsNodes().isNotEmpty())
            onNodeWithText("Start").performClick()
    }

    private fun ComposeUiTest.editTheAsm() {
        onNodeWithTag("menu-Assembly").performClick()
        onNodeWithText("Edit…").performClick()
        onNodeWithTag("edit-source").performTextReplacement("stop\n# edited\n")
        onNodeWithText("Save").performClick()
        waitForIdle()
    }

    @Test
    fun closingWithoutEditsJustCloses() = runComposeUiTest {
        var closes by mutableIntStateOf(0)
        var closed = 0
        setContent { App(closes, onClose = { closed++ }) }
        dismissWelcome()
        closes++
        waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun closingWithEditsAsksFirst() = runComposeUiTest {
        var closes by mutableIntStateOf(0)
        var closed = 0
        setContent { App(closes, onClose = { closed++ }) }
        dismissWelcome()
        editTheAsm()

        closes++
        waitForIdle()
        onNodeWithText("Save changes?").assertExists()
        assertEquals(0, closed)
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals(0, closed, "Cancel keeps the window open")

        closes++
        waitForIdle()
        onNodeWithText("Discard").performClick()
        waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun examplesAskBeforeReplacingAnEditedProgram() = runComposeUiTest {
        setContent { App() }
        dismissWelcome()
        editTheAsm()
        onNodeWithText("Assembly (modified)").assertExists()

        onNodeWithText("Examples").performClick()
        waitForIdle()
        // the dialog's nodes come after the screen's (same text as a menu / button)
        onAllNodesWithText("Bytecode").onLast().performClick()
        waitForIdle()
        onNodeWithText("invalid_opcode", substring = true).performClick()
        onAllNodesWithText("Load").onLast().performClick()
        waitForIdle()
        onNodeWithText("Save changes?").assertExists()
        onNodeWithText("Assembly (modified)").assertExists()
        onNodeWithText("Discard").performClick()
        waitForIdle()
        onNodeWithText("Assembly (modified)").assertDoesNotExist()
    }
}
