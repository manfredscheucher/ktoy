# ktoy — sync TODO (bring up to current pytoy)

Checklist for syncing ktoy to the current state of
[pytoy](https://github.com/manfredscheucher/pytoy) and deploying it as a website.
Written as a plan to implement later. The CPU core is already byte-identical to
pytoy; the gaps below are features pytoy gained after the original port.

Reference for every item: compare against the pytoy file named in brackets.
Verify after each phase: `./gradlew :composeApp:testDebugUnitTest` (GoldenTest +
AssemblerTest must stay green — the CPU semantics must not drift).

**Decided with Manfred:** do the core sync first (incl. I/O), web deploy after.
Within the core sync: Assembler/constants first (small, correctness-critical),
then the I/O UI widget, then polish. I/O IS wanted in ktoy (didactic value on the
web), overriding the original "mobile doesn't need it" scope cut.

---

## Phase 1 — I/O constants + assembler (small, correctness-critical)

These enable `load io0` / `store ready` and the direct-address output toycc
emits. ~35 lines total. No UI yet.

- [ ] **Add I/O constants.** New file (e.g. `core/Format.kt`) with, matching
      pytoy `pytoy/format.py:33-50`:
      - `IO_BASE = 240`, `IO_COUNT = 15`, `IO_READY = 255`, `SP_TOP = 239`
      - `IO_SYMBOLS`: map `"io0"->240 .. "io14"->254`, `"ready"->255`
- [ ] **Assembler size limit 256 → 240.** `Assembler.kt:142`: change
      `if (addr > 256)` to `if (addr > IO_BASE)`; update the "too big" message to
      say only 0..239 are usable (240..255 reserved for I/O). Matches pytoy
      `assembler.py:89-100`.
- [ ] **Pre-seed I/O labels.** `Assembler.kt:133`: start pass 1 with
      `mutableMapOf<String,Int>().apply { putAll(IO_SYMBOLS) }` instead of an
      empty map, so `io0..io14` / `ready` resolve. Matches pytoy `assembler.py:82`.
- [ ] **Label±offset resolution.** `Assembler.kt:212-228` (`resolve`): before the
      parseVal fallback, match `label+N` / `label-N`
      (`Regex("""\s*([A-Za-z_]\w*)\s*([+-])\s*(\w+)\s*""")`), look the base up in
      syms, add/sub the parsed offset, mask `and 0xFF`. Matches pytoy
      `assembler.py:157-170`. Needed for toycc's constant-index array output
      (`load arr+3`).
- [ ] **Tests (AssemblerTest.kt):** `load io0` resolves to 240; `store ready` to
      255; `load arr+2` resolves to arr's address + 2; a 241-byte program is
      rejected; a 240-byte program is accepted. Mirror pytoy's
      `tests/test_toyasm.py` cases (`test_label_plus_offset_resolves`,
      `test_assemble_exactly_240_is_ok`, `test_assemble_241_overflows_into_io_region`).

**Commit after Phase 1.** This is self-contained and testable headless.

---

## Phase 2 — examples + golden sync

- [ ] **Generator: pull 04-io.** `scripts/gen_example_sources.py:66`: loop over
      `("03-programs", "04-io")` so the memory-mapped I/O examples get embedded.
      Regenerate: `PYTOY=/Users/manfred/github/pytoy python3 scripts/gen_example_sources.py`.
      (Decide: embed only the deterministic 04-io examples, or all — the
      input-driven ones only make sense once the I/O panel exists, Phase 3.)
- [ ] **Golden entries.** Add GoldenTest.kt entries for any new deterministic
      examples (04-io: numbers, hello_world, fibonacci, countdown). pytoy's values
      live in `tests/golden/asm_golden.json` under `04-io/<name>`. NOTE: pytoy's
      golden keys are now path-prefixed (`01-basics/12_ifzero`), ktoy uses bare
      names — keep ktoy's bare-name scheme, just copy the acc/steps numbers.
- [ ] **Re-check 12_ifzero if embedded.** pytoy changed it (now acc=0, steps=2).
      ktoy currently only embeds 03-programs, so it's likely not present — confirm.

---

## Phase 3 — I/O panel widget (the big UI piece, ~200 lines Compose)

Port pytoy's IOPanel (`pytoy/simulator.py:494-620`). Headless-untestable, so the
I/O *logic* is covered by Phase 1 tests; the widget itself is verified manually
in the browser/emulator.

- [ ] **IOPanel composable:** 16 cells (io0..io14 + ready), one row (two rows in
      binary mode). Display-format selector: decimal / binary / hex / ascii.
- [ ] **Polling indicator:** highlight `ready` red while the CPU is busy-polling
      (current instruction is `load ready` / `load 255`) — pytoy `polling_input`.
- [ ] **Editing:** click a cell to edit it (reuse the existing EditValueDialog),
      so the user is the device and the program reacts.
- [ ] **Layout:** fit into SimulatorScreen's portrait-stacked / landscape
      side-by-side arrangement.
- [ ] **ascii format:** non-printable bytes show as `·`, matching pytoy.

---

## Phase 4 — polish (optional)

- [ ] **"# old: N" annotation.** On a STORE, show the overwritten cell's previous
      value for one step. Add `changedCellPrev: Int?` to `MachineState`
      (Machine.kt:7-18), capture `mem[argAddr]` before `executeOne` in `step()`,
      include it in snapshot/restore (undo), render in MemoryPanel. Matches pytoy
      `simulator.py:999-1007, 1374-1376`. ~10 lines.
- [ ] **Load dialog remembers last directory** (only relevant once file load
      exists — pytoy `simulator.py` `_load_clicked` uses `os.path.dirname`).

Note: ktoy already has features pytoy lacks — step-back/undo, live cell edit,
blue change-highlight, rotation-safe state. Don't regress those.

---

## Phase 5 — web deploy (after core sync)

ktoy is KMP with the scaffold ready; needs the JS/Web target + entry point.
Recent ktoy commits already mention a "web build fix", so some groundwork exists.

- [ ] **Add JS target** to `composeApp/build.gradle.kts` (the version catalog
      already has `kotlin-browser`).
- [ ] **jsMain entry point:** `composeApp/src/jsMain/kotlin/` calling `App()`,
      plus `resources/index.html`.
- [ ] **Build:** `./gradlew :composeApp:jsBrowserDistribution`.
- [ ] **Deploy** `build/dist/js/productionExecutable/` (GitHub Pages / Vercel /
      …). Decide hosting. Est. ~4-6h per the survey, no known blocker.

---

## What is intentionally NOT ported

- **The C compiler (toycc).** Stays in pytoy. ktoy consumes its *output* (.toys),
  which is why Phase 1's label±offset support matters. If C-level I/O examples are
  wanted on the web later, compile them with pytoy's toycc and embed the .toys.
