# ktoy sync — status

ktoy has been brought up to the current pytoy: the memory-mapped I/O support, the
full C compiler (toycc), and the UI polish. **Everything compiles and all tests
pass** (`./gradlew :composeApp:testDebugUnitTest` → BUILD SUCCESSFUL, 44 tests,
0 failures).

## Done + verified

### Assembler + I/O constants
- `core/Format.kt` — IO_BASE=240, IO_COUNT=15, IO_READY=255, SP_TOP=239,
  IO_LABELS, IO_SYMBOLS; plus IoFormat / formatByte() / pollingInput().
- `core/Assembler.kt` — usable space 0..239 (`addr > IO_BASE`); pass 1 pre-seeds
  io0..io14/ready; `resolve()` handles `label+N` / `label-N`.

### C compiler (full toycc port)
- `core/Compiler.kt` (~2176 lines) — Lexer, Parser, const-folding, call-graph +
  recursion analysis, CallLifter (global slots + marker dispatch, save/restore),
  CodeGen. genMultiply (double-and-add), genLogical (&& / ||), read/write I/O
  builtins, ascii_<char>_<value> labels, const-index fast path. Entry:
  `fun compileSource(src, sourceName, opts = Opts()): String`.
- **VERIFIED byte-identical to pytoy**: `CCompilerTest` compiles all 25
  examples/c (02-extended + 03-programs) and asserts `compileSource(...)` equals
  pytoy's compile_source output exactly, AND that each compiled program runs to
  its `// expect: N`. 25/25 byte-identical, 25/25 correct ACC.
- A fresh review found 4 issues (all fixed before the build): constant-shift fold
  >= 8, function-body emission order (now `lifted` worklist order for
  byte-identity), large numeric literals (Long parse), dead helpers removed.

### I/O panel (Compose)
- `ui/IoPanel.kt` — io0..io14 + ready, format selector, polling-red, tap-to-edit.
- `ui/SimViewModel.kt`, `ui/SimulatorScreen.kt` — Show/Hide I/O toggle + section.
- Reviewed 8/8 faithful by a fresh reviewer.

### Polish + tests
- `core/Machine.kt` + MemoryPanel (in SimulatorScreen.kt) — "# old: N" on an
  overwritten cell for one step, in the undo snapshot. Covered by `MachineTest`.
- `commonTest/`: AssemblerTest (io labels, label+N, 240/241 limit), SnapshotTest
  (keys/values updated to the renamed examples — all match pytoy's
  asm_snapshot.json), FormatTest, MachineTest, CCompilerTest.
- `scripts/gen_example_sources.py` (updated), `scripts/gen_c_examples.py` (new)
  — durable generators for the embedded example fixtures.

## CPU-semantics guarantee
The 16 SnapshotTest (acc, steps) values match pytoy's tests/snapshot/asm_snapshot.json
exactly, and the 25 CCompilerTest outputs are byte-identical — so the ported core,
assembler, and compiler behave identically to the Python reference.

## Still open (not started)
- **Phase 5: web deploy.** Add a JS/Web target to composeApp (the version catalog
  already has kotlin-browser), a jsMain entry point calling App(), index.html, and
  `./gradlew :composeApp:jsBrowserDistribution`; then deploy. See sync-todo.md.
