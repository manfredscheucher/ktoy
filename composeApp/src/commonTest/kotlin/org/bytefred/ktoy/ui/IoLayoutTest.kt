package org.bytefred.ktoy.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The I/O panel wraps its 16 cells into rows of 16, 8 or 4 by available width. */
class IoLayoutTest {
    @Test
    fun wideScreenShowsAllSixteenInOneRow() {
        // 16 * 40 + 15 * 6 = 730
        assertEquals(16, ioCellsPerRow(730.dp, 40.dp, 6.dp))
    }

    @Test
    fun phoneWidthFallsBackToEight() {
        assertEquals(8, ioCellsPerRow(390.dp, 40.dp, 6.dp))
    }

    @Test
    fun binaryCellsWrapSooner() {
        // 8 * 72 + 7 * 6 = 618 > 390, so 4 per row
        assertEquals(4, ioCellsPerRow(390.dp, 72.dp, 6.dp))
        assertEquals(8, ioCellsPerRow(620.dp, 72.dp, 6.dp))
    }

    @Test
    fun fourIsTheFloor() {
        assertEquals(4, ioCellsPerRow(100.dp, 72.dp, 6.dp))
    }
}
