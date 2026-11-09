#!/usr/bin/env python3
"""
Generate ktoy's ExampleSources.kt from the example programs in examples/.

The Kotlin app has no filesystem access to examples/, so the bundled programs
are embedded as Kotlin strings. examples/ is the source of truth (it was copied
from pytoy, which is paused; ktoy no longer depends on a pytoy checkout).

Three kinds of example are embedded (see the generated `ExampleKind`):

  - ASM  — assembly sources (examples/asm/<sub>/*.toys). To load: assemble.
  - C    — C sources (examples/c/<sub>/*.toyc). To load: COMPILE first, then
           assemble. Which compilers accept a C example is decided at runtime
           in Kotlin (`detectCompilers`), with ktoy's own compilers.
  - TOYO — pre-compiled bytecode listings (examples/toyo/*.toyo). To load: parse.

Every example states in its header whether it uses the I/O cells:
`# io: yes` / `# io: no` (`// io: ...` in C). It becomes `Example.io`.

    python3 scripts/gen_example_sources.py

Writes ExampleSources.kt next to the other core sources in this repo.
"""

import os
import re

KTOY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXAMPLES_DIR = os.path.join(KTOY, "examples")
ASM_DIR = os.path.join(EXAMPLES_DIR, "asm")
C_DIR = os.path.join(EXAMPLES_DIR, "c")
TOYO_DIR = os.path.join(EXAMPLES_DIR, "toyo")
OUT = os.path.join(KTOY, "composeApp", "src", "commonMain", "kotlin",
                   "org", "bytefred", "ktoy", "core", "ExampleSources.kt")

# The io header: "# io: yes", "// io: no", or in a .toyo listing
# "#        # io: yes".
IO_HEADER = re.compile(r"^(?:#|//)(?:\s+#)?\s*io:\s*(yes|no)\s*$", re.M)


def io_flag(path, src):
    """The example's io header as a Kotlin literal; fail loudly if it's missing."""
    m = IO_HEADER.search(src)
    if not m:
        raise SystemExit(f"{path}: no 'io: yes' / 'io: no' header")
    return "true" if m.group(1) == "yes" else "false"


def kotlin_triple_string(s):
    """Embed s in a Kotlin raw (triple-quote) string. Raw strings can't contain
    a literal triple-quote or a $ (interpolation), so guard against both — the
    current examples have neither, but fail loudly if that ever changes."""
    if '"""' in s:
        raise ValueError("example contains a triple-quote; can't embed raw")
    if '$' in s:
        raise ValueError("example contains '$'; would break Kotlin interpolation")
    return f'"""{s}"""'


def main():
    if not os.path.isdir(ASM_DIR):
        raise SystemExit(f"examples not found at {ASM_DIR}.")

    lines = [
        "package org.bytefred.ktoy.core",
        "",
        "/**",
        " * The bundled example programs, embedded as strings so they ship inside",
        " * the app (and the tests) with no filesystem access.",
        " *",
        " * Three kinds (see [ExampleKind]):",
        " *   - ASM  — assembly source (*.toys); to load: assemble.",
        " *   - C    — C source (*.toyc); to load: compile (with a compiler from",
        " *            [Example.compilers]) then assemble. `source` holds the C text",
        " *            itself, so the UI can show the C|asm view.",
        " *   - TOYO — pre-compiled bytecode listing (*.toyo); to load: parse.",
        " *",
        " * Each example also carries its repo [Example.subfolder] (e.g. \"01-basics\",",
        " * \"04-io\"; \"\" for bytecode), so the browser UI can group by subfolder like",
        " * pytoy's ExamplesDialog instead of a flat list.",
        " *",
        " * For C examples, [Example.compilers] names the compiler(s) that accept the",
        " * source — a subset of \"minicc\", \"toycc\". minicc rejects recursion (and",
        " * other constructs toycc supports), so a recursive example lists only",
        " * \"toycc\" while a simple one lists both. It is filled in at runtime by",
        " * [detectCompilers]; the list is empty for ASM/TOYO.",
        " *",
        " * [Example.io] comes from the example's `io: yes` / `io: no` header: whether",
        " * the program uses the I/O cells. Loading an example shows the I/O panel",
        " * exactly when it is true.",
        " *",
        " * GENERATED from examples/ by scripts/gen_example_sources.py — do not edit",
        " * by hand; regenerate when the .toys / .toyc / .toyo sources change.",
        " */",
        "",
        "enum class ExampleKind { ASM, C, TOYO }",
        "",
        "data class Example(",
        "    val name: String,",
        "    val kind: ExampleKind,",
        "    val source: String,",
        "    /** C-only: compilers that accept this source (\"minicc\"/\"toycc\"); empty otherwise. */",
        "    val compilers: List<String> = emptyList(),",
        "    /** Repo subfolder this came from (e.g. \"01-basics\", \"04-io\"); \"\" for bytecode. */",
        "    val subfolder: String = \"\",",
        "    /** The example's `io:` header (uses the I/O cells); null for a program that isn't a bundled example. */",
        "    val io: Boolean? = null,",
        ")",
        "",
        "/**",
        " * Every bundled example as written, without compiling anything: C examples",
        " * still have an empty [Example.compilers]. App start reads this, so it never",
        " * compiles the C examples.",
        " */",
        "val EXAMPLE_SOURCES: List<Example> = listOf(",
    ]

    n_examples = 0

    # ── ASM ──────────────────────────────────────────────────────────────────
    # Every subfolder of examples/asm/ (01-basics, 02-extended, 03-programs,
    # 04-io). That includes the input-driven I/O demos (pin_check, greet_name,
    # ...): in the app the user types the input. Only tests have to skip those
    # (they never halt headless). File names are taken verbatim.
    for sub in sorted(os.listdir(ASM_DIR)):
        folder = os.path.join(ASM_DIR, sub)
        if not os.path.isdir(folder):
            continue
        for f in sorted(os.listdir(folder)):
            if not f.endswith(".toys"):
                continue
            name = f[:-5]
            src = open(os.path.join(folder, f)).read()
            lines.append(f'    Example("{name}", ExampleKind.ASM, '
                         f'{kotlin_triple_string(src)}, '
                         f'subfolder = "{sub}", io = {io_flag(f, src)}),')
            n_examples += 1

    # ── TOYO ─────────────────────────────────────────────────────────────────
    for f in sorted(os.listdir(TOYO_DIR)):
        if not f.endswith(".toyo"):
            continue
        name = f[:-5]
        src = open(os.path.join(TOYO_DIR, f)).read()
        lines.append(f'    Example("{name} (bytecode)", ExampleKind.TOYO, '
                     f'{kotlin_triple_string(src)}, io = {io_flag(f, src)}),')
        n_examples += 1

    # ── C ────────────────────────────────────────────────────────────────────
    # Every subfolder of examples/c/ (02-extended, 03-programs, 04-io). The
    # compilers that accept each one are filled in at runtime (see the end of
    # EXAMPLES), and a C example no compiler accepts is dropped there. The name
    # keeps the .toyc extension (as elsewhere) so the "Generated by toycc from
    # <name>" header agrees if the UI recompiles it.
    for sub in sorted(os.listdir(C_DIR)):
        folder = os.path.join(C_DIR, sub)
        if not os.path.isdir(folder):
            continue
        for f in sorted(os.listdir(folder)):
            if not f.endswith(".toyc"):
                continue
            name = f  # keep extension, e.g. "fibonacci_rec.toyc"
            src = open(os.path.join(folder, f)).read()
            lines.append(f'    Example("{name}", ExampleKind.C, '
                         f'{kotlin_triple_string(src)}, '
                         f'subfolder = "{sub}", io = {io_flag(f, src)}),')
            n_examples += 1

    lines += [
        ")",
        "",
        "/**",
        " * The examples the browser offers. Lazy, because it compiles every C example",
        " * to find which compilers accept it; a C example no compiler accepts is dropped.",
        " */",
        "val EXAMPLES: List<Example> by lazy {",
        "    EXAMPLE_SOURCES.mapNotNull { ex ->",
        "        if (ex.kind != ExampleKind.C) ex",
        "        else detectCompilers(ex.source).takeIf { it.isNotEmpty() }?.let { ex.copy(compilers = it) }",
        "    }",
        "}",
        "",
    ]

    # ── grouping helpers for a browser UI ─────────────────────────────────────
    # Mirrors pytoy's grouped_examples / ExamplesDialog: a C example appears under
    # EVERY compiler that accepts it, so a "both" example shows in both C lists.
    # Insertion order of the keys is the display order.
    #
    # groupedExamples()     — flat: category -> List<Example>.
    # groupedExamplesTree() — nested: category -> subfolder -> List<Example>,
    #                         the tree the ExampleListScreen renders. A C leaf
    #                         under "C (minicc)" carries an Example whose
    #                         `compilers` is narrowed to just minicc (so load()
    #                         opens it on that compiler); likewise for toycc.
    lines += [
        "/**",
        " * Examples grouped for a browser UI, keyed by display category:",
        " *   \"Assembly\", \"C (minicc)\", \"C (toycc)\", \"Bytecode\".",
        " *",
        " * A C example appears under EVERY compiler that accepts it (an example",
        " * both accept shows in both \"C (minicc)\" and \"C (toycc)\"). Empty",
        " * categories are omitted. Mirrors pytoy's grouped_examples.",
        " */",
        "fun groupedExamples(): Map<String, List<Example>> {",
        "    val groups = linkedMapOf<String, MutableList<Example>>()",
        "    fun add(key: String, ex: Example) {",
        "        groups.getOrPut(key) { mutableListOf() }.add(ex)",
        "    }",
        "    for (ex in EXAMPLES) {",
        "        when (ex.kind) {",
        "            ExampleKind.ASM -> add(\"Assembly\", ex)",
        "            ExampleKind.TOYO -> add(\"Bytecode\", ex)",
        "            ExampleKind.C -> {",
        "                if (\"minicc\" in ex.compilers) add(\"C (minicc)\", ex)",
        "                if (\"toycc\" in ex.compilers) add(\"C (toycc)\", ex)",
        "            }",
        "        }",
        "    }",
        "    // Fixed display order; drop any category with no examples.",
        "    val order = listOf(\"Assembly\", \"Bytecode\", \"C (minicc)\", \"C (toycc)\")",
        "    val out = linkedMapOf<String, List<Example>>()",
        "    for (key in order) groups[key]?.let { out[key] = it }",
        "    return out",
        "}",
        "",
        "/**",
        " * The example tree for the browser UI: category -> subfolder -> examples,",
        " * mirroring pytoy's ExamplesDialog. Top-level categories, in display order:",
        " *   \"Assembly\"    — .toys, children grouped by subfolder (01-basics, ...).",
        " *   \"C (minicc)\"  — C examples minicc accepts, grouped by subfolder.",
        " *   \"C (toycc)\"   — C examples toycc accepts, grouped by subfolder.",
        " *   \"Bytecode\"    — .toyo listings under a single \"\" subfolder (flat).",
        " *",
        " * A C example both compilers accept appears under BOTH C nodes; which node",
        " * a leaf sits under IS the chosen compiler. To make picking a leaf load it",
        " * on THAT compiler (via load()'s compiler preference), a C leaf's Example is",
        " * narrowed so `compilers` holds only the node's compiler.",
        " */",
        "fun groupedExamplesTree(): Map<String, Map<String, List<Example>>> {",
        "    val out = linkedMapOf<String, Map<String, List<Example>>>()",
        "",
        "    fun bucket(predicate: (Example) -> Boolean, pick: (Example) -> Example):",
        "        Map<String, List<Example>> {",
        "        val bySub = linkedMapOf<String, MutableList<Example>>()",
        "        for (ex in EXAMPLES) if (predicate(ex)) {",
        "            bySub.getOrPut(ex.subfolder) { mutableListOf() }.add(pick(ex))",
        "        }",
        "        return bySub",
        "    }",
        "",
        "    // Assembly: one subfolder bucket per subfolder, preserving EXAMPLES order.",
        "    val asm = bucket({ it.kind == ExampleKind.ASM }, { it })",
        "    if (asm.isNotEmpty()) out[\"Assembly\"] = asm",
        "",
        "    // Bytecode: flat — a single \"\" subfolder holding every .toyo listing.",
        "    val toyo = EXAMPLES.filter { it.kind == ExampleKind.TOYO }",
        "    if (toyo.isNotEmpty()) out[\"Bytecode\"] = linkedMapOf(\"\" to toyo)",
        "",
        "    // C: one top-level node per compiler, each example narrowed to that",
        "    // compiler and bucketed by subfolder; a compiler node is omitted if it",
        "    // accepts nothing.",
        "    for (compiler in listOf(\"minicc\", \"toycc\")) {",
        "        val bySub = bucket(",
        "            { it.kind == ExampleKind.C && compiler in it.compilers },",
        "            { it.copy(compilers = listOf(compiler)) },",
        "        )",
        "        if (bySub.isNotEmpty()) out[\"C ($compiler)\"] = bySub",
        "    }",
        "",
        "    return out",
        "}",
        "",
    ]

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as fh:
        fh.write("\n".join(lines))
    print(f"wrote {OUT} ({len(lines)} lines, {n_examples} examples)")


if __name__ == "__main__":
    main()
