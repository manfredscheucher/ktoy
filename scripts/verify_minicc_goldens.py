#!/usr/bin/env python3
"""
Re-verify the embedded minicc goldens in MiniCExamples.kt against a fresh pytoy
minicc run (sanity check that the committed Kotlin fixtures still match the
oracle). Also compile a few focused snippets to confirm they are accepted and
report what pytoy produces, so a reviewer can eyeball the Kotlin port's expected
behaviour without a Kotlin toolchain.

    python3 scripts/verify_minicc_goldens.py
    PYTOY=/path/to/pytoy python3 scripts/verify_minicc_goldens.py
"""

import os
import re
import sys

KTOY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PYTOY = os.environ.get("PYTOY", os.path.join(os.path.dirname(KTOY), "pytoy"))
KT = os.path.join(KTOY, "composeApp", "src", "commonTest", "kotlin",
                  "org", "bytefred", "ktoy", "core", "MiniCExamples.kt")

sys.path.insert(0, PYTOY)
from pytoy.minicc import compile_source, MiniError  # noqa: E402

BLOCK = re.compile(
    r'name = """(.*?)""",\s*cSource = """(.*?)""",\s*expectedToys = """(.*?)""",',
    re.S,
)


def main():
    kt = open(KT).read()
    blocks = BLOCK.findall(kt)
    print(f"parsed {len(blocks)} embedded examples")
    bad = 0
    for name, src, golden in blocks:
        got = compile_source(src, name)
        if got != golden:
            bad += 1
            print(f"MISMATCH: {name}")
    print(f"byte-identity mismatches vs pytoy minicc: {bad}")

    # Recursion must be rejected.
    rec = ("int f(int n) { if (n == 0) { return 0; } return f(n - 1); }\n"
           "int main(void) { return f(3); }\n")
    try:
        compile_source(rec, "t")
        print("ERROR: recursion was NOT rejected")
        bad += 1
    except MiniError as e:
        print(f"recursion rejected OK: {e}".split(":")[0])

    raise SystemExit(1 if bad else 0)


if __name__ == "__main__":
    main()
