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
import androidx.compose.material3.FilledTonalButton
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
import org.bytefred.ktoy.core.ExampleKind

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
    var saveOpen by remember { mutableStateOf(false) }
    // the format chosen, while "this file lacks your edits" asks
    var staleSaveFor by remember { mutableStateOf<String?>(null) }
    // Escape closes an open menu: the popups are not focusable (see MenuButton),
    // so the bar takes the focus while a menu is open
    val barFocus = remember { FocusRequester() }
    LaunchedEffect(openMenu) { if (openMenu != null) barFocus.requestFocus() }
    var help by remember { mutableStateOf<HelpPage?>(null) }
    var emptyOpen by remember { mutableStateOf(false) }
    LaunchedEffect(openMenu) { emptyOpen = false }
    var showAbout by remember { mutableStateOf(false) }
    // Welcome / early-development notice at start, until "Don't show again".
    // rememberSaveable so an Android rotation doesn't re-open a dismissed one.
    var showWelcome by rememberSaveable { mutableStateOf(loadPref(WELCOME_PREF) != "off") }

    val loadFromDisk = rememberFileLoader { name, text, path -> vm.loadFromFile(name, text, path) }
    val saveToDisk = rememberFileSaver(vm::fileSaved)
    // one format saves right away, several ask which (C preselected, else assembly)
    fun pick(ext: String) {
        if (vm.missingFrom(ext).isNotEmpty()) staleSaveFor = ext
        else { saveOpen = false; saveAs(vm, saveToDisk, ext) }
    }
    fun save() {
        val formats = vm.saveFormats()
        if (formats.size == 1) pick(formats[0].second) else saveOpen = true
    }

    // a menu entry: closes the menu, then runs [onClick]
    @Composable
    fun Item(label: String, enabled: Boolean = true, onClick: () -> Unit) = DropdownMenuItem(
        text = { Text(label) },
        enabled = enabled,
        onClick = { openMenu = null; onClick() },
    )

    // scrolls sideways when the menus don't fit (phones)
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
            Item("Examples…", onClick = onBrowseExamples)
            // a submenu folded out in place: Material3 menus have no submenus
            DropdownMenuItem(
                text = { Text(if (emptyOpen) "Empty ▾" else "Empty ▸") },
                onClick = { emptyOpen = !emptyOpen },
                modifier = Modifier.testTag("menu-empty"),
            )
            if (emptyOpen) for ((label, kind) in listOf("C" to ExampleKind.C, "Assembly" to ExampleKind.ASM, "Bytecode" to ExampleKind.TOYO))
                DropdownMenuItem(
                    text = { Text(label, Modifier.padding(start = 16.dp)) },
                    onClick = { openMenu = null; emptyOpen = false; vm.leave { vm.loadEmpty(kind) } },
                    modifier = Modifier.testTag("empty-$label"),
                )
            Item("Load…") { vm.leave(loadFromDisk) }
            if (fileReloadSupported) Item("Reload", vm.canReload) { vm.leave { vm.reload() } }
            Item("Save…") { save() }
        }

        // ── C ── (only for a C program)
        MenuButton("C", openMenu, { openMenu = it }, enabled = vm.hasCSource) {
            Item("Edit…", onClick = onEditC)
            Item("Compile") { vm.request(SimViewModel.BuildAction.Compile()) }
            AutoItem(vm, SimViewModel.AutoBuild.COMPILE, "Auto compile on edit")
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
            Item("Edit…", onClick = onEditAsm)
            Item("Assemble") { vm.request(SimViewModel.BuildAction.Assemble) }
            AutoItem(vm, SimViewModel.AutoBuild.ASSEMBLE, "Auto assemble on edit")
        }

        // ── Bytecode ──
        MenuButton("Bytecode", openMenu, { openMenu = it }) {
            Item("Edit…", onClick = onEditBytecode)
            Item("Edit line…", vm.memSelectedAddr != null, onEditCell)
            Item("Disassemble") { vm.request(SimViewModel.BuildAction.Disassemble) }
            AutoItem(vm, SimViewModel.AutoBuild.DISASSEMBLE, "Auto disassemble on edit")
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
            Text("Layout", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            for (mode in LayoutMode.entries) {
                CheckItem("  " + mode.name.lowercase(), vm.layoutMode == mode) {
                    vm.layoutMode = mode
                }
            }
        }

        // ── Help ──
        MenuButton("Help", openMenu, { openMenu = it }) {
            for (page in HelpPage.entries) Item(page.title) { help = page }
            Item("About ktoy") { showAbout = true }
        }
    }

    // Load / Examples / Reload / closing the window with unsaved edits
    if (vm.pendingLeave != null && staleSaveFor == null) {
        SaveChoiceDialog("Save changes?", "Not saved to a file yet: ${vm.unsavedDescription()}.", vm.saveFormats(), ::pick, vm::cancelLeave) {
            TextButton(onClick = vm::discardAndLeave) { Text("Discard") }
            TextButton(onClick = vm::cancelLeave) { Text("Cancel") }
        }
    } else if (saveOpen && staleSaveFor == null) {
        SaveChoiceDialog("Save", null, vm.saveFormats(), ::pick, { saveOpen = false }) {
            TextButton(onClick = { saveOpen = false }) { Text("Cancel") }
        }
    }

    // the chosen format lacks an edit: ask first
    staleSaveFor?.let { ext ->
        StaleSaveDialog(
            vm.missingFrom(ext),
            onSaveAnyway = { staleSaveFor = null; saveOpen = false; saveAs(vm, saveToDisk, ext) },
            onBack = { staleSaveFor = null },
        )
    }

    help?.let { HelpDialog(it) { help = null } }
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

/**
 * Which format to save, one full-width button each, highest level first;
 * [buttons] below them (Cancel, or Discard / Cancel before leaving).
 */
@Composable
private fun SaveChoiceDialog(
    title: String,
    message: String?,
    formats: List<Pair<String, String>>,
    onPick: (ext: String) -> Unit,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message?.let { Text(it, Modifier.padding(bottom = 4.dp)) }
                for ((_, ext) in formats)
                    FilledTonalButton(onClick = { onPick(ext) }, Modifier.fillMaxWidth()) { Text(saveLabel(ext)) }
            }
        },
        confirmButton = { Row { buttons() } },
    )
}

private fun saveLabel(ext: String) = when (ext) {
    ".toyc" -> "Save C"
    ".toys" -> "Save assembly"
    else -> "Save bytecode"
}

/** A menu toggle for one [SimViewModel.AutoBuild]; turning one on turns the others off. */
@Composable
private fun AutoItem(vm: SimViewModel, which: SimViewModel.AutoBuild, label: String) =
    CheckItem(label, vm.autoBuild == which) { vm.toggleAutoBuild(which) }

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

/** About: description, feedback box, the projects with their licenses, build identity. */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ktoy") },
        text = {
            val vScroll = rememberScrollState()
            ScrollbarBox(vScroll, Modifier.heightIn(max = 520.dp)) {
            Column(Modifier.verticalScroll(vScroll)) {
                Text("A Toy CPU toolbox: simulator, assembler, C compiler.")
                Spacer(Modifier.height(8.dp))
                FeedbackBox()
                Spacer(Modifier.height(8.dp))
                ProjectLine("ktoy", "https://github.com/manfredscheucher/ktoy", "this app")
                ProjectLine("toycpu", "https://github.com/freedosproject/toycpu", "the Toy CPU instruction set by Jim Hall (FreeDOS Project)")
                ProjectLine("Videos", PLAYLIST_URL, "YouTube playlist", license = null)
                Spacer(Modifier.height(8.dp))
                Text(
                    buildLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                FeedbackBox()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(dontShow, { dontShow = it })
                    Text("Don't show again")
                }
            }
        },
    )
}

/** Yellow box with a clickable e-mail address that opens a prepared mail (About, welcome). */
@Composable
private fun FeedbackBox() {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFFF3B0), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE0C200), RoundedCornerShape(8.dp)).padding(10.dp),
    ) {
        Text("ktoy is in early development. Feedback is very welcome:", color = Color(0xFF3A3000))
        LinkText(EMAIL, FEEDBACK_MAILTO)
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
