#!/usr/bin/env python3
"""
Dump pytoy minicc output for the focused snippets used in MiniCompilerTest, so
the Kotlin port can be hand-traced against the oracle (no Kotlin toolchain
needed). Durable companion to verify_minicc_goldens.py.
"""

import os
import sys

KTOY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PYTOY = os.environ.get("PYTOY", os.path.join(os.path.dirname(KTOY), "pytoy"))
sys.path.insert(0, PYTOY)
from pytoy.minicc import compile_source  # noqa: E402
from pytoy.assembler import assemble  # noqa: E402
from pytoy.simulator import simulate  # noqa: E402
import io
import contextlib


def wrap(body):
    return "int main(void) {\n" + body + "\n}\n"


SNIPPETS = {
    "arith_mul": wrap("int x=6; int y=7; return x * y;"),
    "neg": wrap("int x=5; return -x;"),
    "cmp_lt": wrap("int a=7; int b=12; int r; if (a < b) { r = b - a; } else { r = a - b; } return r;"),
    "while": wrap("int i=0; int s=0; while (i < 5) { s = s + i; i = i + 1; } return s;"),
    "array_init": wrap("int a[4] = {10, 20, 30, 40}; int i=2; return a[i];"),
    "array_rw": wrap("int a[3]; a[0]=5; a[1]=9; a[2]=1; int i=1; a[i] = a[i] + 100; return a[1];"),
    "sub_nested": ("int sub(int a, int b) { return a - b; }\n"
                   "int main(void) { return sub(20, sub(10, 3)); }\n"),
}


def run(src):
    asm = compile_source(src, "t.toyc")
    mem, listing, syms, data_addrs, errors, _ds = assemble(asm)
    assert errors == [], errors
    with contextlib.redirect_stdout(io.StringIO()):
        return simulate(mem, syms, data_addrs)


def main():
    for name, src in SNIPPETS.items():
        print(f"===== {name}  (ACC={run(src)}) =====")
        print(compile_source(src, "t.toyc"))


if __name__ == "__main__":
    main()
