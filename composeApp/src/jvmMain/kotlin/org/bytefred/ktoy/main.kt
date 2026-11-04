package org.bytefred.ktoy

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Desktop
import org.bytefred.ktoy.ui.App

/**
 * Desktop (JVM) entry point. Opens a single window hosting the shared [App].
 */
fun main() = application {
    // a close request goes through the app first ("Save changes?")
    var closeRequests by remember { mutableIntStateOf(0) }
    // the same for the macOS app menu's Quit / Cmd+Q, which never reaches the window
    DisposableEffect(Unit) {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf {
            it.isSupported(Desktop.Action.APP_QUIT_HANDLER)
        } else null
        desktop?.setQuitHandler { _, response ->
            response.cancelQuit()
            closeRequests++
        }
        onDispose { desktop?.setQuitHandler(null) }
    }
    Window(
        onCloseRequest = { closeRequests++ },
        title = "ktoy",
        state = rememberWindowState(width = 450.dp, height = 900.dp),
    ) {
        App(closeRequests, onClose = ::exitApplication)
    }
}
