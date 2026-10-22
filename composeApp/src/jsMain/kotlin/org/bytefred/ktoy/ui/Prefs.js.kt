package org.bytefred.ktoy.ui

actual fun loadPref(key: String): String? =
    try { kotlinx.browser.localStorage.getItem("ktoy.$key") } catch (e: Throwable) { null }

actual fun savePref(key: String, value: String) {
    try { kotlinx.browser.localStorage.setItem("ktoy.$key", value) } catch (e: Throwable) { }
}
