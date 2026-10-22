#!/usr/bin/env python3
"""
Generate ktoy's ExampleSources.kt from the pytoy example programs.

The Kotlin app has no filesystem access to pytoy's examples/, so the bundled
programs are embedded as Kotlin strings. This regenerates that file from the
canonical .toys / .toyc / .toyo sources in the sibling pytoy checkout, so the
two never drift. pytoy is the source of truth; this generator lives in ktoy
(the consumer) so pytoy stays unaware of ktoy.

Three kinds of example are embedded (see the generated `ExampleKind`):

  - ASM  — assembly sources (*.toys). To load: assemble.
  - C    — C sources (*.toyc). To load: COMPILE first (with a compiler the
           source accepts), then assemble. Each C example carries its C source
           text (for the C|asm view) plus a `compilers` list naming which
           compiler(s) accept it ("minicc" and/or "toycc"). Compatibility is
           computed HERE by actually trying pytoy's minicc and toycc on the
           source and catching their errors (same logic as pytoy's
           examples_index._detect_compilers) — accurate, not a heuristic.
  - TOYO — pre-compiled bytecode listings (*.toyo). To load: parse directly.

pytoy is located as a sibling of this repo (../pytoy), overridable with the
PYTOY env var:
    python3 scripts/gen_example_sources.py
    PYTOY=/path/to/pytoy python3 scripts/gen_example_sources.py

Writes ExampleSources.kt next to the other core sources in this repo.
"""

import os
import sys

KTOY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PYTOY = os.environ.get("PYTOY", os.path.join(os.path.dirname(KTOY), "pytoy"))
ASM_DIR = os.path.join(PYTOY, "examples", "asm")
C_DIR = os.path.join(PYTOY, "examples", "c")
TOYO_DIR = os.path.join(PYTOY, "examples", "toyo")
OUT = os.path.join(KTOY, "composeApp", "src", "commonMain", "kotlin",
                   "org", "bytefred", "ktoy", "core", "ExampleSources.kt")


def kotlin_triple_string(s):
    """Embed s in a Kotlin raw (triple-quote) string. Raw strings can't contain
    a literal triple-quote or a $ (interpolation), so guard against both — the
    current examples have neither, but fail loudly if that ever changes."""
    if '"""' in s:
        raise ValueError("example contains a triple-quote; can't embed raw")
    if '$' in s:
        raise ValueError("example contains '$'; would break Kotlin interpolation")
    return f'"""{s}"""'


# ── compiler compatibility probing ───────────────────────────────────────────
# Try pytoy's minicc and toycc on the source and catch their errors. This mirrors
# pytoy/examples_index.py::_detect_compilers, but runs here (the generator runs
# against the pytoy checkout, so the result can be baked into the Kotlin data).

def _minicc_accepts(src):
    from pytoy.minicc import compile_source, MiniError
    try:
        compile_source(src, "probe")
        return True
    except MiniError:
        return False
    except Exception:
        return False


def _toycc_accepts(src):
    from pytoy.compiler import compile_source, CompileError
    try:
        compile_source(src, "probe")
        return True
    except CompileError:
        return False
    except Exception:
        return False


def detect_compilers(src):
    """The compilers that accept `src`, a subset of ("minicc", "toycc") in that
    order. minicc first then toycc, matching pytoy."""
    result = []
    if _minicc_accepts(src):
        result.append("minicc")
    if _toycc_accepts(src):
        result.append("toycc")
    return result


def kotlin_string_list(items):
    """A Kotlin `listOf("a", "b")` literal from a list of plain strings."""
    if not items:
        return "emptyList()"
    inner = ", ".join(f'"{x}"' for x in items)
    return f"listOf({inner})"


def main():
    if not os.path.isdir(ASM_DIR):
        raise SystemExit(
            f"pytoy examples not found at {ASM_DIR}. "
            f"Clone pytoy next to ktoy, or set PYTOY=/path/to/pytoy.")

    # pytoy's compilers (minicc, toycc) are needed to probe compiler compat.
    sys.path.insert(0, PYTOY)

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
        " * \"toycc\" while a simple one lists both. The list is empty for ASM/TOYO.",
        " *",
        " * GENERATED from pytoy examples/ by scripts/gen_example_sources.py — do",
        " * not edit by hand; regenerate when the .toys / .toyc / .toyo sources or",
        " * the compilers change.",
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
        ")",
        "",
        "val EXAMPLES: List<Example> = listOf(",
    ]

    n_examples = 0

    # ── ASM ──────────────────────────────────────────────────────────────────
    # Every subfolder of examples/asm/ (01-basics, 02-extended, 03-programs,
    # 04-io), so the app shows the same tree as pytoy. That includes the
    # input-driven I/O demos (pin_check, greet_name, ...): in the app the user
    # types the input. Only tests have to skip those (they never halt headless).
    # File names are taken verbatim, so renames in pytoy flow through on the
    # next regenerate.
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
                         f'subfolder = "{sub}"),')
            n_examples += 1

    # ── TOYO ─────────────────────────────────────────────────────────────────
    for f in sorted(os.listdir(TOYO_DIR)):
        if not f.endswith(".toyo"):
            continue
        name = f[:-5]
        src = open(os.path.join(TOYO_DIR, f)).read()
        lines.append(f'    Example("{name} (bytecode)", ExampleKind.TOYO, '
                     f'{kotlin_triple_string(src)}),')
        n_examples += 1

    # ── C ────────────────────────────────────────────────────────────────────
    # Every subfolder of examples/c/ (02-extended, 03-programs, 04-io), each
    # example tagged with the compiler(s) that accept it. Loose files directly
    # in examples/c/ are compiled outputs, not C, and are ignored. The name
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
            compilers = detect_compilers(src)
            if not compilers:
                # No compiler accepts it: nothing the UI could do, so skip.
                print(f"  (skipping {sub}/{f}: no compiler accepts it)")
                continue
            lines.append(f'    Example("{name}", ExampleKind.C, '
                         f'{kotlin_triple_string(src)}, '
                         f'compilers = {kotlin_string_list(compilers)}, '
                         f'subfolder = "{sub}"),')
            n_examples += 1

    lines.append(")")
    lines.append("")

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
