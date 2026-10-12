package org.bytefred.ktoy.ui

import androidx.compose.runtime.Composable

/**
 * Cross-platform file load / save for the File menu, as a platform shim in the
 * same style as [AppBackHandler]. Each is a @Composable that returns a launch
 * lambda, so platforms that need a Compose-scoped launcher (Android's
 * ActivityResult, the browser's DOM input) can set one up.
 *
 *  - [rememberFileLoader] opens a picker; on success it calls `onLoaded(name,
 *    text)` with the chosen file's name (WITH extension — the VM infers the
 *    program kind .toys/.toyo/.toyc from it) and its text content.
 *  - [rememberFileSaver] writes `text` out under a suggested `name`+extension —
 *    a Save dialog on desktop/Android/iOS, a download in the browser.
 *
 * `fileIoSupported` reports whether real load/save exists on this platform; the
 * UI hides Load-from-disk / Save where it doesn't (none hidden today, but the
 * browser's "Reload from disk" is suppressed since there is no stable path).
 */

/** True if this platform can load a program file from disk / the user's device. */
expect val fileLoadSupported: Boolean

/** True if this platform can save a program to disk / download it. */
expect val fileSaveSupported: Boolean

/**
 * True if this platform has a stable file *path* that a later Reload can re-read.
 * The browser has none (a picked File is a one-shot blob), so Reload is hidden
 * there even though load/save work.
 */
expect val fileReloadSupported: Boolean

/**
 * Remember a file-open launcher. The returned lambda, when called, opens the
 * platform's file picker (filtered to .toys/.toyo/.toyc). On a successful pick it
 * invokes [onLoaded] with (fileName, fileText) — fileName carries the extension
 * so the VM can tell .toys/.toyo/.toyc apart. A cancel does nothing.
 */
@Composable
expect fun rememberFileLoader(onLoaded: (name: String, text: String) -> Unit): () -> Unit

/**
 * Remember a file-save launcher. The returned lambda takes (suggestedName,
 * extension, text) and writes it out: a Save dialog on desktop/Android/iOS, a
 * blob download in the browser. [onSaved] is called with a short human message
 * (e.g. the file name) on success, or null if cancelled/unsupported.
 */
@Composable
expect fun rememberFileSaver(
    onSaved: (message: String?) -> Unit,
): (suggestedName: String, extension: String, text: String) -> Unit
