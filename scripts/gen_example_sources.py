#!/usr/bin/env python3
"""
Generate ktoy's ExampleSources.kt from the pytoy example programs.

The Kotlin app has no filesystem access to pytoy's examples/, so the bundled
programs are embedded as Kotlin strings. This regenerates that file from the
canonical .toys / .toyo sources in the sibling pytoy checkout, so the two never
drift. pytoy is the source of truth; this generator lives in ktoy (the consumer)
so pytoy stays unaware of ktoy.

pytoy is located as a sibling of this repo (../pytoy), overridable with the
PYTOY env var:
    python3 scripts/gen_example_sources.py
    PYTOY=/path/to/pytoy python3 scripts/gen_example_sources.py

Writes ExampleSources.kt next to the other core sources in this repo.
"""

import os

KTOY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PYTOY = os.environ.get("PYTOY", os.path.join(os.path.dirname(KTOY), "pytoy"))
ASM_DIR = os.path.join(PYTOY, "examples", "asm")
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


def main():
    if not os.path.isdir(ASM_DIR):
        raise SystemExit(
            f"pytoy examples not found at {ASM_DIR}. "
            f"Clone pytoy next to ktoy, or set PYTOY=/path/to/pytoy.")

    lines = [
        "package org.bytefred.ktoy.core",
        "",
        "/**",
        " * The bundled example programs, embedded as strings so they ship inside",
        " * the app (and the tests) with no filesystem access.",
        " *",
        " * GENERATED from pytoy examples/ by scripts/gen_example_sources.py — do",
        " * not edit by hand; regenerate when the .toys / .toyo sources change.",
        " */",
        "",
        "data class Example(val name: String, val kind: ExampleKind, val source: String)",
        "",
        "enum class ExampleKind { ASM, TOYO }",
        "",
        "val EXAMPLES: List<Example> = listOf(",
    ]
    # The full programs (examples/asm/03-programs/) plus the deterministic I/O
    # demos (examples/asm/04-io/). The 01-basics/ and 02-extended/ folders are
    # per-opcode / concept demos meant for the pytoy repo, not the ktoy app, so
    # they stay out of the embedded list. File names are taken verbatim (no
    # hardcoded list), so renames in pytoy flow through on the next regenerate.
    prog_dir = os.path.join(ASM_DIR, "03-programs")
    for f in sorted(os.listdir(prog_dir)):
        if not f.endswith(".toys"):
            continue
        name = f[:-5]
        src = open(os.path.join(prog_dir, f)).read()
        lines.append(f'    Example("{name}", ExampleKind.ASM, '
                     f'{kotlin_triple_string(src)}),')
    # Only the deterministic 04-io demos: they finish on their own. The
    # input-driven ones (poll_demo, greet_name, sort_array_io, pin_check) block
    # on user-supplied io cells and would never halt headless, so skip them.
    io_dir = os.path.join(ASM_DIR, "04-io")
    io_deterministic = {
        "countdown", "fibonacci_io", "hello_world",
        "numbers", "numbers_ascii", "numbers_hex",
    }
    for f in sorted(os.listdir(io_dir)):
        if not f.endswith(".toys"):
            continue
        name = f[:-5]
        if name not in io_deterministic:
            continue
        src = open(os.path.join(io_dir, f)).read()
        lines.append(f'    Example("{name}", ExampleKind.ASM, '
                     f'{kotlin_triple_string(src)}),')
    for f in sorted(os.listdir(TOYO_DIR)):
        if not f.endswith(".toyo"):
            continue
        name = f[:-5]
        src = open(os.path.join(TOYO_DIR, f)).read()
        lines.append(f'    Example("{name} (bytecode)", ExampleKind.TOYO, '
                     f'{kotlin_triple_string(src)}),')
    lines.append(")")
    lines.append("")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as fh:
        fh.write("\n".join(lines))
    print(f"wrote {OUT} ({len(lines)} lines, "
          f"{sum(1 for l in lines if l.startswith('    Example'))} examples)")


if __name__ == "__main__":
    main()
