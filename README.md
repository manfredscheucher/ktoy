# ktoy

An Android app for the [Toy CPU](https://github.com/freedosproject/toycpu): an
assembler and a simulator you can step through on your phone. A Kotlin
Multiplatform + Compose port of [pytoy](https://github.com/manfredscheucher/pytoy).

The Toy CPU is a minimal 8-bit processor with 256 bytes of memory, one
accumulator, and a program counter. It was built as a FreeDOS learning tool with
a switch-based interface (like an Altair 8800); ktoy keeps the same CPU but lets
you write readable assembly and watch it run. The point is to *see* how a CPU
works: the screen highlights the current program counter and the memory address
each instruction reads or writes, and you step through one instruction at a time.

You write assembly (`.toys`); ktoy assembles it to a 256-byte image and runs it.
A handful of example programs are built into the app. The C compiler that pytoy
ships (`toycc`) is intentionally left out here.

A taste of the syntax:

```
        load  a         # acc = a
        add   b         # acc = a + b
        stop
a:      10              # data goes after stop
b:      20
```

## The app

Two screens. The simulator starts empty; the "Load…" button lists the built-in
examples. Once a program is loaded you get two panels:

- **Assembly** (left) — the source, with the current line highlighted.
- **CPU & memory** (right) — the accumulator, program counter and the current
  instruction, above a listing of all 256 memory bytes.

Controls: Step one instruction, Run at an adjustable speed (steps/sec), Undo a
step, Reset to the start, and Edit the value at a selected memory cell. Tapping a
cell or a source line highlights the same address in both panels. Long-press a
cell, or use the Edit button, to change its value.

## The code

One `composeApp` module. The CPU logic is a byte-for-byte port of pytoy and
lives in `commonMain`:

- `core/ToyCpu.kt` — the CPU core (opcodes, decode/execute). The single source of
  truth for the machine's semantics.
- `core/Assembler.kt` — turns `.toys` assembly into the byte image, with
  line-numbered error messages and did-you-mean hints for typos.
- `core/Machine.kt` — the runnable simulator state: step, step-back (undo), live
  edit, and `.toyo` parsing.
- `ui/` — the Compose screens.
- `core/ExampleSources.kt` — the bundled example programs, **generated** from
  pytoy's `examples/` by `pytoy/scripts/gen_ktoy_examples.py` (pytoy is the single
  source of truth for the examples).

## Build & run

Needs the `kotlin-common` git submodule (used for the shared logger):

```bash
git submodule update --init --recursive
./gradlew :composeApp:assembleDebug        # build the debug APK
./gradlew :composeApp:testDebugUnitTest    # run the tests
```

The tests include a snapshot table that asserts ktoy computes the exact same final
accumulator and step count as pytoy for every bundled example, so the two
implementations can't drift.

Machine-local scripts for installing on a device or emulator live in
`_private/scripts/` (not committed).

## Documentation

The full specification — what was built and why, and how it maps to pytoy — is in
[`doc/spec.md`](doc/spec.md). Building and running each platform (Android, desktop,
web, iOS), the menus, file I/O and version info are in
[`doc/platforms.md`](doc/platforms.md).

## Credits & license

The Toy CPU instruction set is by Jim Hall (FreeDOS Project) — see its
[official page](https://jimhall.itch.io/toy-cpu) and
[source](https://github.com/freedosproject/toycpu). ktoy is a port of pytoy,
whose own code (assembler, simulator, UI) is independent of the original toycpu.

ktoy is MIT-licensed (see [`LICENSE`](LICENSE)).
