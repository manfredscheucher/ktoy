package org.bytefred.ktoy.ui

import androidx.compose.ui.graphics.Color

/**
 * The highlight colours, ported from pytoy. A cell/line can carry several roles
 * at once; [HighlightRow] draws all the active colours as segments of the row
 * background.
 */
val HL_SELECTED = Color(0xFFFFCC66)   // orange: the address the user tapped
val HL_PC       = Color(0xFFFFFFAA)   // yellow: the byte(s) of the current instruction
val HL_ARG      = Color(0xFFAAFFAA)   // green:  the address this instruction reads/writes
val HL_CHANGE   = Color(0xFF99CCFF)   // blue:   the cell/ACC written in the last step
val HL_DATA     = Color(0xFFFFCCCC)   // light red: a data byte now differs from the original
val HL_CODE     = Color(0xFFFF6666)   // strong red: a code byte was overwritten at runtime
