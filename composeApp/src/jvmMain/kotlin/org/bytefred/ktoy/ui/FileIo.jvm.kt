package org.bytefred.ktoy.ui

import androidx.compose.runtime.Composable
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

// Desktop: a Swing file chooser + plain java.io read/write. A real path exists,
// so Reload is supported.
actual val fileLoadSupported: Boolean = true
actual val fileSaveSupported: Boolean = true
actual val fileReloadSupported: Boolean = true

@Composable
actual fun rememberFileLoader(onLoaded: (name: String, text: String) -> Unit): () -> Unit = {
    val chooser = JFileChooser().apply {
        dialogTitle = "Load program"
        fileFilter = FileNameExtensionFilter(
            "Toy programs (*.toys, *.toyo, *.toyc)", "toys", "toyo", "toyc")
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val f = chooser.selectedFile
        // full name incl. extension — the VM infers the program kind from it
        onLoaded(f.name, f.readText())
    }
}

@Composable
actual fun rememberFileSaver(
    onSaved: (message: String?) -> Unit,
): (String, String, String) -> Unit = { suggestedName, extension, text ->
    val chooser = JFileChooser().apply {
        dialogTitle = "Save program"
        selectedFile = File(suggestedName + extension)
        fileFilter = FileNameExtensionFilter(
            "*$extension", extension.removePrefix("."))
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var f = chooser.selectedFile
        if (!f.name.lowercase().endsWith(extension)) f = File(f.path + extension)
        f.writeText(if (text.endsWith("\n")) text else text + "\n")
        onSaved(f.name)
    } else {
        onSaved(null)
    }
}
