package org.bytefred.ktoy.ui

import platform.Foundation.NSUserDefaults

actual fun loadPref(key: String): String? =
    NSUserDefaults.standardUserDefaults.stringForKey("ktoy.$key")

actual fun savePref(key: String, value: String) {
    NSUserDefaults.standardUserDefaults.setObject(value, forKey = "ktoy.$key")
}
