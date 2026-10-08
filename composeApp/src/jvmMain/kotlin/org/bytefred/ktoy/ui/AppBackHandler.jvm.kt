package org.bytefred.ktoy.ui

import androidx.compose.runtime.Composable

// Desktop has no system "back" gesture — nothing to handle.
@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
