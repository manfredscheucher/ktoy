package org.bytefred.ktoy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ports pytoy's test_format.py / test_io.py for the pure I/O helpers: formatByte
 * (the four display modes) and pollingInput (ready-poll detection). The Compose
 * IoPanel is built on these, so pinning them down covers the I/O logic that can
 * be tested without a GUI.
 */
class FormatTest {

    @Test
    fun formatDecimal() {
        assertEquals("0", formatByte(0, IoFormat.DECIMAL))
        assertEquals("255", formatByte(255, IoFormat.DECIMAL))
        assertEquals("7", formatByte(7, IoFormat.DECIMAL))
    }

    @Test
    fun formatBinaryIsAlways8Wide() {
        assertEquals("00000000", formatByte(0, IoFormat.BINARY))
        assertEquals("00000111", formatByte(7, IoFormat.BINARY))
        assertEquals("11111111", formatByte(255, IoFormat.BINARY))
    }

    @Test
    fun formatHexIsUppercase2Wide() {
        assertEquals("00", formatByte(0, IoFormat.HEX))
        assertEquals("0F", formatByte(15, IoFormat.HEX))
        assertEquals("FF", formatByte(255, IoFormat.HEX))
    }

    @Test
    fun formatAsciiPrintableVsDot() {
        assertEquals("H", formatByte(72, IoFormat.ASCII))
        assertEquals(" ", formatByte(32, IoFormat.ASCII))
        assertEquals("~", formatByte(126, IoFormat.ASCII))
        // a real "." (code 46) stays a dot…
        assertEquals(".", formatByte(46, IoFormat.ASCII))
        // …but control codes / 127 / high bytes show as the middle dot "·"
        assertEquals("·", formatByte(0, IoFormat.ASCII))
        assertEquals("·", formatByte(31, IoFormat.ASCII))
        assertEquals("·", formatByte(127, IoFormat.ASCII))
        assertEquals("·", formatByte(200, IoFormat.ASCII))
    }

    @Test
    fun formatMasksToByte() {
        assertEquals("1", formatByte(257, IoFormat.DECIMAL))   // 257 & 0xFF
    }

    @Test
    fun pollingInputTrueOnLoadReady() {
        // LOAD (opcode 20) of address 255 (ready) at pc=0
        val mem = IntArray(256)
        mem[0] = 20
        mem[1] = IO_READY
        assertTrue(pollingInput(mem, 0))
    }

    @Test
    fun pollingInputFalseForOtherLoads() {
        val mem = IntArray(256)
        mem[0] = 20
        mem[1] = 10          // LOAD of an ordinary address, not ready
        assertFalse(pollingInput(mem, 0))
    }

    @Test
    fun pollingInputFalseForNonLoad() {
        val mem = IntArray(256)
        mem[0] = 21          // STORE
        mem[1] = IO_READY
        assertFalse(pollingInput(mem, 0))
    }
}
