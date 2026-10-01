# ktoy — specification

**ktoy** is a Kotlin Multiplatform port of the runnable part of
[pytoy](https://github.com/manfredscheucher/pytoy): the Toy CPU **assembler**,
**simulator**, and **GUI debugger**. It targets Android, JVM desktop, iOS and
Web (JS) from one codebase.

The point of this document is to record *what* is being built and *which
decisions* were made, so that:

1. Manfred can re-read later what was agreed,
2. the implementation can be checked against it, and
3. a fresh reviewer with zero context can judge, by comparing against pytoy,
   whether the port is faithful.

This is a **spec / plan** written before the code. Nothing here is committed to
git yet. Manfred reviews this first; only then does the Gradle scaffold + code
follow.

---

## 1. Goal and scope

### 1.1 What ktoy does

Reproduce, on mobile and desktop, the pytoy workflow:

```
python3 run.py sim examples/asm/sum3.toys
```

i.e. take a `.toys` assembly file (or a compiled `.toyo` byte listing),
assemble it into a 256-byte memory image, and let the user **step through** and
**run** the program in a GUI that highlights the current program counter and the
memory address being read/written — exactly the behaviour pytoy's GUI has today.

The examples that live in `examples/` are **bundled into the app** as ready-made
programs the user can pick from a list (like the Python GUI's Load button, but
pre-populated), because on mobile there is no obvious filesystem to browse.

### 1.2 What ktoy deliberately does NOT include (for now)

- **The C compiler (`toycc`, pytoy `cc`).** Not needed on mobile, and it's the
  largest/most complex part of pytoy. Out of scope. The `.toyc` C-source examples
  are not used by ktoy (they may stay in `examples/` for reference, unused).
  Note: `examples/c/` also contains *generated* `.toys` files (compiler output,
  one next to most `.toyc`). Those are plain assembly and ktoy *can* assemble +
  run them — decide at scaffold time whether to bundle `examples/asm/` only, or
  also the `c/*.toys`. Recommendation: bundle `examples/asm/` for v1 (curated,
  hand-written), add `c/*.toys` later if useful.
- Any file export beyond what's needed to run (e.g. writing `.toyo` back out) is
  not a launch requirement.

### 1.3 Design priority: didactic clarity

ktoy, like pytoy, is a **teaching tool** — nobody runs it in production. The
overriding priority is that what happens inside the CPU is **clear, visible and
easy to operate**. When a UI/behaviour choice trades polish for clarity, choose
clarity. This drives the GUI proposals in §5.

---

## 2. Repository layout

Standalone repo at `~/github/ktoy` (sibling of `~/github/pytoy`), Manfred
creates the GitHub URL. Working name **ktoy** (parallel to pytoy: same tool, the
`py`→`k` swap says "Kotlin port"). The GitHub repo name is Manfred's choice
(`ktoy`, `toycputools-kotlin`, …).

```
~/github/ktoy/
  doc/
    spec.md            (this file)
  composeApp/          (KMP + Compose Multiplatform module)
    src/commonMain/kotlin/.../core/ExampleSources.kt   (GENERATED, see below)
  ...gradle files...
```

**examples sharing (decided — generate, not submodule).** The single source of
truth is **`pytoy/examples/`**. ktoy does NOT keep its own copy of the `.toys` /
`.toyo` files. Instead the bundled examples are generated into Kotlin as
`core/ExampleSources.kt` (embedded strings, since Android has no filesystem
access to pytoy's tree) by **`pytoy/scripts/gen_ktoy_examples.py`**.

Why generate rather than submodule: Android can't read the files from disk at
runtime anyway — they must be compiled in — so a submodule would still need a
generation step. Generating straight from the canonical sources keeps exactly
one hand-maintained copy (the pytoy `.toys`) and derives everything else.

Guarding against drift (the risk of any generated artifact): pytoy's pre-commit
hook (`scripts/install-hooks.sh`) regenerates `ExampleSources.kt` whenever a
staged change touches `examples/` and aborts the commit if it was stale — so the
embedded copy can't silently fall behind. `ExampleSources.kt` also carries a
"GENERATED — do not edit" header. To refresh by hand: run
`python3 scripts/gen_ktoy_examples.py` from the pytoy repo.

### 2.1 pytoy rename

Manfred mentioned pytoy itself might get renamed. That is a separate task in the
pytoy repo, not part of ktoy, and is left open. (`pytoy` is a fine name; if it
changes, ktoy's cross-references get updated then.)

---

## 3. The Toy CPU (ported verbatim from pytoy `core.py`)

The CPU is an 8-bit accumulator machine with 256 bytes of memory, one
accumulator (ACC) and a program counter (PC). This is the single source of
truth; assembler and simulator both build on it. The Kotlin port must match
pytoy byte-for-byte.

### 3.1 Instruction set

| mnemonic | opcode | bytes | effect |
|----------|-------:|:-----:|--------|
| `stop`   | 0      | 1 | halt |
| `right`  | 1      | 1 | `acc >>= 1` |
| `left`   | 2      | 1 | `acc <<= 1` |
| `not`    | 15     | 1 | `acc = ~acc` |
| `and a`  | 17     | 2 | `acc &= mem[a]` |
| `or a`   | 18     | 2 | `acc \|= mem[a]` |
| `xor a`  | 19     | 2 | `acc ^= mem[a]` |
| `load a` | 20     | 2 | `acc = mem[a]` |
| `store a`| 21     | 2 | `mem[a] = acc` |
| `add a`  | 22     | 2 | `acc += mem[a]` |
| `sub a`  | 23     | 2 | `acc -= mem[a]` |
| `goto a` | 24     | 2 | `pc = a` |
| `ifzero a`|25     | 2 | `if acc == 0: pc = a` |
| `nop`    | 128    | 1 | do nothing |

Bit 4 (`0x10`, `FETCH`) of the opcode means "this instruction has a second
address byte". `has_operand(op) = (op & 0x10) != 0`.

Semantics rules to preserve exactly:
- **ACC is masked to 8 bits** after every instruction (`acc & 0xFF`). So `not`,
  `add`, `sub`, `left` wrap mod 256. In Kotlin: keep ACC as an `Int` and mask,
  or use `UByte`; masking `Int` mirrors the Python most directly.
- **PC wraps mod 256**: `next_pc = (pc + 1) % 256` or `(pc + 2) % 256`.
- **`store a` writes unconditionally** in the core; the code-overwrite guard
  (§4.3) is checked by the *caller* before executing, not inside execute.
- **Unknown opcodes** (anything not listed, including 128) behave like `nop`:
  just advance the PC. They are *shown* as unknown, not relabelled "NOP".
- `decode(mem, pc)` returns `(instr, argAddr, nextPc)`; `argAddr` is null for
  one-byte instructions.

### 3.2 Value parser (`parse_val`)

A token is parsed as: an 8-char binary string of `0`/`1` → base 2; otherwise
Python's `int(s, 0)` which accepts `0x…` hex, `0b…` binary, and decimal. The
Kotlin port must accept the same forms (`10`, `0x0A`, `0b1010`, `00001010`).

---

## 4. The assembler (ported from pytoy `assembler.py`)

Two-pass assembler turning `.toys` source into `mem[256]` plus a *listing* and
symbol table.

### 4.1 Source syntax

- `#` starts a comment to end of line.
- `label:` defines a symbol at the current address (lowercased).
- A line is `[label:] [mnemonic [operand]]`; label-only and blank lines are
  allowed.
- A bare token that is not an opcode is a **data byte** (a value, or a label
  used as an address constant). Data typically goes after `stop`.

Example (`sum3.toys`): `load a` / `add b` / `add c` / `stop`, then `# data`,
then `a: 3`, `b: 1`, `c: 4` → result 8.

### 4.2 Two-pass assembly

- **Pass 1**: walk lines, assign addresses, record labels → addresses. Each
  instruction is 1 or 2 bytes. If total size > 256, emit a single clear error
  ("Program is too big: needs N bytes, Toy CPU has only 256") and stop — do not
  crash.
- **Pass 2**: emit bytes into `mem`, build the `listing` (per line: address,
  emitted bytes, original source text, is-data flag), resolve operands (label →
  address, else `parse_val`). Collect `data_addrs` (addresses holding data).
- Errors (unknown label, missing operand, bad value) are collected and returned;
  a program with errors is not run.

`listing` entries are `(addr, bytes, origText, isData)`; `addr` is null for
comment/blank lines. This listing drives the GUI's source panel and its
address→line mapping.

### 4.3 The `# data` marker (code-overwrite guard)

A comment line whose only payload is the word `data` (regex `^#\W*data\W*$`,
case-insensitive — matches `# data`, `#data`, `# ── data ──`, but NOT `# a data
byte`) marks where data begins. Its address becomes `data_start`.

When enabled, the simulator warns before a `store` writes to an address *below*
`data_start` (i.e. into code). In the GUI this is a dialog asked **once per run**
("Continue anyway?"); if declined, halt. If a program has no `# data` marker the
guard is simply off.

Caveat from pytoy to be aware of: the guard is only armed on the CLI path with
`-d/--detect-code-overwrite`. pytoy's GUI **Load button always passes
`code_guard=None`**, so a program opened *inside* the GUI has the guard off even
if it has a `# data` marker. For ktoy — where the program picker *is* the main
entry — Claude recommends arming the guard whenever `data_start` exists (a
didactic improvement over pytoy), but flag it so the divergence is intentional,
not an accidental port bug.

### 4.4 The `.toyo` object format

`export()` writes a human-readable listing; ktoy needs to **parse** it back
(pytoy `parse_toyo`): read lines whose first token is a decimal address followed
by one or two 8-char binary byte-strings into `mem`; ignore `#` comments and the
`# SYMBOLS` section. A `.toyo` has no re-runnable source, so when loaded the GUI
shows only the memory panel, no source panel (mirrors pytoy).

Whether ktoy also *writes* `.toyo` is optional (not a launch requirement).

---

## 4b. Input model — how a program gets into the machine

The original FreeDOS toycpu has a switch-based front panel: you enter a memory
**address**, then a **value**, byte by byte, to key the whole program into
memory by hand (Altair-8800 style). ktoy and pytoy deliberately do **not**
reproduce that. Instead:

- The primary way in is **assembly**: you write/pick `.toys` source, and ktoy
  assembles it into the byte image. You do not hand-key raw bytes as the normal
  path — the bytecode is *derived* from the assembly.
- pytoy today is **read-only**: the source is not edited in-app; you change it
  outside and reload via Load. That stays the default.

### 4b.1 Byte-edit mode (new in ktoy, and proposed for pytoy)

An **explicit mode switch** with two modes:

- **Mode A — "ASM view" (default, read-only):** source panel + memory panel
  side by side, exactly the pytoy behaviour in §5. Highlighting as specified.
- **Mode B — "Byte edit":** the source/ASM panel is **hidden**; only the memory
  panel is shown, and it is **editable** — tap/click a memory address and change
  its value directly (this is pytoy's right-click-a-cell idea).

Why the ASM panel disappears in Mode B: once the user hand-edits a byte, the
assembly text on the left no longer corresponds to the bytes in memory, so
showing it would be misleading. Hiding it keeps the picture honest.

**A↔B round-trip semantics (decided, so the behaviour is unambiguous):**

- The **byte memory image is the single source of truth** for the running
  machine at all times. There is no disassembler: hand-edited bytes are never
  turned back into assembly text.
- **A→B**: keep the current memory image; edits in B mutate that same image
  (they are visible to the CPU immediately, i.e. stepping uses the edited bytes).
- **B→A**: the source panel re-appears showing the **last loaded assembly text
  unchanged** (it is *not* re-derived from possibly-edited bytes). Because the
  bytes may now diverge from that text, Mode A after any hand-edit shows a small
  banner: "memory was hand-edited; source shown may not match". The highlighting
  in A still tracks the real PC/memory, so it stays truthful even if the source
  text is stale.
- **Edits persist across A↔B toggles and across rotation** (they live in the
  same state the simulator holds; see §5.5). They are discarded only by **Reset**
  (restores the originally assembled/loaded image) or by loading another program.
- The code-overwrite guard (§4.3), if armed, keeps using the original program's
  `data_start`; hand-edits do not move it.

This is a firm default; Manfred can revise it at review. For pytoy the same
feature is *proposed* (mode toggle hiding the source split); only the change
highlight (§6 item 4) is a definite pytoy patch.

## 5. The GUI (this is the heart of the port)

pytoy's GUI is two panels — **source** (left) and **memory** (right) — plus a
bottom bar with **Load / Step / Run / steps-per-second / Reset**. The engine is
`decode`/`execute_one` from the core; the GUI just visualises state and drives
stepping. ktoy reproduces this in Compose Multiplatform.

### 5.1 State shown

- **ACC** (decimal + 8-bit binary), **PC**, current instruction bytes, and an
  `EXPLAIN` line describing the current instruction (e.g. `LOAD a (addr:13,
  val:3)`).
- **Memory listing**: each visible address shown as `addr=bBBBBBBBB
  value=bVVVVVVVV`. "Visible" = all program addresses plus any address written
  at runtime.
- Step counter; a clear "Stopped after N steps" state on halt.

### 5.2 Highlighting rules (must match pytoy — the key requirement Manfred named)

The whole point Manfred called out: **highlight the current PC and the memory
address being read/written**. Three of the four colours below (yellow/green/
orange) are pytoy's existing scheme, to reproduce exactly. The fourth (blue,
"change") is **new** — added to ktoy and back-ported to pytoy (see §6 item 4);
it is not in pytoy's original rendering.

Source panel (per line):
- **PC line(s)** → yellow (`#ffffaa`), prefix `>>`. For a two-byte instruction
  **both** source rows are highlighted (opcode byte at PC and operand byte at
  PC+1). For a normal `load a` both bytes map to the same source line; for
  hand-written raw bytes they can be two lines — mark both.
- **Argument line** (the address the current instruction reads/writes) → green
  (`#aaffaa`), prefix `**`.
- **User-selected line** (clicked) → orange (`#ffcc66`). Selection wins over PC
  and argument colour.
- Auto-scroll so the PC line stays visible.

Memory panel (per address):
- **Selected address** (clicked) → orange (`#ffcc66`), wins over all the others.
- **PC bytes** (opcode at PC + operand at PC+1) → yellow background on **both**
  bytes. The `>>` text marker, however, is on the **opcode byte only** (the
  operand byte at PC+1 gets yellow background but a blank marker). Match this.
- **Changed cell** (the address written in the *last* step) → the new "change"
  colour (light blue `#99ccff`, §6 item 4). Persists until the next step.
- **Argument address** (the `mem[a]` this instruction touches) → green.

Colour precedence, highest first: **selected (orange)** > **PC (yellow)** >
**change (blue)** > **argument (green)**.

Rationale for PC above change: the change highlight persists until the next
step, and on that next step the changed cell is often exactly the PC byte. The
program counter is the headline feature and must never be hidden, so yellow
wins over a lingering blue. Blue still shows wherever the changed cell is not
currently the PC or the selection.

Two precedence subtleties to reproduce exactly:
- Selection wins on **background colour** only; it does **not** replace the
  `>>`/`**` prefix marker. A selected line that is also the PC line shows orange
  background *with* a `>>` marker.
- The **argument** highlight refers to the instruction *about to execute* (the
  one now at PC, after the previous step already advanced PC) — not the address
  the last instruction touched. pytoy computes it via `decode(mem, pc)` at
  refresh time. A common port mistake is to highlight the previous instruction's
  argument instead.

Clicking a source line or a memory row selects that address and highlights the
corresponding line/row in the other panel — so the user can see "this source
line is this byte in memory".

### 5.3 Run controls

- **Step**: execute one instruction, refresh highlights.
- **Run**: auto-step on a timer at *steps per second* (float, clamped to
  `[0.1, 1000]`, default in pytoy = 1.0). Run toggles to Stop; single-stepping
  disabled while auto-running.
- **Reset**: restore the original memory image, ACC=0, PC=0, re-arm the
  overwrite guard.
- Keyboard (desktop): Space/Enter = step, R = run/stop, Esc = reset. On mobile
  these become on-screen buttons (the primary interface).

### 5.4 Program picker (replaces file dialog on mobile)

Bundled `examples/` programs are listed by name; picking one assembles and loads
it. On desktop a "Load file…" option can still open an arbitrary `.toys`/`.toyo`
(nice-to-have); on mobile the bundled list is the main entry.

### 5.5 Responsive layout and lifecycle (Android especially)

The ASM panel and the memory panel are **two separate widgets/composables** laid
out responsively:

- **Landscape** (or wide desktop window): the two panels sit **side by side**
  (like pytoy's split view).
- **Portrait** (narrow): the two panels stack **vertically**, one above the
  other.

Drive this off the available width (Compose `BoxWithConstraints` /
window-size class), not a hard-coded orientation flag, so desktop-window resizing
and phone rotation use the same rule.

**Do NOT reset the machine on rotation / configuration change.** By default an
Android `Activity` is recreated on rotation, which would wipe ACC/PC/memory/step
state and yank the user back to step 0 — unacceptable. The whole simulator state
(loaded program, memory image, ACC, PC, step count, run/stop, selected address,
current mode A/B) must survive a config change. Options, to choose at
implementation: hoist the state into a `ViewModel` (survives recreation), and/or
handle config changes so the Activity isn't recreated, and/or `rememberSaveable`
for the small scalar state. The requirement is behavioural: **rotating the phone
must not restart the program.** This is on the manual test checklist (§7).

---

## 6. Didactic improvements over pytoy (decided for v1)

pytoy is a prototype and open to change. All four below are **in scope for
ktoy v1** (Manfred decided). They touch only the front end — CPU semantics and
the assembler are unchanged.

1. **CPU as a diagram, not just text rows.** Today ACC/PC/memory are text lines.
   A visible ACC box and a data-flow arrow (LOAD pulls `mem[a]`→ACC, STORE pushes
   ACC→`mem[a]`) makes the fetch-execute cycle tangible, especially on a phone.
   Keep the same highlighting, render it visually instead of `>>`/`**` prefixes.

2. **Step back (undo).** pytoy can only Reset and re-run. Stepping *backwards*
   one instruction is valuable for learning ("wait, what did that do?"). Snapshot
   (pc, acc, mem-diff) per step so a step can be undone.

3. **Binary/decimal/hex toggle** for ACC and memory values, so learners can flip
   representations.

4. **Change highlight (own colour, persistent).** Highlight what the last step
   **wrote** in a **new colour not yet used** (yellow/orange/green are taken):
   light blue `#99ccff` (final value TBD at build time; magenta `#ff99dd` is the
   alternative). Fire on *write*, **not** on value change — so a `load` of the
   value already in ACC, or a `store` of the same value, still lights up (the
   point is to show "this step touched this", not "this value differs"). Two
   things get it:
   - the **memory cell written** in the last step (i.e. the target address of a
     STORE), highlighted in the memory grid — even if the stored value equals
     what was already there;
   - the **ACC status line** (`ACC=…=b…` at the top of the memory panel),
     highlighted **iff the instruction writes ACC** (LOAD/ADD/SUB/AND/OR/XOR/
     NOT/LEFT/RIGHT; not STORE/GOTO/IFZERO/STOP/NOP) — regardless of whether the
     value changed. ACC is header text, not a grid cell, so it is the ACC line's
     background that turns blue, not a memory row.
   It is **not** a brief flash: the highlight persists **until the next step
   executes**, so the learner can study the delta. After Reset / at step 0 there
   is no change highlight.
   Precedence (see §5.2): blue sits **below** selected (orange) and PC (yellow)
   but **above** the green argument colour. So on the changed cell blue wins over
   green, but if that cell is the current PC byte or the clicked selection, those
   win over blue (the program counter is never hidden by a lingering highlight).
   **This one is also added to pytoy**, not just ktoy (already implemented there;
   see §9).

---

## 7. Testing plan (per Manfred's pipeline: nothing untested stays)

- **Core + assembler**: unit tests in `commonTest` that assemble each bundled
  example and run it to STOP, asserting the final ACC **and step count and final
  memory** match pytoy. The golden values already exist: pytoy's
  `scripts/gen_golden.py` produces `tests/golden/asm_golden.json`
  (`name → {final_acc, steps, memory, hit_cap}`) for all `examples/asm/*.toys`.
  ktoy ships a copy of that JSON in `commonTest` resources and asserts against it,
  so the two implementations cannot drift. A pytoy pytest (`tests/test_golden.py`)
  guards the JSON on the Python side.
- Remember the two easy-to-miss semantics the golden run depends on: ACC masked
  `& 0xFF` every step and PC wrapping mod 256 — get either wrong and steps/memory
  diverge from the golden table.
- GUI behaviour (highlighting, step/run/reset) tested where the Compose test
  harness allows; manual sign-off per target (Android / JVM / iOS / Web) is the
  real gate, tracked as a checklist.
- **Rotation must not reset** (§5.5): explicit manual test on Android — load a
  program, step a few times, rotate the phone, assert ACC/PC/step/selection are
  unchanged. Also test landscape=side-by-side, portrait=stacked.
- Byte-edit mode (§4b.1): editing a byte updates memory and the running machine;
  switching A↔B behaves as specified; **hand-edits survive rotation** (edit a
  byte, rotate, assert the edit is still there and Reset restores the original).

## 8. Decisions (was: open questions)

- Repo name: **`ktoy`**, remote `git@github.com:manfredscheucher/ktoy.git`.
- §6 didactic improvements: **all four in v1** (diagram, undo, bin/dec/hex,
  persistent change highlight).
- Targets: **Android + JVM desktop first**, iOS + Web (JS) added once the core
  is proven.
- examples: **generated from pytoy/examples/** into ExampleSources.kt by
  scripts/gen_ktoy_examples.py, kept in sync by pytoy's pre-commit hook (NOT a
  submodule — Android needs them compiled in). ~~ktoy submodules the whole pytoy repo~~ (examples at
  `pytoy/examples/`).
- bundled examples: **`examples/asm/*.toys` for v1** (curated, hand-written); the
  generated `examples/c/*.toys` can be added later (§1.2).
- **pytoy gets patched too**: the change-highlight (§6 item 4), and optionally
  the other didactic ideas, land in pytoy as well — Manfred likes them. Tracked
  as a separate pytoy change set (see §9).

Still open / long term:
- Does ktoy eventually *replace* pytoy (drop Python+Qt once JVM desktop is on
  par), or coexist? Not decided; no action needed now.

## 9. pytoy patch set (parallel track)

Manfred wants the didactic improvements in pytoy too. These are pytoy changes,
independent of the ktoy port, committed in the pytoy repo:

- **Change highlight (definite):** in `simulator.py`, add a 4th highlight colour
  for the cell written in the last step (and/or ACC), persistent until the next
  step, winning over green. Same colour as ktoy (`#99ccff`).
- **Byte-edit mode (proposed):** right-click / edit a memory cell; when editing
  raw bytes, hide the source split (see §4b.1).
- **Step-back / undo (proposed):** snapshot per step to allow one step back.
- **Bin/dec/hex toggle (proposed):** flip value representation.

These are done as normal pytoy commits (Claude does not push; Manfred does).

## 10. Implementation status (Android v1)

The Android app is built. Structure:

- **Scaffold:** Kotlin 2.2.20, Compose Multiplatform 1.10.3, AGP 8.13.2, Gradle
  9.2.1, minSdk 24 (matching the OpenYarnStash conventions). One `composeApp`
  module, Android target only. The `kotlin-common` `:core` module is wired as a
  git submodule for the leveled Logger. Namespace `org.bytefred.ktoy`.
- **Core (commonMain, ported from pytoy):**
  `core/ToyCpu.kt` (ISA, decode/executeOne, WRITES_ACC, describe),
  `core/Assembler.kt` (two-pass, line-numbered errors, did-you-mean, source-line
  echo), `core/Machine.kt` (runnable state + undo history + parseToyo).
- **UI (commonMain):** two screens — an example list and the simulator (source +
  memory panels; portrait stacks them, landscape shows them side by side via
  `BoxWithConstraints`). Highlighting mirrors pytoy; ACC turns green when
  stopped, blue when just written. Step / Run / Undo / Reset. Tap a memory cell
  to select, long-press to live-edit (value-only dialog with 8-bit wrap note).
- **Rotation does not reset:** state lives in a `ViewModel` and the activity
  declares `configChanges`, so turning the phone keeps ACC/PC/step/memory.
- **Examples bundled in the APK:** all `examples/asm/*.toys` plus the two
  compiled `.toyo` are embedded as Kotlin strings in `core/ExampleSources.kt`,
  generated by pytoy's `scripts/gen_ktoy_examples.py` (no filesystem access).

### 10.1 Byte-for-byte fidelity to pytoy

A fresh reviewer compared the Kotlin core against pytoy line by line; the
arithmetic, PC-wrapping, WRITES_ACC, is_code_store, two-pass address counting,
resolve, the too-big message, the data-marker regex, step/undo semantics and
parseToyo were all confirmed equivalent. Three edge-case drifts were then fixed
so ktoy matches pytoy exactly:

- **`parseVal`** now mirrors Python `int(s, 0)`: rejects redundant decimal
  leading zeros (`010`, `007`), accepts `0o` octal, `_` separators and a leading
  `+`/`-` sign. (Before it silently accepted `010` as 10.)
- **did-you-mean** now uses the Ratcliff/Obershelp ratio (2·M/T) like difflib,
  not Levenshtein, with the same first-in-order tie-break — verified to give the
  identical suggestion as pytoy for `stpo→stop`, `adn→and`, `o→or`, etc.
- **`parseSource`** drops the phantom trailing blank line after a final newline,
  so the listing row count matches pytoy.

### 10.2 Tests

`commonTest` has a golden test (assembles + runs the 10 asm examples and the 2
toyo examples, asserting final ACC + step count against pytoy's committed golden
table) and an assembler test (20 cases: parseVal forms, happy path, and every
error message including the did-you-mean / ignored-operand / too-big / source-
line-echo behaviours).

### 10.3 Not in v1 (deferred)

iOS / JVM desktop / Web targets (the scaffold is Android-only for now; the core
is pure commonMain so adding them later is straightforward). No file load/save
(everything is in RAM; the examples are bundled). The C compiler stays out of
scope (§1.2).
