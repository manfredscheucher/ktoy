package org.bytefred.ktoy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
    var showCHelp by remember { mutableStateOf(false) }
    // Welcome / early-development notice at start, until "Don't show again".
    // rememberSaveable so an Android rotation doesn't re-open a dismissed one.
    var showWelcome by rememberSaveable { mutableStateOf(loadPref(WELCOME_PREF) != "off") }

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
            CheckItem("I/O panel", vm.showIo) { vm.toggleIo() }
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
                text = { Text("C: full C vs mini C") },
                onClick = { helpOpen = false; showCHelp = true },
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
    if (showCHelp) TextDialog("C: full C vs mini C", C_HELP) { showCHelp = false }
    if (showAbout) AboutDialog { showAbout = false }
    if (showWelcome) WelcomeDialog { showWelcome = false }
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

/** About: build identity, links, license, and the feedback box. */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About ktoy") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                val sha = GeneratedVersionInfo.COMMIT_SHA
                if (sha != "unknown") {
                    val tag = if (GeneratedVersionInfo.IS_DIRTY == "dirty") "$sha (modified)" else sha
                    Text("commit $tag")
                }
                if (GeneratedVersionInfo.COMMIT_DATE != "unknown") {
                    Text("committed ${GeneratedVersionInfo.COMMIT_DATE}")
                }
                Text("built ${GeneratedVersionInfo.BUILD_DATE}")
                Spacer(Modifier.height(8.dp))
                Text("A Toy CPU toolbox: simulator, assembler, C compiler.")
                Spacer(Modifier.height(8.dp))
                LinkText("ktoy on GitHub", "https://github.com/manfredscheucher/ktoy")
                LinkText("pytoy on GitHub", "https://github.com/manfredscheucher/pytoy")
                LinkText("Videos (YouTube playlist)", PLAYLIST_URL)
                Spacer(Modifier.height(8.dp))
                Text("License", fontWeight = FontWeight.Bold)
                LinkText("ktoy is MIT-licensed (LICENSE)", "https://github.com/manfredscheucher/ktoy/blob/main/LICENSE")
                Text("The Toy CPU instruction set is by Jim Hall " +
                    "(FreeDOS Project). Font: DejaVu Sans Mono (Bitstream Vera license, " +
                    "DejaVu changes public domain).")
                LinkText("Toy CPU by Jim Hall", "https://github.com/freedosproject/toycpu")
                Spacer(Modifier.height(10.dp))
                FeedbackBox("ktoy is in early development. Feedback is very welcome:")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** Shown at start until "Don't show again" is ticked. Ported from PoCoView. */
@Composable
private fun WelcomeDialog(close: () -> Unit) {
    var dontShow by remember { mutableStateOf(false) }
    fun done() { if (dontShow) savePref(WELCOME_PREF, "off"); close() }
    AlertDialog(
        onDismissRequest = ::done,
        confirmButton = { TextButton(onClick = ::done) { Text("Start") } },
        title = { Text("Welcome to ktoy") },
        text = {
            Column {
                Text("Step through a tiny 8-bit CPU: assembly, bytecode and a small C compiler.")
                Spacer(Modifier.height(10.dp))
                FeedbackBox("ktoy is in early development. Bugs, ideas or general feedback? Please send them:")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(dontShow, { dontShow = it })
                    Text("Don't show again")
                }
            }
        },
    )
}

/** Yellow box with a clickable e-mail address that opens a prepared mail. Ported from PoCoView. */
@Composable
private fun FeedbackBox(question: String) {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFFF3B0), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE0C200), RoundedCornerShape(8.dp)).padding(10.dp),
    ) {
        Text(question, color = Color(0xFF3A3000))
        Text(
            EMAIL,
            style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
            color = Color(0xFF1A4FB0),
            modifier = Modifier.clickable { openUriSafely(uri, FEEDBACK_MAILTO) }.padding(top = 2.dp),
        )
    }
}

/** A clickable, underlined link that opens [url] in the browser. */
@Composable
private fun LinkText(label: String, url: String) {
    val uri = LocalUriHandler.current
    Text(
        label,
        style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
        color = Color(0xFF1A4FB0),
        modifier = Modifier.clickable { openUriSafely(uri, url) }.padding(vertical = 2.dp),
    )
}

/**
 * Open a link without ever crashing the app: Android throws when no mail app or
 * browser is installed, the JVM when the desktop can't browse. Then just nothing
 * happens.
 */
private fun openUriSafely(uri: UriHandler, url: String) {
    try { uri.openUri(url) } catch (e: Exception) { }
}

private const val WELCOME_PREF = "welcome"
private const val EMAIL = "manfred.scheucher@proton.me"
private const val PLAYLIST_URL = "https://www.youtube.com/watch?v=vQxGXtlf5c4&list=PLU1OJ36r4v_c"
// subject "ktoy feedback", body "hi manfred," + blank line
private const val FEEDBACK_MAILTO =
    "mailto:$EMAIL?subject=ktoy%20feedback&body=hi%20manfred%2C%0A%0A"

private const val C_HELP = """C COMPILERS

ktoy compiles a small subset of C to Toy CPU assembly and
shows C, assembly and memory side by side. There are two
compilers; switch with the button above the C column.

full C (toycc)
  - functions, including recursion (a software stack
    in memory)
  - arrays and pointers
  - read() / write() for the I/O cells

mini C (minicc)
  - small and readable, made to see how a compiler works
  - several functions, but no recursion (no call stack)
  - no pointers, no I/O
  - flat variable scope (a name declared in a { } block
    stays visible after it)

Both
  - every int is 8 bits (0..255) and wraps mod 256
  - < > <= >= use bit 7, correct when the operands
    differ by less than 128
  - code and data must fit into 240 bytes
"""
