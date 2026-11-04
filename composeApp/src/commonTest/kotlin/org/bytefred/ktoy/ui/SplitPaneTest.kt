package org.bytefred.ktoy.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** Dragging a divider between two panels (dragSplit). */
class SplitPaneTest {

    private fun assertWeights(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 1e-4f, "$expected vs $actual") }
    }

    @Test
    fun dragMovesOnlyTheTwoNeighbours() {
        // 3 equal panels in 900 px: 300 each; move the first divider 150 px right
        val w = dragSplit(listOf(1f, 1f, 1f), 0, 150f, 900f, 50f)
        assertWeights(listOf(1.5f, 0.5f, 1f), w)
        assertEquals(3f, w.sum(), 1e-4f)
    }

    @Test
    fun dragStopsAtTheMinimumSize() {
        // 2 panels of 300 px, min 50: the right one can shrink to 50 only
        assertWeights(listOf(1.8333f, 0.1667f), dragSplit(listOf(1f, 1f), 0, 1000f, 600f, 50f))
        assertWeights(listOf(0.1667f, 1.8333f), dragSplit(listOf(1f, 1f), 0, -1000f, 600f, 50f))
    }

    @Test
    fun keepsStackedMemoryDefaultRatio() {
        // stacked asm 1 : memory 2 in 600 px; drag the divider 100 px down
        assertWeights(listOf(1.5f, 1.5f), dragSplit(listOf(1f, 2f), 0, 100f, 600f, 50f))
    }

    @Test
    fun ignoresInvalidInput() {
        val w = listOf(1f, 1f)
        assertEquals(w, dragSplit(w, 1, 10f, 600f, 50f))   // no divider after the last panel
        assertEquals(w, dragSplit(w, 0, 10f, 0f, 50f))     // no space yet
    }
}
