package org.bytefred.ktoy.ui

import androidx.compose.runtime.Composable

// iOS has no in-app system "back" gesture to intercept here — the example list
// is closed via its own UI, like on desktop/web.
@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
