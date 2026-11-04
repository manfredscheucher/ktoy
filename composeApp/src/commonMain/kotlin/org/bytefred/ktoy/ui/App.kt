package org.bytefred.ktoy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.bytefred.ktoy.core.Example
import org.bytefred.ktoy.core.groupedExamplesTree

/**
 * The app root: the simulator, with the Examples dialog on top while it is open.
 * The simulator's state lives in a ViewModel that survives rotation.
 * [closeRequests] counts the desktop window's close requests: each one asks
 * "Save changes?" if there are unsaved edits, then calls [onClose].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(closeRequests: Int = 0, onClose: () -> Unit = {}) {
    KtoyTheme {
      ProvideMonoStyle {
        // Keep content out of the status bar (top) and the navigation bar /
        // camera cutout (bottom); the activity draws edge-to-edge.
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val vm: SimViewModel = viewModel { SimViewModel() }
            // Start with the first example (00_stop) open, so there is
            // something to step through right away.
            if (!vm.hasProgram) vm.loadStartExample()
            var showList by remember { mutableStateOf(false) }
            // outlives the dialog, so it reopens as it was left
            val examplesState = remember { ExamplesTreeState() }

            // System-back closes the Examples dialog if it's open. Wrapped in a
            // platform shim: Android maps it to the real system-back handler;
            // JVM/web have no system back, so it's a no-op there.
            AppBackHandler(enabled = showList) { showList = false }

            LaunchedEffect(closeRequests) { if (closeRequests > 0) vm.leave(onClose) }

            SimulatorScreen(vm = vm, onLoad = { showList = true })

            if (showList) {
                ExamplesDialog(
                    state = examplesState,
                    running = vm.running,
                    onLoad = { example ->
                        vm.leave { vm.load(example) }
                        showList = false
                    },
                    onDismiss = { showList = false },
                )
            }
        }
      }
    }
}

/**
 * The example browser as a dialog: a collapsible tree grouped by category then
 * repo subfolder (mirrors pytoy's ExamplesDialog). Top-level nodes are the
 * categories (Assembly / Bytecode / C (minicc) / C (toycc)); their children are
 * the subfolders (01-basics, 04-io, ...); leaves are the examples, as radio
 * buttons. Bytecode has a single unnamed subfolder, so its leaves sit directly
 * under it. A click only selects; Load loads the selected example. If the
 * program is still running, Load first asks whether to stop it.
 *
 * Which category node a C leaf lives under IS the chosen compiler: the tree
 * holds an Example already narrowed to that one compiler, so [SimViewModel.load]
 * opens it on minicc or toycc accordingly (and the same C file under the other
 * compiler is a different, separately selectable leaf).
 */
@Composable
internal fun ExamplesDialog(
    state: ExamplesTreeState,
    running: Boolean,
    onLoad: (Example) -> Unit,
    onDismiss: () -> Unit,
) {
    val tree = remember { groupedExamplesTree() }
    val expanded = state.expanded
    fun isOpen(key: String, default: Boolean) = expanded[key] ?: default
    fun toggle(key: String, default: Boolean) { expanded[key] = !isOpen(key, default) }
    var selected by state::selected
    var confirmStop by remember { mutableStateOf(false) }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            text = { Text("The program is still running. Stop it and load the new example?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    selected?.let(onLoad)   // load() stops the run first
                }) { Text("Stop and load") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Cancel") } },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Examples") },
        text = {
            LazyColumn(Modifier.heightIn(max = 560.dp).testTag("examples-tree"), state = state.list) {
                for ((category, bySub) in tree) {
                    val catOpen = isOpen(category, default = false)
                    item(key = "cat:$category") {
                        TreeHeader(category, catOpen, depth = 0) { toggle(category, false) }
                        HorizontalDivider()
                    }
                    if (!catOpen) continue
                    for ((sub, examples) in bySub) {
                        // Bytecode's single "" subfolder: no header, leaves sit
                        // right under the category.
                        if (sub.isEmpty()) {
                            items(examples, key = { "$category//${it.name}" }) { ex ->
                                TreeLeaf(ex.name, depth = 1, ex == selected) { selected = ex }
                            }
                            continue
                        }
                        val subKey = "$category/$sub"
                        val subOpen = isOpen(subKey, default = false)
                        item(key = "sub:$subKey") {
                            TreeHeader(sub, subOpen, depth = 1) { toggle(subKey, false) }
                        }
                        if (!subOpen) continue
                        items(examples, key = { "$subKey//${it.name}" }) { ex ->
                            TreeLeaf(ex.name, depth = 2, ex == selected) { selected = ex }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ex = selected ?: return@TextButton
                    if (running) confirmStop = true else onLoad(ex)
                },
                enabled = selected != null,
            ) { Text("Load") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * What the Examples dialog remembers between openings: the expanded nodes
 * (keyed by a stable path string; everything starts collapsed, so the first
 * opening shows just the category groups), the selected example and the
 * scroll position. Reopening shows the tree exactly as it was left.
 */
class ExamplesTreeState {
    val expanded = mutableStateMapOf<String, Boolean>()
    var selected by mutableStateOf<Example?>(null)
    val list = LazyListState()
}

/** A clickable, expand/collapse tree node (category or subfolder). */
@Composable
private fun TreeHeader(label: String, open: Boolean, depth: Int, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onToggle() }
            .padding(start = (depth * 16).dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (open) "▾" else "▸", style = MaterialTheme.typography.bodyLarge)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** An example leaf: a radio button; a click selects it. */
@Composable
private fun TreeLeaf(name: String, depth: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(start = (depth * 16).dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * Material 3 with neutral backgrounds: the default scheme tints the background,
 * dialogs and menus slightly pink (#FFFBFE, #ECE6F0, ...). White and plain greys
 * instead; the accent colours stay as they are.
 */
@Composable
fun KtoyTheme(content: @Composable () -> Unit) {
    val neutral = lightColorScheme(
        background = Color.White,
        surface = Color.White,
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF7F7F7),
        surfaceContainer = Color(0xFFF2F2F2),
        surfaceContainerHigh = Color(0xFFEDEDED),
        surfaceContainerHighest = Color(0xFFE6E6E6),
        surfaceVariant = Color(0xFFE6E6E6),
        surfaceDim = Color(0xFFDDDDDD),
    )
    MaterialTheme(colorScheme = neutral, content = content)
}
