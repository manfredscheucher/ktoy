package org.bytefred.ktoy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Snapshot table test: verifies the ported Toy CPU behaves byte-for-byte like
 * pytoy. The expected (acc, steps) values are produced by pytoy and are
 * authoritative — if the Kotlin port ever drifts from the Python reference,
 * one of these assertions fails.
 */
class SnapshotTest {

    private data class Snapshot(val acc: Int, val steps: Int)

    // Final ACC + total step count for each bundled ASM example, from pytoy.
    private val snapshot = mapOf(
        "fibonacci" to Snapshot(acc = 13, steps = 80),
        "fibonacci_rec" to Snapshot(acc = 3, steps = 446),
        "max3" to Snapshot(acc = 4, steps = 14),
        "max_array" to Snapshot(acc = 9, steps = 114),
        "multiply" to Snapshot(acc = 42, steps = 59),
        "sevenfold" to Snapshot(acc = 28, steps = 9),
        "sort3" to Snapshot(acc = 1, steps = 19),
        "sort_array" to Snapshot(acc = 1, steps = 1955),
        "sum3" to Snapshot(acc = 8, steps = 3),
        "sum_array" to Snapshot(acc = 25, steps = 89),
    )

    private val SAFETY_CAP = 200_000

    /** Run a machine to completion (or the safety cap) and return it. */
    private fun runToStop(machine: Machine) {
        var iterations = 0
        while (machine.step()) {
            iterations += 1
            assertTrue(
                iterations < SAFETY_CAP,
                "machine did not stop within $SAFETY_CAP iterations",
            )
        }
    }

    @Test
    fun asmExamplesMatchPytoySnapshot() {
        for ((name, expected) in snapshot) {
            val example = assertNotNull(
                EXAMPLES.firstOrNull { it.name == name && it.kind == ExampleKind.ASM },
                "no ASM example named '$name' found",
            )

            val result = assemble(example.source)
            assertTrue(
                result.errors.isEmpty(),
                "assembling '$name' produced errors: ${result.errors.joinToString("\n")}",
            )

            val machine = Machine(result.mem, result.dataAddrs)
            runToStop(machine)

            assertEquals(expected.acc, machine.acc, "'$name' final ACC mismatch")
            assertEquals(expected.steps, machine.stepCount, "'$name' step count mismatch")
        }
    }

    @Test
    fun toyoExamplesMatchAcc() {
        // TOYO images have no source symbols, so step counts differ from the ASM
        // runs; the machine code is identical, so the final ACC must match.
        val expectedAcc = mapOf(
            "sum3 (bytecode)" to 8,
            "fibonacci (bytecode)" to 13,
        )

        for ((name, acc) in expectedAcc) {
            val example = assertNotNull(
                EXAMPLES.firstOrNull { it.name == name && it.kind == ExampleKind.TOYO },
                "no TOYO example named '$name' found",
            )

            val mem = parseToyo(example.source)
            // dataAddrs only seed the 'touched' set; they don't affect execution.
            val machine = Machine(mem, emptySet())
            runToStop(machine)

            assertEquals(acc, machine.acc, "'$name' final ACC mismatch")
        }
    }
}
