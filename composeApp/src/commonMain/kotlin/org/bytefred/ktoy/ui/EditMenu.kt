package org.bytefred.ktoy.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.round

/**
 * How a panel's context menu (a single "Edit…" item) opens: a right-click
 * and/or a long-press. Right-click on desktop and the web, long-press on
 * touch (Android, iOS) and also on the web.
 */
data class EditGestures(val rightClick: Boolean, val longPress: Boolean)

expect val platformEditGestures: EditGestures

/** The gestures in use; tests can switch on the other platforms' ones. */
val LocalEditGestures = staticCompositionLocalOf { platformEditGestures }

/** One panel's context menu: where it is open and what its Edit… does. */
class EditMenuState {
    // the last press in the panel, in the panel's coordinates: the menu opens there
    internal var lastPress = Offset.Zero
    internal var openAt by mutableStateOf<Offset?>(null)
    private var onEdit: () -> Unit = {}

    fun open(edit: () -> Unit) {
        onEdit = edit
        openAt = lastPress
    }

    internal fun edit() {
        openAt = null
        onEdit()
    }
}

@Composable
fun rememberEditMenu() = remember { EditMenuState() }

/** A panel area whose rows ([editableRow]) can open the "Edit…" menu at the pointer. */
@Composable
fun EditMenuBox(state: EditMenuState, modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.pointerInput(state) {
        // only watches (Initial pass, nothing consumed): remembers where a press was
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.type == PointerEventType.Press) e.changes.firstOrNull()?.let { state.lastPress = it.position }
            }
        }
    }) {
        content()
        state.openAt?.let { at ->
            Box(Modifier.offset { at.round() }) {
                DropdownMenu(expanded = true, onDismissRequest = { state.openAt = null }) {
                    DropdownMenuItem(
                        text = { Text("Edit…") },
                        onClick = state::edit,
                        modifier = Modifier.testTag("context-edit"),
                    )
                }
            }
        }
    }
}

/**
 * A row's pointer handling: a tap runs [onTap]; a right-click or long-press
 * (as [gestures] allow) opens [menu] with [onEdit]. A right-click is taken
 * before the tap detector sees it, so it does not also select the row.
 */
fun Modifier.editableRow(
    menu: EditMenuState,
    gestures: EditGestures,
    key: Any?,
    onTap: (() -> Unit)?,
    onEdit: () -> Unit,
): Modifier = this
    .pointerInput(key, gestures) {
        if (!gestures.rightClick) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                    e.changes.forEach { it.consume() }
                    menu.open(onEdit)
                }
            }
        }
    }
    .pointerInput(key, gestures) {
        detectTapGestures(
            onTap = onTap?.let { tap -> { tap() } },
            onLongPress = if (gestures.longPress) { _ -> menu.open(onEdit) } else null,
        )
    }
