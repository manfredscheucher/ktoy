package org.bytefred.ktoy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.bytefred.ktoy.GeneratedVersionInfo
import org.bytefred.ktoy.core.referenceText

/**
 * The simulator's menu bar: File (Load/Reload/Save), one menu per layer of the
 * build pipeline — C (Edit, Compile, compiler), Assembly (Edit, Assemble),
 * Bytecode (Edit line, Disassemble) — then View (line numbers, I/O, layout, auto-scroll) and
 * Help (instruction-set reference + About). The pytoy menu bar, as cross-platform
 * Compose: each menu is a text button that opens a [DropdownMenu]. File I/O goes
 * through the [FileIo] platform shim (Swing / SAF / browser blob). [onBrowseExamples]
 * opens the existing example list; [onEditC] / [onEditAsm] / [onEditCell] open
 * the same edit dialogs as the panels' right-click / long-press menu.
 */
@Composable
fun SimMenuBar(
    vm: SimViewModel,
    onBrowseExamples: () -> Unit,
    onEditC: () -> Unit,
    onEditAsm: () -> Unit,
    onEditBytecode: () -> Unit,
    onEditCell: () -> Unit,
) {
    // the enabled states below read plain VM fields; tick tells when they change
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    // the open menu's title, or null; one state for all menus, so a click on
    // another title switches straight to that menu (see MenuButton)
    var openMenu by remember { mutableStateOf<String?>(null) }
    var savePickerFor by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    // the format chosen in the picker, while "this file lacks your edits" asks
    var staleSaveFor by remember { mutableStateOf<String?>(null) }
    // Escape closes an open menu: the popups are not focusable (see MenuButton),
    // so the bar takes the focus while a menu is open
    val barFocus = remember { FocusRequester() }
    LaunchedEffect(openMenu) { if (openMenu != null) barFocus.requestFocus() }
    var showReference by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showCHelp by remember { mutableStateOf(false) }
    var showBuildHelp by remember { mutableStateOf(false) }
    // Welcome / early-development notice at start, until "Don't show again".
    // rememberSaveable so an Android rotation doesn't re-open a dismissed one.
    var showWelcome by rememberSaveable { mutableStateOf(loadPref(WELCOME_PREF) != "off") }

    val loadFromDisk = rememberFileLoader { name, text, path -> vm.loadFromFile(name, text, path) }
    val saveToDisk = rememberFileSaver(vm::fileSaved)
    // one format saves right away, several ask which (C preselected, else assembly)
    fun save() {
        val formats = vm.saveFormats()
        if (formats.size == 1) saveAs(vm, saveToDisk, formats[0].second)
        else savePickerFor = formats
    }

    // scrolls sideways when the seven menus don't fit (phones)
    Row(
        Modifier.horizontalScroll(rememberScrollState())
            .testTag("menu-bar")
            .focusRequester(barFocus)
            .onKeyEvent {
                if (it.key == Key.Escape && openMenu != null) { openMenu = null; true } else false
            }
            .focusable(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── File ──
        MenuButton("File", openMenu, { openMenu = it }) {
            DropdownMenuItem(
                text = { Text("Examples…") },
                onClick = { openMenu = null; onBrowseExamples() },
            )
            if (fileLoadSupported) {
                DropdownMenuItem(
                    text = { Text("Load…") },
                    onClick = { openMenu = null; vm.leave(loadFromDisk) },
                )
            }
            if (fileReloadSupported) {
                DropdownMenuItem(
                    text = { Text("Reload") },
                    enabled = vm.canReload,
                    onClick = { openMenu = null; vm.leave { vm.reload() } },
                )
            }
            if (fileSaveSupported) {
                DropdownMenuItem(
                    text = { Text("Save…") },
                    onClick = { openMenu = null; save() },
                )
            }
        }

        // ── C ── (only for a C program)
        MenuButton("C", openMenu, { openMenu = it }, enabled = vm.hasCSource) {
            DropdownMenuItem(text = { Text("Edit…") }, onClick = { openMenu = null; onEditC() })
            DropdownMenuItem(
                text = { Text("Compile") },
                onClick = { openMenu = null; vm.request(SimViewModel.BuildAction.Compile()) },
            )
            HorizontalDivider()
            // switching recompiles right away (full C is the default at load)
            for ((compiler, label) in listOf("toycc" to "Full C (toycc)", "minicc" to "Mini C (minicc)")) {
                CheckItem(label, vm.currentCompiler == compiler) {
                    openMenu = null
                    if (vm.currentCompiler != compiler) vm.request(SimViewModel.BuildAction.Compile(compiler))
                }
            }
        }

        // ── Assembly ── (any program with source; for C it drops the C)
        MenuButton("Assembly", openMenu, { openMenu = it }, enabled = vm.asmText != null) {
            DropdownMenuItem(text = { Text("Edit…") }, onClick = { openMenu = null; onEditAsm() })
            DropdownMenuItem(
                text = { Text("Assemble") },
                onClick = { openMenu = null; vm.request(SimViewModel.BuildAction.Assemble) },
            )
        }

        // ── Bytecode ──
        MenuButton("Bytecode", openMenu, { openMenu = it }) {
            DropdownMenuItem(text = { Text("Edit…") }, onClick = { openMenu = null; onEditBytecode() })
            DropdownMenuItem(
                text = { Text("Edit line…") },
                enabled = vm.memSelectedAddr != null,
                onClick = { openMenu = null; onEditCell() },
            )
            DropdownMenuItem(
                text = { Text("Disassemble") },
                onClick = { openMenu = null; vm.request(SimViewModel.BuildAction.Disassemble) },
            )
        }

        // ── View ──
        MenuButton("View", openMenu, { openMenu = it }) {
            CheckItem("Line numbers", vm.showLineNumbers) {
                vm.showLineNumbers = !vm.showLineNumbers
            }
            CheckItem("I/O panel", vm.showIo) { vm.toggleIo() }
            CheckItem("Auto-scroll to PC", vm.autoScroll) { vm.autoScroll = !vm.autoScroll }
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
        MenuButton("Help", openMenu, { openMenu = it }) {
            DropdownMenuItem(
                text = { Text("Instruction set & shortcuts") },
                onClick = { openMenu = null; showReference = true },
            )
            DropdownMenuItem(
                text = { Text("Edit and build") },
                onClick = { openMenu = null; showBuildHelp = true },
            )
            DropdownMenuItem(
                text = { Text("C: full C vs mini C") },
                onClick = { openMenu = null; showCHelp = true },
            )
            DropdownMenuItem(
                text = { Text("About ktoy") },
                onClick = { openMenu = null; showAbout = true },
            )
        }
    }

    // Load / Examples / Reload / closing the window with unsaved edits
    if (vm.pendingLeave != null && savePickerFor == null) {
        UnsavedEditsDialog(
            vm.unsavedDescription(),
            onSave = ::save,
            onDiscard = vm::discardAndLeave,
            onCancel = vm::cancelLeave,
        )
    }

    // Save-format picker (shown only when more than one format applies)
    if (staleSaveFor == null) savePickerFor?.let { formats ->
        SaveFormatDialog(
            formats,
            onSave = { ext ->
                if (vm.missingFrom(ext).isNotEmpty()) staleSaveFor = ext
                else { savePickerFor = null; saveAs(vm, saveToDisk, ext) }
            },
            // also cancels a pending Load / Examples / Reload / close
            onDismiss = { savePickerFor = null; vm.cancelLeave() },
        )
    }

    // the chosen format lacks an edit: ask first
    staleSaveFor?.let { ext ->
        StaleSaveDialog(
            vm.missingFrom(ext),
            onSaveAnyway = { staleSaveFor = null; savePickerFor = null; saveAs(vm, saveToDisk, ext) },
            onBack = { staleSaveFor = null },
        )
    }

    if (showReference) TextDialog("Instruction set & shortcuts", referenceText()) { showReference = false }
    if (showBuildHelp) TextDialog("Edit and build", BUILD_HELP) { showBuildHelp = false }
    if (showCHelp) TextDialog("C: full C vs mini C", C_HELP) { showCHelp = false }
    if (showAbout) AboutDialog { showAbout = false }
    if (showWelcome) WelcomeDialog { showWelcome = false }
}

private fun saveAs(vm: SimViewModel, saveToDisk: (String, String, String) -> Unit, ext: String) {
    val text = vm.beginSave(ext)
    if (text != null) saveToDisk(vm.suggestedSaveName(), ext, text) else vm.fileSaved(null)
}

/** The chosen format does not contain every edit: Save anyway / Back to the picker. */
@Composable
private fun StaleSaveDialog(missing: List<String>, onSaveAnyway: () -> Unit, onBack: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("This file lacks your edits") },
        text = {
            Column {
                missing.forEach { Text(it, Modifier.padding(bottom = 4.dp)) }
                Text("They stay unsaved.", Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = onSaveAnyway) { Text("Save anyway") } },
        dismissButton = { TextButton(onClick = onBack) { Text("Back") } },
    )
}

/** "Save changes?" before the program is replaced: Save… / Discard / Cancel. */
@Composable
private fun UnsavedEditsDialog(unsaved: String, onSave: () -> Unit, onDiscard: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Save changes?") },
        text = { Text("Not saved to a file yet: $unsaved.") },
        confirmButton = { TextButton(onClick = onSave) { Text("Save…") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text("Discard") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}

/** Which format to save: one radio button each, the first (highest level) preselected. */
@Composable
private fun SaveFormatDialog(
    formats: List<Pair<String, String>>,
    onSave: (ext: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember(formats) { mutableStateOf(formats.first().second) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as") },
        text = {
            Column {
                formats.forEach { (label, ext) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(chosen == ext, role = Role.RadioButton) { chosen = ext },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(chosen == ext, onClick = null)
                        Text(label, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A menu title that opens a [DropdownMenu]. Plain clickable text rather than a
 * TextButton: Material's 58 dp minimum button width would push six menus past
 * a 360 dp phone screen. The popup is not focusable, so a click outside it
 * still reaches what is underneath: clicking another menu title closes this
 * menu and opens that one in one click. A click on the open menu's own title
 * closes it: the popup's outside-click dismissal fires first (on press), so the
 * title click right after it must not reopen the menu.
 */
@Composable
private fun MenuButton(
    label: String,
    openMenu: String?,
    setOpenMenu: (String?) -> Unit,
    enabled: Boolean = true,
    items: @Composable () -> Unit,
) {
    var dismissedAt by remember { mutableStateOf<TimeMark?>(null) }
    Box {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier
                .testTag("menu-$label")
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = enabled) {
                    val justClosed = dismissedAt?.let { it.elapsedNow() < REOPEN_GUARD } == true
                    setOpenMenu(if (openMenu == label || justClosed) null else label)
                }
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
        DropdownMenu(
            expanded = openMenu == label,
            // only close if this menu is still the open one: the outside click
            // may have just opened another menu
            onDismissRequest = {
                if (openMenu == label) {
                    dismissedAt = TimeSource.Monotonic.markNow()
                    setOpenMenu(null)
                }
            },
            properties = PopupProperties(focusable = false),
        ) {
            items()
        }
    }
}

// a title click this soon after its menu's outside-click dismissal belongs to
// the same click (press dismissed, release clicked)
private val REOPEN_GUARD = 500.milliseconds

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

/** About: description, feedback box, the projects with their licenses, build identity. */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ktoy") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("A Toy CPU toolbox: simulator, assembler, C compiler.")
                Spacer(Modifier.height(8.dp))
                FeedbackBox(FEEDBACK_TEXT)
                Spacer(Modifier.height(8.dp))
                ProjectLine("ktoy", "https://github.com/manfredscheucher/ktoy", "this app")
                ProjectLine("pytoy", "https://github.com/manfredscheucher/pytoy", "the Python original ktoy is ported from")
                ProjectLine("toycpu", "https://github.com/freedosproject/toycpu", "the Toy CPU instruction set by Jim Hall (FreeDOS Project)")
                ProjectLine("Videos", PLAYLIST_URL, "YouTube playlist", license = null)
                Spacer(Modifier.height(8.dp))
                Text(
                    buildLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** One About line "[name] (license): note" (no bracket without a license); only the name is a link. */
@Composable
private fun ProjectLine(name: String, url: String, note: String, license: String? = "MIT") {
    Row {
        LinkText(name, url)
        Text((license?.let { " ($it)" } ?: "") + ": $note", Modifier.padding(vertical = 2.dp))
    }
}

/** "sha · committed <date> · built <date>"; unknown parts are left out. */
private fun buildLine(): String = buildList {
    val sha = GeneratedVersionInfo.COMMIT_SHA
    if (sha != "unknown") add(if (GeneratedVersionInfo.IS_DIRTY == "dirty") "$sha (modified)" else sha)
    if (GeneratedVersionInfo.COMMIT_DATE != "unknown") add("committed ${GeneratedVersionInfo.COMMIT_DATE}")
    add("built ${GeneratedVersionInfo.BUILD_DATE}")
}.joinToString(" · ")

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
                FeedbackBox(FEEDBACK_TEXT)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(dontShow, { dontShow = it })
                    Text("Don't show again")
                }
            }
        },
    )
}

// the feedback note, the same in About and the welcome dialog
private const val FEEDBACK_TEXT = "ktoy is in early development. Feedback is very welcome:"

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

private const val BUILD_HELP = """EDIT AND BUILD

A program goes down three layers:
  C  --Compile-->  assembly  --Assemble-->  bytecode
and back up one:
  bytecode  --Disassemble-->  assembly

Edit
  Right-click a column (long-press on a touch screen) and
  choose Edit… or Edit line…, or use the C / Assembly /
  Bytecode menu.
  - C and assembly: Save only stores the text. The column
    shows "(modified)" and the program keeps running the
    old version until you Compile / Assemble. Save & Compile
    / Save & Assemble does both.
  - Edit line changes only that line.
  - Bytecode: takes effect at once. Edit… shows all 256
    cells, one integer per line. Missing lines at the end
    are 0; more than 256 lines is an error.

Compile (C menu)
  C -> assembly -> bytecode. The compiler choice in the
  same menu (full C / mini C) recompiles right away.

Assemble (Assembly menu)
  Assembly -> bytecode. For a C program the C column goes
  away: the program is plain assembly from then on.

Disassemble (Bytecode menu)
  The current memory (with your cell edits and what the
  program stored) -> assembly. Code and data are told
  apart by following the jumps from address 0; every byte
  that is never executed comes out as data. Labels are
  generated: L12 for code, D40 for data.

Reset vs. Compile / Assemble
  Reset (bottom bar) goes back to the start of the last
  build: PC 0, ACC 0, the bytes as built. Compile and
  Assemble build again from the source text.

Before a step throws something away (the C column, a
run in progress, memory edits, unassembled edits) ktoy
asks first and lists what would be lost.

Saving
  File -> Save… asks for the format: C, assembly or
  bytecode (whichever the program has; the highest level
  is preselected). Compile and Assemble never run by
  themselves, so if the chosen format lacks an edit (say
  assembly while the C edit is not compiled) ktoy asks
  first. An edit counts as saved only in the format it
  was made in: C edits as C, assembly as assembly, memory
  cells as bytecode. Until then Load, Examples, Reload and
  closing the window ask "Save changes?". Running a program
  is no edit, and a bytecode file holds the program as
  built plus your cell edits, nothing a run stored: one
  value per line, up to the last non-zero cell.
  Reload reads the file from disk again; saving at the
  program's highest level makes that file the one Reload
  opens, a lower level is only an export.
"""

private const val C_HELP = """C COMPILERS

ktoy compiles a small subset of C to Toy CPU assembly and
shows C, assembly and memory side by side. There are two
compilers; switch with the button above the C column
(or in the C menu).

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
