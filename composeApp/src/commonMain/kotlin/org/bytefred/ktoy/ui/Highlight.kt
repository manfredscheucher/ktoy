package org.bytefred.ktoy.ui

import androidx.compose.ui.graphics.Color

/**
 * The highlight colours, ported from pytoy. A cell/line can carry several roles
 * at once; the row is coloured by cycling through all the active colours (see
 * rowColours below), matching pytoy's per-character alternation idea — but here,
 * on a real UI, we draw them as segments of the row background.
 */
val HL_SELECTED = Color(0xFFFFCC66)   // orange: the address the user tapped
val HL_PC       = Color(0xFFFFFFAA)   // yellow: the byte(s) of the current instruction
val HL_ARG      = Color(0xFFAAFFAA)   // green:  the address this instruction reads/writes
val HL_CHANGE   = Color(0xFF99CCFF)   // blue:   the cell/ACC written in the last step
val HL_DATA     = Color(0xFFFFCCCC)   // light red: a data byte now differs from the original
val HL_CODE     = Color(0xFFFF6666)   // strong red: a code byte was overwritten at runtime

/**
 * The active highlight colours for a memory cell, in pytoy's precedence order,
 * so a cell with several roles shows all of them. Order: selected, PC, change,
 * argument. (Red overwrite/data colours are handled in the source panel.)
 */
fun memoryCellColours(
    addr: Int,
    selectedAddr: Int?,
    pcBytes: Set<Int>,
    argAddr: Int?,
    changedCell: Int?,
    stopped: Boolean,
): List<Color> {
    // order matches pytoy's stripe order: selected, PC, argument, change
    val colours = mutableListOf<Color>()
    if (addr == selectedAddr) colours.add(HL_SELECTED)
    if (addr in pcBytes) colours.add(HL_PC)
    if (addr == argAddr) colours.add(HL_ARG)
    if (!stopped && addr == changedCell) colours.add(HL_CHANGE)
    return colours
}
