package org.bytefred.ktoy.ui

import androidx.compose.runtime.Composable

// The browser has no in-app system "back" — nothing to handle.
@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
