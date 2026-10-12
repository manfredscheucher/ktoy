package org.bytefred.ktoy.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.bytefred.ktoy.core.Example
import org.bytefred.ktoy.core.groupedExamplesTree

/**
 * The app root: two screens. The example list, and — after picking one — the
 * simulator. A tiny nav flag keeps them separate; the simulator's state lives in
 * a ViewModel that survives rotation.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    MaterialTheme {
        // Keep content out of the status bar (top) and the navigation bar /
        // camera cutout (bottom); the activity draws edge-to-edge.
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val vm: SimViewModel = viewModel { SimViewModel() }
            // Start on the simulator with an empty machine (like pytoy's
            // empty_gui); the Load button opens the example list.
            if (!vm.hasProgram) vm.loadEmpty()
            var showList by remember { mutableStateOf(false) }

            // System-back closes the example list if it's open. Wrapped in a
            // platform shim: Android maps it to the real system-back handler;
            // JVM/web have no system back, so it's a no-op there.
            AppBackHandler(enabled = showList) { showList = false }

            SimulatorScreen(vm = vm, onLoad = { showList = true })

            if (showList) {
                ExampleListScreen(
                    onPick = { example ->
                        vm.load(example)
                        showList = false
                    },
                    onDismiss = { showList = false },
                )
            }
        }
    }
}

/**
 * The example browser: a collapsible tree grouped by category then repo
 * subfolder (mirrors pytoy's ExamplesDialog), not a flat list. Top-level nodes
 * are the categories (Assembly / C (minicc) / C (toycc) / Bytecode); their
 * children are the subfolders (01-basics, 04-io, ...); leaves are the examples.
 * Bytecode has a single unnamed subfolder, so its leaves sit directly under it.
 *
 * Which category node a C leaf lives under IS the chosen compiler: the tree
 * hands [onPick] an Example already narrowed to that one compiler, so [SimViewModel.load]
 * opens it on minicc or toycc accordingly.
 */
@Composable
private fun ExampleListScreen(onPick: (Example) -> Unit, onDismiss: () -> Unit) {
    val tree = remember { groupedExamplesTree() }
    // Expanded nodes, keyed by a stable path string. Category nodes start open
    // so the structure is visible at a glance; subfolders start collapsed.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    fun isOpen(key: String, default: Boolean) = expanded[key] ?: default
    fun toggle(key: String, default: Boolean) { expanded[key] = !isOpen(key, default) }

    // opaque overlay over the simulator so the list fully replaces it while open
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Pick an example", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                for ((category, bySub) in tree) {
                    val catOpen = isOpen(category, default = true)
                    item(key = "cat:$category") {
                        TreeHeader(category, catOpen, depth = 0) { toggle(category, true) }
                        HorizontalDivider()
                    }
                    if (!catOpen) continue
                    for ((sub, examples) in bySub) {
                        // Bytecode's single "" subfolder: no header, leaves sit
                        // right under the category.
                        if (sub.isEmpty()) {
                            items(examples, key = { "$category//${it.name}" }) { ex ->
                                TreeLeaf(ex.name, depth = 1) { onPick(ex) }
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
                            TreeLeaf(ex.name, depth = 2) { onPick(ex) }
                        }
                    }
                }
            }
        }
    }
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

/** A clickable example leaf. */
@Composable
private fun TreeLeaf(name: String, depth: Int, onPick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clickable { onPick() }
            .padding(start = (depth * 16).dp)
            .padding(vertical = 12.dp)
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}
