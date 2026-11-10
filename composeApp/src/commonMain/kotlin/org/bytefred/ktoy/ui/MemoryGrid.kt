package org.bytefred.ktoy.ui

/**
 * View -> Memory: how the memory panel shows the 256 bytes. SINGLE_COLUMN is the
 * default listing (address and value, decimal and binary, one per row). The
 * grids show only the decimal values, 16 or 8 per row and no addresses, like the
 * mini simulator's `-v` dump; the cell at the program counter carries a `*`.
 */
enum class MemoryView(val label: String, val columns: Int) {
    SINGLE_COLUMN("Single column", 1),
    COLUMNS_16("16 columns", 16),
    COLUMNS_8("8 columns", 8),
}

/** One grid cell as the mini simulator prints it: the value right-aligned in 3, then `*` at the PC, else a space. */
internal fun memoryGridCell(value: Int, isPc: Boolean): String =
    value.toString().padStart(3) + if (isPc) "*" else " "

/** The grid rows for [columns] values per row: 256 / [columns] rows of [memoryGridCell]s. */
internal fun memoryGridRows(mem: IntArray, pc: Int, columns: Int): List<String> =
    (0 until 256 step columns).map { start ->
        (start until start + columns).joinToString("") { memoryGridCell(mem[it], it == pc) }
    }
