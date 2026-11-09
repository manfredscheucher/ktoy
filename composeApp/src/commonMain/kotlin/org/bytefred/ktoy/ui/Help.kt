package org.bytefred.ktoy.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.bytefred.ktoy.core.REFERENCE_NOTES
import org.bytefred.ktoy.core.referenceTable

/** The Help menu's pages, in menu order (About has its own dialog). */
enum class HelpPage(val title: String) {
    GENERAL("General"),
    INSTRUCTIONS("Instruction set"),
    C_COMPILERS("C compilers"),
}

/** One Help page in a [BigDialog]: plain text, monospace only for the instruction table. */
@Composable
fun HelpDialog(page: HelpPage, onDismiss: () -> Unit) {
    BigDialog(page.title, onDismiss, buttons = { TextButton(onClick = onDismiss) { Text("OK") } }) {
        val vScroll = rememberScrollState()
        ScrollbarBox(vScroll, Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.verticalScroll(vScroll).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (page) {
                    HelpPage.GENERAL -> GENERAL_TEXT.forEach { Paragraph(it) }
                    HelpPage.INSTRUCTIONS -> {
                        val hScroll = rememberScrollState()
                        Text(referenceTable(), style = MonoStyle, softWrap = false, modifier = Modifier.horizontalScroll(hScroll))
                        Paragraph(REFERENCE_NOTES)
                    }
                    HelpPage.C_COMPILERS -> {
                        Paragraph(C_INTRO)
                        Paragraph(MINI_C, bold = "Mini C (minicc)")
                        Paragraph(FULL_C, bold = "Full C (toycc)")
                    }
                }
            }
        }
    }
}

/** A paragraph; [bold] is its first words, set in bold. */
@Composable
private fun Paragraph(text: String, bold: String? = null) {
    Text(
        buildAnnotatedString {
            if (bold != null) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            append(text)
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}

internal val GENERAL_TEXT = listOf(
    "ktoy simulates the Toy CPU, an 8-bit computer with 256 bytes of memory and one register, " +
        "the accumulator (ACC). A program starts at address 0 and runs until stop; then ACC holds its result.",
    "The columns show the program in C, in assembly and as bytecode in memory. A click on a line marks " +
        "the same place in the other columns. Step runs one instruction, Run runs until stop (press Stop " +
        "to pause), Undo goes back one step and Reset starts over.",
    "Right-click a line (long-press on touch) to edit it or the whole column, or use Edit… in the C, " +
        "Assembly and Bytecode menus. Compile turns C into assembly and bytecode, Assemble turns assembly " +
        "into bytecode, and Disassemble turns the memory back into assembly. Before anything gets lost, " +
        "ktoy asks.",
    "Cells 240 to 254 are for input and output, cell 255 is the ready flag. View → I/O panel shows them " +
        "as text or numbers, which makes input and output easier to read and type.",
    "File → Examples has programs to start from, File → Empty starts from scratch, and File → Load… " +
        "and Save… read and write C, assembly or bytecode.",
)

internal const val C_INTRO = "Two compilers turn a small subset of C into Toy CPU assembly. Switch between them " +
    "with the button above the C column or in the C menu. When the program stops, ACC holds the value " +
    "main returns."

internal const val MINI_C = " is small and readable, made to see how a compiler works. It has int " +
    "variables, one-dimensional arrays, if/else, while, and functions without recursion. Every int " +
    "has 8 bits and wraps modulo 256. The comparisons use the most significant bit of the difference, " +
    "so they are correct when the operands differ by less than 128. Code and data must fit into 240 " +
    "bytes. A name declared in a { } block stays visible after it."

internal const val FULL_C = " extends mini C by recursion (with a stack in memory), pointers, " +
    "read() and write() for the I/O cells, compound assignments such as +=, and shifts by a variable " +
    "amount."
