package org.bytefred.ktoy

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.bytefred.ktoy.ui.App

/**
 * Desktop (JVM) entry point. Opens a single window hosting the shared [App].
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ktoy",
        state = rememberWindowState(width = 450.dp, height = 900.dp),
    ) {
        App()
    }
}
