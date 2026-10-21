# ktoy

A cross-platform app for the [Toy CPU](https://github.com/freedosproject/toycpu):
an assembler and a simulator you can step through — on the **desktop, the web,
Android and iOS**. A Kotlin Multiplatform + Compose rewrite of
[pytoy](https://github.com/manfredscheucher/pytoy).

The Toy CPU is a minimal 8-bit processor with 256 bytes of memory, one
accumulator, and a program counter. It was built as a FreeDOS learning tool with
a switch-based interface (like an Altair 8800); ktoy keeps the same CPU but lets
you write readable assembly and watch it run. The point is to *see* how a CPU
works: while a program runs, it highlights the current bytecode in memory next to
the matching assembly instruction, and you step through one instruction at a time.

You write assembly (`.toys`); ktoy assembles it to a 256-byte image and runs it.
It also compiles a small C subset (the same `toycc`/`minicc` as pytoy) and shows
C, assembly and memory side by side. Example programs are built in.

## Try it

- **Web** (live once GitHub Pages is enabled):
  <https://manfredscheucher.github.io/ktoy/>
- **Desktop / Android / iOS** — see [Build & run](#build--run) below.

## Videos

Tutorials and demos on the Toy CPU and these tools (basic instruction set,
programs, the C compiler, and more):

▶ [YouTube playlist](https://www.youtube.com/watch?v=vQxGXtlf5c4&list=PLU1OJ36r4v_c)

## pytoy and ktoy

[**pytoy**](https://github.com/manfredscheucher/pytoy) is the original
implementation — Python, for the desktop. ktoy is the Kotlin Multiplatform
rewrite that covers more platforms (desktop plus web, Android, iOS) and is meant
to become the main front end over time. pytoy stays the reference: it's the
source of truth for the CPU semantics and the examples, which ktoy is generated
from, and it keeps working. Python covers Windows/Linux/macOS; Kotlin
Multiplatform is a superset that adds the browser and the phones on top.

A taste of the syntax:

```
        load  a         # acc = a
        add   b         # acc = a + b
        stop
a:      10              # data goes after stop
b:      20
```

## The app

A menu bar (File / View / Help) over an adaptive 1–3 column view: memory only for
bytecode (`.toyo`), assembly + memory for `.toys`, and C + assembly + memory for a
C program. The columns sit side by side when the window is wide and stack when
it's narrow (View → Layout to force portrait/landscape).

- **C** / **Assembly** — the source, with the current line highlighted.
- **CPU & memory** — the accumulator, program counter and current instruction,
  above a listing of all 256 memory bytes.

A click in any column highlights the same statement across the others. Controls:
Step, Run at an adjustable speed (steps/sec), Undo, Reset, and Edit the selected
cell. Double-tap a cell or line (desktop/web) — or the Edit button — to change its
value. File → Load/Save a program, Examples browses the built-ins by folder, Help
shows the instruction set.

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
  pytoy's `examples/` by `scripts/gen_example_sources.py` (pytoy is the single
  source of truth for the examples).

## Build & run

Needs the `kotlin-common` git submodule (used for the shared logger):

```bash
git submodule update --init --recursive
./gradlew :composeApp:assembleDebug        # build the debug APK
./gradlew :composeApp:testDebugUnitTest    # run the tests
```

Run scripts per platform (see [`doc/platforms.md`](doc/platforms.md) for details):

```bash
./scripts/runJVM.sh               # desktop
./scripts/runWeb.sh               # web dev-server (http://localhost:8604)
./scripts/runAndroidEmulator.sh   # Android emulator
./scripts/runIosSimulator.sh      # iOS Simulator (macOS + Xcode)
./scripts/deployPages.sh          # build the web bundle -> gh-pages branch
```

The tests include a snapshot table that asserts ktoy computes the exact same final
accumulator and step count as pytoy for every bundled example, so the two
implementations can't drift.

Machine-local scripts for installing on a specific device live in
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
