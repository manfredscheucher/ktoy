package org.bytefred.ktoy.ui

import java.util.prefs.Preferences

private val prefs: Preferences? =
    try { Preferences.userRoot().node("org/bytefred/ktoy") } catch (e: Exception) { null }

actual fun loadPref(key: String): String? = try { prefs?.get(key, null) } catch (e: Exception) { null }

actual fun savePref(key: String, value: String) {
    try { prefs?.put(key, value); prefs?.flush() } catch (e: Exception) { }
}
