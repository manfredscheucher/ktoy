package org.bytefred.ktoy.ui

/**
 * Tiny persistent key/value store for UI preferences (e.g. "don't show the
 * welcome dialog again"). Ported from PoCoView's loadPref/savePref; ktoy adds
 * the Android and iOS actuals. Failures are swallowed: a preference that can't
 * be read or written just falls back to the default.
 */
expect fun loadPref(key: String): String?

expect fun savePref(key: String, value: String)

// the View settings (SimViewModel loads them, the View menu saves them)
internal const val LINE_NUMBERS_PREF = "lineNumbers"
internal const val AUTO_SCROLL_PREF = "autoScroll"
internal const val LAYOUT_PREF = "layout"
