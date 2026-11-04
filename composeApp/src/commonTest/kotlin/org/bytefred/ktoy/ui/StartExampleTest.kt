package org.bytefred.ktoy.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The app opens with the 00_stop example. */
class StartExampleTest {

    @Test
    fun startsWithTheStopExample() {
        val vm = SimViewModel()
        vm.loadStartExample()
        assertEquals(START_EXAMPLE, vm.title)
        assertTrue(vm.hasSource, "the assembly column is there")
        vm.step()
        vm.step()
        assertTrue(vm.machine!!.stopped)
    }
}
