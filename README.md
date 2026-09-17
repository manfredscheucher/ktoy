# ktoy

A Kotlin Multiplatform port of the runnable part of
[pytoy](https://github.com/manfredscheucher/pytoy): an **assembler**,
**simulator**, and **GUI debugger** for the
[Toy CPU](https://github.com/freedosproject/toycpu) — a minimal 8-bit
educational processor (256 bytes of memory, one accumulator, a program counter).

Same idea as pytoy, but from one codebase on **Android, JVM desktop, iOS and
Web**, so the CPU can be explored on a phone. The point is didactic: *see* how a
CPU works — the GUI highlights the current program counter and the memory
address each instruction reads or writes, and you can step through a program one
instruction at a time.

You write assembly (`.toys`), ktoy assembles it to the byte image and runs it.
The C compiler that pytoy ships (`toycc`) is intentionally **out of scope** here.

## Status

Early. The plan and the full specification live in
[`doc/spec.md`](doc/spec.md) — read that first. The Gradle/Compose Multiplatform
scaffold and code follow once the spec is signed off.

## Credits & license

The Toy CPU instruction set is by Jim Hall (FreeDOS Project). ktoy's own code is
a port of pytoy, which is independent of the original toycpu. License to match
pytoy (MIT).
