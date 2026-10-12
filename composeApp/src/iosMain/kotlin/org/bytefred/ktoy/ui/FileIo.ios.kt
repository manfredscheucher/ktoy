package org.bytefred.ktoy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dataUsingEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

// iOS: load via UIDocumentPickerViewController, save/share via
// UIActivityViewController (writes a temp file, then presents the share sheet).
// A picked file is a one-shot security-scoped URL, not a stable path we re-read,
// so Reload is not supported.
actual val fileLoadSupported: Boolean = true
actual val fileSaveSupported: Boolean = true
actual val fileReloadSupported: Boolean = false

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFileLoader(onLoaded: (name: String, text: String) -> Unit): () -> Unit {
    val viewController = LocalUIViewController.current
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                @Suppress("UNCHECKED_CAST")
                val url = (didPickDocumentsAtURLs as? List<NSURL>)?.firstOrNull()
                if (url != null) {
                    url.startAccessingSecurityScopedResource()
                    try {
                        val text = NSString.stringWithContentsOfURL(
                            url, NSUTF8StringEncoding, null) ?: ""
                        // full name incl. extension — the VM infers the kind from it
                        onLoaded(url.lastPathComponent ?: "program", text)
                    } finally {
                        url.stopAccessingSecurityScopedResource()
                    }
                }
            }

            override fun documentPickerWasCancelled(
                controller: UIDocumentPickerViewController,
            ) {}
        }
    }
    return {
        // Toy programs have no registered UTI; accept any data file and let the
        // parser reject non-programs.
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
        picker.delegate = delegate
        picker.allowsMultipleSelection = false
        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@Composable
actual fun rememberFileSaver(
    onSaved: (message: String?) -> Unit,
): (suggestedName: String, extension: String, text: String) -> Unit {
    val viewController = LocalUIViewController.current
    return { suggestedName, extension, text ->
        val body = if (text.endsWith("\n")) text else text + "\n"
        val fileName = suggestedName + extension
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        // String -> NSData -> write (a direct String->NSString cast fails on K/N).
        val data = NSString.create(string = body)
            .dataUsingEncoding(NSUTF8StringEncoding)
        val ok = data?.writeToURL(url, atomically = true) ?: false
        if (ok) {
            val share = UIActivityViewController(
                activityItems = listOf(url), applicationActivities = null)
            viewController.presentViewController(share, animated = true, completion = null)
            onSaved(fileName)
        } else {
            onSaved(null)
        }
    }
}
