package org.bytefred.ktoy.ui

import androidx.compose.runtime.Composable

/**
 * Handle the system "back" gesture. Only Android has one, so this is a
 * platform shim: the Android actual delegates to Compose's BackHandler; the
 * JVM/web actuals are no-ops (those platforms close the list via the UI).
 *
 * Kept as our own expect/actual rather than the `ui-backhandler` CMP module,
 * because that module drags in androidx.compose.ui 1.12.1, which demands
 * AGP 9.1+. This shim keeps us on AGP 8.13.2 (matching the sibling apps).
 */
@Composable
expect fun AppBackHandler(enabled: Boolean, onBack: () -> Unit)
