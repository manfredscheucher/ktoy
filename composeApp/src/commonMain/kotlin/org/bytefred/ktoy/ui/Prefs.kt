package org.bytefred.ktoy.ui

/**
 * Tiny persistent key/value store for UI preferences (e.g. "don't show the
 * welcome dialog again"). Ported from PoCoView's loadPref/savePref; ktoy adds
 * the Android and iOS actuals. Failures are swallowed: a preference that can't
 * be read or written just falls back to the default.
 */
expect fun loadPref(key: String): String?

expect fun savePref(key: String, value: String)
