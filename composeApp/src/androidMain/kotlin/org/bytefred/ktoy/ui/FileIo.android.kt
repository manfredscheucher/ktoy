package org.bytefred.ktoy.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// Android: the Storage Access Framework (SAF). OpenDocument picks a file to read;
// CreateDocument picks where to write. A content:// Uri is not a stable file
// path we can silently re-read later, so Reload is not supported.
actual val fileLoadSupported: Boolean = true
actual val fileSaveSupported: Boolean = true
actual val fileReloadSupported: Boolean = false

@Composable
actual fun rememberFileLoader(onLoaded: (name: String, text: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            // full name incl. extension — the VM infers the kind from it
            onLoaded(displayName(context, uri), text)
        }
    }
    // Toy programs have no registered MIME type; accept anything and filter by
    // extension at load time (the parser rejects non-programs anyway).
    return remember(launcher) { { launcher.launch(arrayOf("*/*")) } }
}

@Composable
actual fun rememberFileSaver(
    onSaved: (message: String?) -> Unit,
): (String, String, String) -> Unit {
    val context = LocalContext.current
    // CreateDocument needs the text at launch time; stash it between the call and
    // the result callback.
    var pending: String? = null
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val text = pending
        pending = null
        if (uri != null && text != null) {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write((if (text.endsWith("\n")) text else text + "\n").toByteArray())
            }
            onSaved(displayName(context, uri))
        } else {
            onSaved(null)
        }
    }
    return remember(launcher) {
        { suggestedName: String, extension: String, text: String ->
            pending = text
            launcher.launch(suggestedName + extension)
        }
    }
}

/** The human file name behind a content:// Uri (for the "Saved X" / loaded name). */
private fun displayName(context: android.content.Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "program"
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
    }
    return name
}
