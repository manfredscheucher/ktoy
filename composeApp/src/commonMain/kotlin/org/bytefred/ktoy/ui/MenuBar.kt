package org.bytefred.ktoy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.bytefred.ktoy.GeneratedVersionInfo
import org.bytefred.ktoy.core.referenceText

/**
 * The simulator's menu bar: File (Load/Reload/Save), View (line numbers, I/O),
 * Help (instruction-set reference + About). The pytoy menu bar, as cross-platform
 * Compose: each menu is a text button that opens a [DropdownMenu]. File I/O goes
 * through the [FileIo] platform shim (Swing / SAF / browser blob). [onBrowseExamples]
 * opens the existing example list.
 */
@Composable
fun SimMenuBar(vm: SimViewModel, onBrowseExamples: () -> Unit) {
    var fileOpen by remember { mutableStateOf(false) }
    var viewOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var savePickerFor by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var showReference by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val loadFromDisk = rememberFileLoader { name, text -> vm.loadFromFile(name, text) }
    val saveToDisk = rememberFileSaver { msg -> vm.setStatus(msg?.let { "Saved $it" }) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── File ──
        MenuButton("File", fileOpen, { fileOpen = it }) {
            DropdownMenuItem(
                text = { Text("Examples…") },
                onClick = { fileOpen = false; onBrowseExamples() },
            )
            if (fileLoadSupported) {
                DropdownMenuItem(
                    text = { Text("Load…") },
                    onClick = { fileOpen = false; loadFromDisk() },
                )
            }
            if (fileReloadSupported) {
                DropdownMenuItem(
                    text = { Text("Reload") },
                    enabled = vm.canReload,
                    onClick = { fileOpen = false; vm.reload() },
                )
            }
            if (fileSaveSupported) {
                DropdownMenuItem(
                    text = { Text("Save…") },
                    onClick = {
                        fileOpen = false
                        val formats = vm.saveFormats()
                        if (formats.size == 1) {
                            val (_, ext) = formats[0]
                            vm.exportText(ext)?.let { saveToDisk(vm.suggestedSaveName(), ext, it) }
                        } else {
                            savePickerFor = formats
                        }
                    },
                )
            }
        }

        // ── View ──
        MenuButton("View", viewOpen, { viewOpen = it }) {
            CheckItem("Line numbers", vm.showLineNumbers) {
                vm.showLineNumbers = !vm.showLineNumbers
            }
            CheckItem("I/O panel", vm.showIo) { vm.showIo = !vm.showIo }
            HorizontalDivider()
            // Layout: AUTO (by window size) / PORTRAIT (stacked) / LANDSCAPE
            // (side by side). A flat radio group rather than a nested submenu,
            // since Material3 DropdownMenu has no built-in submenus.
            Text("Layout", style = MonoStyle,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            for (mode in LayoutMode.entries) {
                CheckItem("  " + mode.name.lowercase(), vm.layoutMode == mode) {
                    vm.layoutMode = mode
                }
            }
        }

        // ── Help ──
        MenuButton("Help", helpOpen, { helpOpen = it }) {
            DropdownMenuItem(
                text = { Text("Instruction set & shortcuts") },
                onClick = { helpOpen = false; showReference = true },
            )
            DropdownMenuItem(
                text = { Text("About ktoy") },
                onClick = { helpOpen = false; showAbout = true },
            )
        }
    }

    // Save-format picker (shown only when more than one format applies)
    savePickerFor?.let { formats ->
        AlertDialog(
            onDismissRequest = { savePickerFor = null },
            title = { Text("Save as") },
            text = {
                Column {
                    formats.forEach { (label, ext) ->
                        TextButton(onClick = {
                            savePickerFor = null
                            vm.exportText(ext)?.let {
                                saveToDisk(vm.suggestedSaveName(), ext, it)
                            }
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { savePickerFor = null }) { Text("Cancel") } },
        )
    }

    if (showReference) TextDialog("Instruction set & shortcuts", referenceText()) { showReference = false }
    if (showAbout) AboutDialog { showAbout = false }
}

@Composable
private fun MenuButton(
    label: String,
    expanded: Boolean,
    setExpanded: (Boolean) -> Unit,
    items: @Composable () -> Unit,
) {
    Box {
        TextButton(onClick = { setExpanded(true) }) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
            items()
        }
    }
}

@Composable
private fun CheckItem(label: String, checked: Boolean, onToggle: () -> Unit) {
    DropdownMenuItem(
        text = { Text((if (checked) "✓ " else "   ") + label) },
        onClick = onToggle,
    )
}

/** A read-only, monospaced, scrollable text dialog (Help reference). */
@Composable
private fun TextDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                body,
                style = MonoStyle,
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** About: build identity from GeneratedVersionInfo (version + git commit). */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About ktoy") },
        text = {
            Column {
                Text("ktoy ${GeneratedVersionInfo.VERSION}")
                val sha = GeneratedVersionInfo.COMMIT_SHA
                if (sha != "unknown") {
                    val tag = if (GeneratedVersionInfo.IS_DIRTY == "dirty") "$sha (modified)" else sha
                    Text("commit $tag")
                }
                if (GeneratedVersionInfo.COMMIT_DATE != "unknown") {
                    Text("committed ${GeneratedVersionInfo.COMMIT_DATE}")
                }
                Text("")
                Text("A Toy CPU toolbox: simulator, assembler, compiler.")
                Text("https://github.com/manfredscheucher/pytoy")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
