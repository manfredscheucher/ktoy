package org.bytefred.ktoy.ui

import androidx.compose.runtime.Composable
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.FileReader
import org.w3c.files.get

// Browser: load via a hidden <input type=file> + FileReader; save via a Blob
// download. There is no stable path, so Reload is not supported (a picked File
// is a one-shot blob).
actual val fileLoadSupported: Boolean = true
actual val fileSaveSupported: Boolean = true
actual val fileReloadSupported: Boolean = false

@Composable
actual fun rememberFileLoader(onLoaded: (name: String, text: String) -> Unit): () -> Unit = {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = ".toys,.toyo,.toyc"
    input.onchange = {
        val file = input.files?.get(0)
        if (file != null) {
            val reader = FileReader()
            reader.onload = {
                val text = reader.result as? String ?: ""
                // full name incl. extension — the VM infers the kind from it
                onLoaded(file.name, text)
            }
            reader.readAsText(file)
        }
    }
    input.click()
}

@Composable
actual fun rememberFileSaver(
    onSaved: (message: String?) -> Unit,
): (String, String, String) -> Unit = { suggestedName, extension, text ->
    val body = if (text.endsWith("\n")) text else text + "\n"
    val blob = Blob(arrayOf(body), BlobPropertyBag(type = "text/plain"))
    val url = URL.createObjectURL(blob)
    val a = document.createElement("a") as HTMLAnchorElement
    a.href = url
    a.download = suggestedName + extension
    a.click()
    URL.revokeObjectURL(url)
    onSaved(suggestedName + extension)
}
