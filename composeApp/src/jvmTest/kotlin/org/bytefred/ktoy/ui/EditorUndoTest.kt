package org.bytefred.ktoy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import kotlin.test.Test

/** Ctrl+Z (Cmd+Z on a Mac) takes typing back in the source editor. */
@OptIn(ExperimentalTestApi::class)
class EditorUndoTest {
    private val mac = System.getProperty("os.name").lowercase().contains("mac")
    private val mod = if (mac) Key.MetaLeft else Key.CtrlLeft

    @Test
    fun undoTakesTypingBack() = runComposeUiTest {
        setContent {
            MaterialTheme {
                EditSourceDialog(title = "Edit", label = "Assembly", initial = "load x", onSave = {}, onDismiss = {})
            }
        }
        val f = onNodeWithTag("edit-source")
        f.performClick()
        f.performTextInput("ZZZ")
        f.assertTextContains("ZZZ", substring = true)
        f.performKeyInput { withKeyDown(mod) { pressKey(Key.Z) } }
        waitForIdle()
        val now = f.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        check(!now.contains("ZZZ")) { "undo did nothing: '$now'" }
    }
}
