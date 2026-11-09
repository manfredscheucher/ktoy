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

Bundled `examples/` programs are picked from a **collapsible tree**, grouped by
category then repo subfolder (mirrors pytoy's `ExamplesDialog`), not a flat list.
Top-level nodes: Assembly / C (minicc) / C (toycc) / Bytecode. Assembly and C
nodes expand into their repo subfolders (e.g. `03-programs`, `04-io`,
`02-extended`); Bytecode is flat. Category nodes start expanded, subfolders start
collapsed. The tree lives in the Examples dialog: leaves are radio buttons, Load
assembles/compiles and loads the selected one (see section 13).

Each `Example` carries a `subfolder` field (generated from pytoy's folder
layout); `groupedExamplesTree()` in `ExampleSources.kt` builds the
category→subfolder→examples map the picker renders. A C example both compilers
accept appears under BOTH C nodes; which node a leaf sits under IS the chosen
compiler — the tree narrows that leaf's `compilers` to the node's single
compiler so `load()` opens it on minicc or toycc accordingly (matching pytoy,
whose leaves carry `(example, compiler)`).

On desktop a "Load file…" option can still open an arbitrary `.toys`/`.toyo`
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
  memory** match pytoy. The snapshot values already exist: pytoy's
  `scripts/gen_snapshot.py` produces `tests/snapshot/asm_snapshot.json`
  (`name → {final_acc, steps, memory, hit_cap}`) for all `examples/asm/*.toys`.
  ktoy ships a copy of that JSON in `commonTest` resources and asserts against it,
  so the two implementations cannot drift. A pytoy pytest (`tests/test_snapshot.py`)
  guards the JSON on the Python side.
- Remember the two easy-to-miss semantics the snapshot run depends on: ACC masked
  `& 0xFF` every step and PC wrapping mod 256 — get either wrong and steps/memory
  diverge from the snapshot table.
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

`commonTest` has a snapshot test (assembles + runs the 10 asm examples and the 2
toyo examples, asserting final ACC + step count against pytoy's committed snapshot
table) and an assembler test (20 cases: parseVal forms, happy path, and every
error message including the did-you-mean / ignored-operand / too-big / source-
line-echo behaviours).

### 10.4 Example categorization (C | asm browser)

The bundled examples are categorized so a browser UI can group them. The
`Example` model (generated `ExampleSources.kt`) now has three kinds —
`ExampleKind { ASM, C, TOYO }` — and a `compilers: List<String>` field used only
for C examples. `source` holds the raw text: `.toys` for ASM, the `.toyc` C
source for C (so the UI can show the C|asm view by compiling on selection),
`.toyo` listing for TOYO.

**Compiler compatibility** for a C example (which of `"minicc"` / `"toycc"`
accept it) is computed in the Python generator, which runs against the pytoy
checkout: it actually tries pytoy's `minicc` and `toycc` on the source and
catches their errors (same logic as pytoy's `examples_index._detect_compilers`).
Recursion is the usual splitter — minicc rejects it, so the recursive examples
(`count_rec`, `fibonacci_rec`, `gcd_rec`, `sort_array_fn`, `sum_countdown`) are
tagged `toycc` only; the simple ones list both.

**Grouping helper** (generated): `groupedExamples(): Map<String, List<Example>>`
keyed `"Assembly"`, `"C (minicc)"`, `"C (toycc)"`, `"Bytecode"` (empty categories
dropped). A C example appears under every compiler that accepts it, so a
both-compilers example shows in both C lists. Mirrors pytoy's `grouped_examples`.
The UI consumes this; building the UI is a separate task.

**What's embedded.** C examples from `examples/c/02-extended/` and `03-programs/`
(24 files). `examples/c/04-io/` is **skipped**: those use `read()`/`write()`
memory-mapped I/O (`greet_name`/`pin_check` block on user input and never halt
headless), matching the deterministic-run convention and the existing
`CExamples.kt` test exclusion. `SimViewModel.load()` now compiles a `C` example
with ktoy's toycc before assembling (the `when(kind)` is exhaustive so a future
kind can't silently fall through to the ASM path).

**Tests** (`ExampleCatalogTest`, commonTest): grouping returns the four expected
categories in order; a recursive C example is toycc-only while a simple one
lists both; and every embedded C example compiles with ktoy's toycc, assembles
cleanly, and runs to its `// expect: N` value. That both verifies the embedding
and gives the C|asm feature verified data. (The existing `CCompilerTest` already
proves ktoy's `compileSource` output matches pytoy byte-for-byte for these same
sources.)

### 10.3 Not in v1 (deferred)

iOS / JVM desktop / Web targets (the scaffold is Android-only for now; the core
is pure commonMain so adding them later is straightforward). No file load/save
(everything is in RAM; the examples are bundled). The C compiler stays out of
scope (§1.2).

---

## 11. GUI fixes round (Manfred's review of the running app)

A batch of GUI corrections after Manfred ran the simulator on JVM + web. Each is
his request; the pytoy-consistent choice was taken where the ask was open.

1. **Monospace data views.** The C / assembly / memory panels and the I/O widget
   all render through `HighlightRow` / `IoCell`, which use `MonoStyle`
   (`FontFamily.Monospace`). Already correct; verified the `>>` marker and
   columns line up because the data font is truly monospace (the rest of the GUI
   keeps the default font).
2. **Title layout.** The program title (top bar, right) now has `maxLines = 1`,
   `overflow = Ellipsis`, `textAlign = End` and `weight(1f, fill = false)` so a
   long name is truncated instead of breaking/overflowing the bar. General fix,
   not target-specific.
3. **Bottom controls order (pytoy).** `Load · Examples · Reload — gap — Step ·
   Run · steps/sec · Undo · Reset — Edit (far right)`. Load/Examples/Reload are
   surfaced as buttons in the bottom bar (the File menu keeps them too); Edit is
   pushed right by a weight spacer. Known trade-off: on a very narrow phone the
   single row can clip the right end (pytoy also uses one bottom bar); the
   adaptive columns above handle the main content.
4. **No "set ready" input.** ktoy never had a dedicated ready(255) input/button;
   `ready` is the 16th I/O cell and is set by tapping it like any I/O cell. No
   change needed.
5. **I/O format picker in the panel.** Already present (`FormatSelector` chips
   inside `IoPanel`). Default format changed to **ASCII** (`ioFormat` in the VM).
6. **No C-open dialog.** ktoy already loads a C program straight into the three
   columns (`vm.load(example)`); there is no intermediate compiler-choice /
   comparison popup. No change needed.
7. **Double-tap to edit.** Memory cells and the C column now open the edit dialog
   on `onDoubleTap` (`detectTapGestures`); single tap still selects. Long-press
   removed. Works on touch too; mobile also has the bottom-bar Edit button for a
   selected memory cell.
8. **Auto portrait/landscape.** `wide = maxWidth > maxHeight || maxWidth >=
   240.dp * columnCount` — side-by-side when landscape-ish OR wide enough to give
   each shown column a ~240.dp floor, stacked otherwise. Adapts on resize.
9. **PC highlight in the C column.** `vm.pcCLine()` maps the PC's asm row up to
   its C line; that line gets `HL_PC` (yellow), like the asm/memory panels.
10. **Selection fan-out.** `transitiveSelection(..., fromC)`: a **C-line click**
    fans DOWN to all the statement's asm rows + bytes (as before); an **asm /
    memory click** resolves UP to the owning C line but lights ONLY the clicked
    row and its own bytes (no sibling-row fan-out). The VM tracks `selectedFromC`.
11. **Scroll-to-selection (centred).** `scrollToLine()` scrolls each column so the
    focused line (selected line, else PC) lands mid-viewport, on selection change.
12. **steps/sec stepper.** `StepsPerSecInput` = `−` / field / `+` (text
    `OutlinedButton`s, not material-icons — shape adapted from kotlin-common's
    `IntegerInput`, no shared dep and no icons dependency).
    Shows one decimal (e.g. `2.0`, `0.5`); range 0.1..1000, step 0.5; sub-1 values
    allowed.
13. **Line numbers default ON.** `showLineNumbers = true` with the View-menu
    toggle kept. Verified, unchanged.

## 12. Round 3: examples, font, welcome, phone layout

What Manfred asked for, and what was decided:

1. **All pytoy examples in the app.** The generator embeds every subfolder: asm
   01-basics, 02-extended, 03-programs, 04-io (including the input-driven demos
   like `pin_check`; in the app the user types the input), C 02-extended,
   03-programs, 04-io, and the bytecode listings. The earlier "deterministic
   only / programs only" filter was an agent decision, not Manfred's.
   `everyCExampleCompilesAssemblesAndRunsToExpected` still compiles and
   assembles the 04-io C examples but does not run them (they wait on `ready`).
2. **Category order** in the examples tree (ktoy and pytoy): Assembly (the focus),
   Bytecode, C (minicc), C (toycc).
3. **Layout menu.** View → Layout: auto / portrait / landscape. Auto decides by
   width only (side by side once each shown column gets 300 dp), so it reacts to
   resizing. On a phone auto and portrait both stack, by design.
4. **Real monospace font.** `FontFamily.Monospace` was not monospaced on the web
   (no system fonts there), so `>>` and labels shifted columns. ktoy now ships
   DejaVu Sans Mono (`composeResources/font/dejavu_sans_mono.ttf`, license in
   `doc/licenses/LICENSE_DEJAVU.txt`), provided once at the app root
   (`ProvideMonoStyle` / `LocalMonoStyle`) for every data view and the Help text.
   Striped rows are one Text over an AnnotatedString, so they align too.
5. **Welcome dialog** at start: early development, feedback by e-mail
   (`manfred.scheucher@proton.me`, subject "ktoy feedback", body "hi manfred,"),
   "Don't show again". Ported from PoCoView (`WelcomeDialog`, `FeedbackBox`,
   `loadPref`/`savePref`); ktoy adds the Android (SharedPreferences,
   `initPrefs` in MainActivity) and iOS (NSUserDefaults) actuals. Key `welcome`.
6. **Help.** "C: full C vs mini C" explains the two compilers. About has
   clickable links (ktoy, pytoy, the playlist, the LICENSE, Jim Hall's Toy CPU),
   the license note and the feedback box. Links never crash the app when no
   handler exists (`openUriSafely`).
7. **Bottom bar on phones.** The one-row bar needs about 950 dp (1050 dp with
   Reload), so below 1000 dp (1100 dp with Reload) it splits into two rows: Load /
   Examples / Reload with Edit at the right, then the run controls (scrollable if
   needed). Before, Edit was clipped off-screen on phones.
8. **I/O cells per row:** 16, 8 or 4, the most that fit the width for the current
   format (`ioCellsPerRow`, tested). Binary cells are wider and wrap sooner. A
   360 dp phone gets 4 per row, a 390 dp phone 8.
9. **README:** early-development note with the feedback mailto link.

10. **Phone layout** (on a phone in Safari the panels showed only a few rows: the
    menu, the two-row bar and the I/O panel took most of the height and up to
    three stacked panels shared the rest). Manfred chose all four fixes:
    - **Tabs below 600 dp** when stacked: one panel at a time (C / Assembly /
      Memory, FilterChips), full height. The tab lives in the VM (`phoneTab`,
      survives rotation) and falls back to an available panel (`visibleTab`).
    - **Compact bottom bar** below the one-row threshold: 34 dp buttons with tight
      padding and a low steps/sec field instead of Material's 40/56 dp.
    - **Memory weight 2** when stacked on wider screens (it carries the status
      block on top of the 256-byte listing).
    - **I/O panel off by default**, shown automatically when the program uses the
      I/O cells: at load if an instruction operand is in 240..255 (`usesIo`), else
      the first time the running program reads/writes 240..255 (covers
      self-modifying code, C `write()` and bytecode). Once the user toggles it in
      View, auto-show stops for that load. Test: every 04-io example opens it.

## 13. 2026-10-30: About and Examples dialog

Spec (Manfred, same About structure in ktoy, PoCoView and the ksat playground):

- **About**, top to bottom: title "ktoy" only; description "A Toy CPU toolbox:
  simulator, assembler, C compiler."; the yellow feedback box "ktoy is in early
  development. Feedback is very welcome:" with the e-mail link; project lines
  "[name] (MIT): note", only the name is a link: ktoy (this app), pytoy (the
  Python original ktoy is ported from), toycpu (the Toy CPU instruction set by
  Jim Hall, FreeDOS Project); last line "[Videos]: YouTube playlist" (no license
  bracket). At the very bottom one small grey line "sha · committed <date> ·
  built <date>" (" (modified)" if dirty, unknown parts left out). No font
  mention, no License block (reversed by §34).
- **Examples as a dialog** in the ksat playground style (was a full-screen
  overlay where a click loaded at once): an AlertDialog "Examples" with the same
  collapsible tree (category > subfolder), leaves are radio buttons that only
  select; buttons Cancel and Load. If the simulator is running (`vm.running`),
  Load asks "The program is still running. Stop it and load the new example?"
  with "Stop and load" / "Cancel" (`vm.load` stops the run itself).

Decisions:
- No example is preselected (the leaves start hidden in collapsed subfolders), so
  Load is disabled until a leaf is selected.
- No per-example detail line: the first comment line is not uniform (assembly
  starts with a description, C with `// expect: N`, bytecode with the generic
  "Toy CPU – compiled listing" header), so a detail would need a heuristic.
  Open question for Manfred.
- System-back still closes the dialog (AppBackHandler + the dialog's own dismiss).


## 14. 2026-11-03: Split dividers, edit and build menus, disassembler

What Manfred asked for, and what was decided (via AskUserQuestion, after two
fresh-reviewer passes):

1. **Draggable dividers** between the panels, side by side and stacked, so the
   split is not fixed at equal shares. `SplitPane` / `dragSplit` (tested): a drag
   moves only the two neighbours, each keeps at least 80 dp. The shares live in
   the VM (`wideWeights`, `stackedWeights`, per panel), not persisted. Stacked
   memory still starts at weight 2. Resize cursor on desktop and web.
2. **Edit in all three layers.** Double-click / double-tap a column, or Edit… in
   its menu. C and assembly open `EditSourceDialog`; **Save only stores the
   text** as a draft (`cDraft` / `asmDraft`): the column title shows
   "(modified)", the draft is shown as plain text without highlighting, and the
   machine keeps running the old build. Bytecode edits a single cell (the
   existing value dialog).
3. **Build steps are menu items, no buttons in the panel headers.** Menus:
   File | C | Assembly | Bytecode | View | Help.
   - C: Edit…, Compile (C -> asm -> bytes), compiler choice Full C (toycc,
     default) / Mini C (minicc). Switching recompiles right away; on an error
     the old compiler stays. Disabled without C.
   - Assembly: Edit…, Assemble (asm -> bytes). For a C program this drops the
     C (it becomes a plain asm program); an asm draft alone keeps the C until
     Assemble. Disabled without asm.
   - Bytecode: Edit cell…, Disassemble (live memory -> a new asm program).
   - Compile / Assemble are not Reset: Reset goes back to the start of the last
     build; Compile / Assemble build again from the (edited) source.
4. **Confirm before a loss** (`lossesFor` / `request`, tested). One dialog lists
   every loss, nothing is shown when nothing is lost: the C column goes away
   (incl. uncompiled C edits), a run in progress (`stepCount > 0`), memory
   edits (`hasLiveEdits`), unassembled asm edits (Compile / Disassemble), the
   asm's labels and comments (Disassemble of an asm program). Disassemble keeps
   the memory, so there it says the run starts over at PC 0. A running program
   is stopped when the dialog opens.
5. **Disassembler** (`core/Decompiler.kt`, tested on every example): code vs
   data by following the control flow from address 0 (goto, both ifzero
   branches, stop ends a path). Unreachable bytes come out as data. Labels `L12`
   (code) / `D40` (data); an operand pointing into a 2-byte instruction becomes
   `L12+1`; I/O cells keep io0..ready. Re-assembling gives the same bytes in
   0..239.
   After Assemble / Disassemble the program no longer matches the loaded file,
   so Reload is disabled (it would silently bring the old file back). File ->
   Save writes the draft text if there is one.
6. **Examples dialog starts collapsed**: only the category groups at first.
7. Help -> "Edit and build" explains the pipeline.

Agent decisions to check: menu titles are plain clickable text instead of
TextButtons (six TextButtons with Material's 58 dp minimum width don't fit a
360 dp phone); the compiler items are called "Full C" / "Mini C" like the
existing Help text (Manfred said "simple"); Load / Reload / Examples do not use
the loss dialog yet.

UI tests (`jvmTest/.../SimulatorScreenUiTest.kt`, `compose.uiTest`, run with
`jvmTest`) drive the real screen on desktop: divider drag, Assembly -> Edit… /
Save / Assemble, double-click edit, the confirm dialog (Cancel and OK) when
Assemble drops the C, C -> Edit… / Compile, the compiler choice, Bytecode ->
Disassemble, and the C / Assembly menus being disabled for plain bytecode.

Follow-up (Manfred): the compiler switch stays in the C panel as well, as the
old button ("Switch to mini C" / "Switch to full C", naming the other compiler).
It does the same as the C menu's compiler choice (incl. the confirm dialog).

## 15. 2026-11-03: STOP counts as a step

Manfred: the step count after a stop was always one too small; STOP itself is a
step. A program that is just `stop` shows "Stopped after 1 steps." (not 0). The
PC stays on the STOP (it is not advanced), and Undo takes the STOP back like any
step. Same change in pytoy (CLI, GUI undo, `scripts/gen_snapshot.py`), the
snapshot step counts went up by 1 in both repos. Tests: `StopStepTest`, the UI
test `aSingleStopShowsStoppedAfterOneStep`, pytoy `tests/test_stop_step.py`.

## 16. 2026-11-04: Horizontal scrollbar, no phone tabs

- **Horizontal scrollbar** (Manfred: lines too wide for the panel). The C,
  assembly and memory panels already scrolled sideways (Shift + wheel,
  trackpad, touch) but showed nothing. `ScrollbarBox` now draws a bar at the
  bottom when a line is wider than the panel. Both thumbs (right and bottom)
  can be dragged with the mouse or a finger (Manfred chose "vertical too"),
  6 dp thick.
- **No tabs in portrait** (Manfred: "im portrait mode keine tabs sondern die
  widgets übereinander"). Replaces §12.10's tabs below 600 dp: a narrow screen
  stacks the panels like any portrait layout, with the draggable dividers.
- **Welcome dialog feedback text** (Manfred: the Help/About text reads better):
  the welcome dialog now uses the About text "ktoy is in early development.
  Feedback is very welcome:" (one shared constant, `FEEDBACK_TEXT`).
- **steps/sec field as high as the buttons**: the one-row bottom bar used
  Material's OutlinedTextField (56 dp, much taller than the number in it, odd in
  the browser); now both bars use the low bordered field, 40 dp (button height)
  or 34 dp compact. UI test compares the field with the Step button.

## 17. 2026-11-04: Neutral colours, menu bar

- **No pink tint** (Manfred: "warum verpasst du der ganzen app nen leichten
  rotstich?"). It came from Material 3's default scheme (background #FFFBFE,
  dialogs/menus #ECE6F0), not from a recent change. `KtoyTheme` (App.kt) keeps
  the defaults but sets the background and all surface colours to white / plain
  greys.
- **Menu bar looks like one**: the top row (menus + title) sits on a grey bar.
- **One click switches menus**: with File open, a click on View closes File and
  opens View (before, the first click only closed File). One `openMenu` state
  for all menus; the popups are not focusable, so the outside click reaches
  the other title. A click on the open menu's own title closes it.

## 18. 2026-11-04: Bottom bar order, auto-scroll to PC

- **Bottom bar**: Load · Examples · Reload — Reset · Undo · Step · Run ·
  steps/sec. The Edit button is gone (a cell is edited by double-tap or
  Bytecode -> Edit cell…).
- **Auto-scroll to PC** (View menu, on by default): on Step / Run the C,
  assembly and memory panels scroll so the PC's line (">>") is in the middle.
  This existed before but assumed a fixed 34 px row, so the line ended up
  elsewhere; the row height is now measured from the mono font. A selected line
  still wins over the PC. The last lines of a program can't be centred (the
  scroll stops at the end). UI tests check the centring in all three panels,
  the toggle, and the button order.

## 19. 2026-11-04: Fibonacci fib(7), step counts in every example

- Manfred: fibonacci_iter and fibonacci_rec (C, and the asm rec) compute
  fib(7) = 13, with the sequence fib(0)=0 … fib(7)=13 in the header comment
  (asm iter already did). The C versions take 117 / 2121 steps with toycc.
- Every example (pytoy is the source, ktoy bundles it) has machine-checked
  header lines: asm `# result: N` and `# steps: N`, C `// expect: N` and
  `// steps: toycc N, minicc M` (only the compilers that build it); a .toyo
  carries its source's lines in the listing's SOURCE column. STOP counts. The
  I/O examples wait for input and have none.
- Checked twice, headless: pytoy `scripts/example_steps.py` (`--table`,
  `--write`; test `tests/test_example_steps.py`) and ktoy `ExampleStepsTest`
  (ktoy's own CPU and compilers). Both must agree with the headers.
- A fresh reviewer went through all example comments in pytoy and fixed stale
  ones (wrong file names, "see X" links, wrong claims); open points were
  reported to Manfred.

## 20. 2026-11-04: Examples dialog remembers its state

Reopening the Examples dialog shows it as it was left: the same groups open,
the last selected example still selected, the same scroll position
(`ExamplesTreeState`, kept by `App`). The first opening shows only the groups.

## 21. 2026-11-04: Invalid opcodes

- Manfred: keep Jim Hall's toycpu behaviour, an invalid opcode is a NOP. Valid
  are the 14 opcodes (0, 1, 2, 15, 17..25, 128); every other byte is invalid.
- New menu **Execution -> Invalid opcode**: NOP (like toycpu, default) / Stop
  with an error / Always ask.
  - Stop: the bad byte counts as step N (like STOP), the PC stays on it, Undo
    takes it back. Status line and a dialog: "Error in step N: invalid opcode X
    at address A." (a normal halt stays "Stopped after N steps.").
  - Always ask: the run (or Step) pauses and asks "NOP" or "Stop", with
    "Remember this decision" (makes the answer the setting). NOP continues an
    interrupted run.
- Only ktoy has the menu; pytoy keeps the toycpu NOP without asking.
- New bytecode example `invalid_opcode.toyo` (pytoy examples/toyo): load x,
  the byte 3, add x, stop -> ACC 42 as a NOP.
- The disassembler treats an invalid byte on the path as a NOP (the trace goes
  on) and writes it as a data byte with a comment.
- The menu bar scrolls sideways when the seven menus don't fit (phones).
- Review fixes: choosing Stop in the question shows no second error dialog
  (only Stop mode's own halt does); the status block's EXPLAIN line says
  "invalid: stops / asks" instead of "treated as NOP" in those modes; after a
  NOP answer the run waits one step period before going on; a 2-byte invalid
  opcode (operand bit set, e.g. 26) is a 2-byte NOP, also in the disassembler.
  "Invalid opcode" in the Execution menu is a heading over three radio items
  (Material 3 menus have no submenus; same as View -> Layout).

## 22. 2026-11-04: Start with 00_stop

The app opens with the example 00_stop (Assembly / 01-basics) loaded instead
of an empty machine (`loadStartExample`, falls back to empty if missing).

## 23. 2026-11-04: Save changes?, Save as, context menu, Escape

Spec (Manfred): if something was edited, ask whether to save; a save offers
C / asm / bytecode, depending on what is visible; a run alone is no change, only
an edit in one of the widgets. Escape closes a menu. Instead of a double-click:
right-click on JVM + web, long-press on Android and also web, opening a menu
with "Edit" as the only option.

Decisions (asked first):
- The question comes before Load, Examples (at the dialog's Load), Reload and
  closing the desktop window. Not on the web or Android: there is no window
  close to intercept. Not before Compile / Assemble (those have the loss dialog).
- A change = any edit since the last load or save, built or not: C / asm text, a
  memory cell. It stays a change after Compile / Assemble, until saved to a file.
- "Save changes?": Save… / Discard / Cancel. Save… opens the "Save as" picker;
  the highest level is preselected (C, else assembly, else bytecode). With only
  bytecode there is nothing to pick, so it saves right away.

Interpretations (not asked, tell Manfred):
- Typing into an I/O cell (240..255) is no edit: it is the program's input.
- Any successful save clears the change, whichever format was chosen (saving
  only the bytecode of an edited C program also counts as saved).
- Cancel in the file dialog or the picker also cancels the pending Load / Reload
  / close.
- A compiler switch and Disassemble are no edits by themselves.

Context menu: `EditMenu.kt`. Right-click on JVM + web, long-press on Android, iOS
and web (`platformEditGestures`). The double-click is gone. On a memory row Edit…
edits that cell. A right-click does not select the row.

Escape closes an open menu: the popups are not focusable (for the one-click
switch), so the bar takes the focus while a menu is open. Android Back was not
part of the request and still does not close a menu.

Code-review fixes: the menu bar reads `tick` (C / Assembly enabled state could go
stale); Compile of an edited C turns Reload off, like Assemble and Disassemble;
loading a C program clears the memory selection; Disassemble over an asm draft
lists the "labels and comments" loss too; a wrong comment and a German one fixed.

Fresh-reviewer fixes: Android Save wrote nothing (the text stash was not
remembered across recompositions); Cmd+Q / the macOS Quit menu now asks too; a
`.toys` with assemble errors shows the error and leaves the program and edits
alone (it used to replace the program); setting a cell to its current value is
no edit; a failed file write on the desktop shows a message instead of crashing
and keeps the edits.

## 24. 2026-11-04: Compact bytecode view

Spec (Manfred): the bytecode view has too much whitespace; address and value can
be much more compact, too many leading spaces. Rows are now
`>>   3 (00000011) ->  42 (00101010)`, no extra padding; the header says
`addr (binary)` so it fits the column.

## 25. 2026-11-04: What counts as saved

Spec (Manfred): Reload reads the file from disk again; an edit counts as saved
only when the chosen format contains it; ask if the user saves something that
isn't the most current. Compile and Assemble stay manual (no auto-compile: it can
fail and discards memory edits and the run). Clarified with a reviewer; these
were decided by Manfred:
- A compiled C edit saved only as asm or bytecode stays unsaved: the C text is not
  in that file. Same rule for each layer: C edit -> .toyc, asm edit -> .toys,
  memory edit -> .toyo.
- A bytecode save writes the program as built plus hand edits, nothing a run
  stored. Disassemble still takes the live memory (Manfred's earlier choice).
- Saving the program's highest level makes that file the current one (Reload,
  title). A lower level is only an export; Reload stays on the old file.

Decided by Claude with the reviewer:
- Unsaved state is derived per format from what the last load or save held, so a
  reverted edit (text back to the original, cell back to its value, Undo, Reset)
  leaves nothing unsaved.
- If the chosen format lacks an edit, "This file lacks your edits" asks first:
  Save anyway / Back. Such an edit stays unsaved.
- In the "Save changes?" flow the pending action goes on only when nothing is
  unsaved; otherwise the dialog returns and names what is left. Discard is the
  only way to lose an edit there.
- A build that drops an unbuilt draft after its confirm dialog leaves no edit. A
  compiled C edit moves to the asm when Assemble drops the C; a built edit moves
  to the asm when Disassemble replaces the program.
- The picker keeps preselecting the highest level (Manfred's rule) even when only
  bytecode is left unsaved.
- A reloaded file that can't be read shows an error and keeps the program.

## 26. 2026-11-05: Bytecode files, minisim

Manfred: the bytecode export should really show only the values of the 256
cells, up to the last non-zero one; comments are allowed in such a file, the
listing and symbols are not wanted. Decisions (same in pytoy):

- Save -> Bytecode writes one integer per line, up to the last non-zero cell (an
  all-zero program is an empty file). No comments are written. Extension stays
  `.toyo`.
- Loading tells the formats apart by content: only single integers (`#` comments
  allowed) is bytecode, anything else is the old byte listing. Too many lines, a
  value above 255, or text that is neither format is a load error and keeps the
  program. An empty or comment-only file is an all-zero program. `minisim` is just
  as strict: one integer per line.
- Hand edits to the I/O cells (240..255) are not part of the saved program. Values
  a loaded file has there are.
- After a bytecode save, Undo of a saved edit counts as unsaved: the file still
  holds it. Reset never asks.
- `run.py asm` and the bundled `examples/toyo` stay byte listings: the example
  check reads their `# result` and `# steps` comments.
- pytoy's Save now writes the program as built plus hand edits, like ktoy.

`minisim` (`pytoy/minisim.py`, `jvmMain/.../MiniSim.kt`): one standalone file that
reads such a bytecode file and runs it. One line per step, state before the step:
`step=1 acc=0 pc=0 op=20`. `-v` adds a memory dump under each line,
`x_000...x_015:` and 16 cells, width 3 plus one marker char: `*` behind the cell
the PC points at, else a space. The end prints `stopped after N steps,
acc=A`. No I/O (cells 240..255 are plain memory) and no step limit.

## 27. 2026-11-05: Simpler: no invalid-opcode modes, fewer buttons

Manfred: keep the code simple.

- An invalid opcode does nothing, like toycpu. The **Execution** menu, its Stop /
  Always ask modes and their dialog are gone (replaces §21's menu). A two-byte
  invalid opcode is still a two-byte NOP.
- The bottom bar no longer has **Load** and **Examples**: both are in the File
  menu. Reload stays on the desktop (replaces the bar in §23).
- The edit dialogs have **Save** and **Save & Compile** / **Save & Assemble**
  instead of a hint in the field label. Building still asks first if it would
  lose something, and a failed build keeps the text as a draft.
- The context menu on C and assembly rows offers **Edit…** (whole text) and **Edit
  line…**: just that row's line, with Cancel / Save / Save & Compile (Assemble).
- Bytecode rows and the Bytecode menu offer the same two items, with only Cancel /
  Save, and take effect at once. **Edit…** shows all 256 cells as decimal integers,
  one per line. Each line may also be binary (`b1010`) or hex (`0x1f`); files stay
  plain integers. Missing lines at the end count as 0; more than 256 lines or a bad
  value shows an error in the dialog and changes nothing. It is one Undo step.
- On a narrow window the bottom bar wraps as needed: Reset, Undo and Step stay
  together, Run and steps/sec stay together, and all fit on one line if there is room.
- The whole-text edit dialogs show line numbers in front of the lines (not part of
  the text): from 1 for C and assembly, from 0 for bytecode, where the number is
  the address.
- `invalid_opcode.toyo` is gone: such a byte is just a NOP, nothing special to show.
  The bytecode examples are fibonacci, multiply_repeated (6 * 7 by addition) and sum3.
- Load in the Examples dialog no longer asks "still running, stop it?": it just stops
  the run, like File -> Load and Reload.

## 28. 2026-11-06: Public repo, no kotlin-common, README only about ktoy

- ktoy is a public repo and kotlin-common is private, so the unused submodule is gone.
  Nothing had to be copied: nothing in ktoy used it. Older mentions in this file are history.
- The README and the About dialog no longer mention pytoy. This doc, code comments and
  the example generators still do.

## 29. 2026-11-06: Select view in the cell editor

- Bytecode "Edit line" opens on a Select view: the operations in opcode order, labelled
  with their code (`STOP(0)`, `RIGHT(1)`, ...), then the values 0..255 in a 16x16 grid.
- A click writes the value and closes the dialog (Interpretation of "sobald man da drauf
  klickt schreibt ers hin"), so this view has only Cancel.
- A Select / Type toggle on top; Type is the usual input field. The last view used is
  remembered (`editCellView`), on all platforms.
- A narrow dialog (portrait) has 8 values per row instead of 16, at the normal font size,
  with a scrollbar (Manfred's suggestion: 8x32 in portrait).

## 30. 2026-11-07: Edit dialogs, right-click highlight, example comments

- Multiline edit dialogs (C, assembly, bytecode) have a vertical and a horizontal scrollbar like
  the code panels, no line wrap, and the scroll follows the cursor.
- Whole-bytecode edit: Cancel / Save / Save & Disassemble. Save & X checks the text first.
  Opening it stops a run: Save writes the differences to the text shown.
- Right-click or long-press highlights the row orange, also rows without an address (comments,
  blank lines, draft lines, C lines without code).
- A build clears the selection.
- Help -> Instruction set: no shortcuts list (the app has none), no "like toycpu".
- Examples (written in pytoy, generated here): header `example`, `description`, `steps`, `result`
  in this order; `// expect:` became `// result:`. Comments carry real information only, one wording
  per concept, MSB explained at first mention. `bit_set_clear_toggle` now toggles x, not r_set.
- Examples are demos, not hardened code: no dead code or unused labels, except where unreachable code
  is the point (`00_stop`, `11_goto`). Input limits are stated in the description, not checked:
  longer input may overflow (`greet_name`, `sort_array_io`).
- A right-click or long-press does not scroll the clicked panel, so the row stays under the menu;
  the next tap or step scrolls as usual. Other panels still scroll to the selection.
- Whole-bytecode edit: line i is cell i. A blank or comment line is a 0, trailing ones are dropped.
- Disassemble warns when cells 240..255 hold values: the disassembly covers 0..239 only.
- Same program, same data in assembly and C: `greet_name` writes "Hallo NAME" (up to 9 characters),
  `multiply_*` 7 * 6, `sum_array`/`max_array` 10 values, `times7_*` in `02-extended`.

## 31. 2026-11-09: File -> Empty, Save dialog, Help pages, big dialogs, auto build

- File -> Empty folds out C / Assembly / Bytecode in the menu (Material3 menus have no real
  submenus). C: `int main(void) { return 0; }`, assembly: `stop`, bytecode: all zeros. Asks first
  when edits are unsaved.
- File -> Save opens one dialog: a full-width button per format the program has, highest level
  first ("Save C", "Save assembly", "Save bytecode"), then Cancel. Leaving with unsaved edits
  shows the same buttons with Discard and Cancel. Only one format: saves at once. A format that
  lacks an edit still asks first ("This file lacks your edits"). Design from a fresh reviewer,
  confirmed by Manfred over his first idea (Save C / Save asm / Save bytecode / Don't save).
- Help: General (short overview of everything), Instruction set, C compilers, About. No caption
  plus sub-caption style, plain paragraphs, monospace only for the instruction table. C compilers:
  mini C first, full C as "extends mini C by …". Edit and build is folded into General.
- Content dialogs (Help, the multiline editors, Examples) fill the window but a 16 dp margin, on
  every platform. Short questions stay small.
- C / Assembly / Bytecode menus: Auto compile / assemble / disassemble on edit. Off at start, not
  remembered, at most one on (assemble then disassemble would undo each other). Auto disassemble
  follows bytecode edits of cells 0..239 only, so typing into the I/O panel never rebuilds.
  Save & X in an edit dialog does not build a second time.
- Every C example's result is what main returns: both compilers leave it in ACC at stop.
- The panels centre a line only after their first layout (the memory column opened scrolled by
  about two rows, hiding its header and address 0).
- Disassemble reads more like the hand-written examples (Manfred: "assembly to bytecode and back
  should give about the same code"; comments and names may differ). Checked on every assembly
  example: same structure, only labels differ. Changes: the columns as in the examples
  (`load  x`), a blank line after goto and stop, `# data` before the data at the end, an operand
  the program patches stays a number with "# patched by the program", and a data cell that a
  load carries into such a patch (`load ptr; add i; store L5+1`) is written as the address it
  holds (`D35`, `io0`). A pointer that is 0 or points past the last value stays a number.

## 32. Look like sat-chess-kotlin

Prompt: "kannst du bitte wie in ../sat-chess-kotlin den style machn? da is obn ne schöne leiste … evtl color theme etc auch anpassen".

- The menu bar is one grey strip (#E6E8EB) across the full width at the top, no rounded corners or margin; the program title stays on its right.
- The colour scheme is the sat-chess one: teal primary (#2F5D62), neutral white/grey surfaces. The highlight colours of the panels are unchanged.

## 33. Confirm dialog text, auto build confirm

Prompts: "beim disamble dialog wenn er nachfragt is vorn ein komischer punkt . da is auch zu viel text in klammern … schreib konkret dass beim disassemblen verworfen wird"; "beim auto disassemble kannst auch den dialog anzeigen, nur wenn bestätigt toggle aktivieren; analog für auto compile und auto assemble".

- The confirm dialog is one sentence without bullets or brackets: "Disassemble discards the C code, your C edits and the current run."
- Switching an auto toggle on builds once right away. If that build discards something, the same confirm dialog comes first, and the toggle only goes on with its OK (Manfred's choice). Cancel leaves it off.
- While a toggle is on, the builds it triggers never ask: switching it on was the OK.
- Picking a toggle closes the menu.

## 34. View settings remembered, license texts in About

From the comparison with ksat-chess and the ksat playground; Manfred: "alles".

- View -> Line numbers, Auto-scroll to PC and Layout are remembered across starts (prefs `lineNumbers`, `autoScroll`, `layout`; loaded by SimViewModel, saved by the View menu), as in ksat-chess. Nothing stored: the old defaults (on, on, auto). The auto build toggles stay per session (§31).
- View -> I/O panel is not remembered: each load decides it. An example shows it exactly when its `io:` header says yes (§35).
- About gets the "License texts" toggle of ksat-chess. This reverses §13 ("no License block"). The texts are generated at build time (generateLicenses, ported from ksat-chess) from the files: `LICENSE` (ktoy, MIT), `doc/licenses/LICENSE_TOYCPU.txt` (copied verbatim from freedosproject/toycpu, MIT, Jim Hall) and `doc/licenses/LICENSE_DEJAVU.txt` (the bundled DejaVu Sans Mono font).
- JVM tests keep preferences in memory (`-Dktoy.prefs=memory`), so they never touch the real desktop preferences.

## 35. Examples state their I/O use; examples live in ktoy

Manfred: the examples say in their header whether they use I/O ("hab ich dir nicht scho mehrfach erklärt dass ktoy examples i/o annotation habn sollen"), and the examples move to ktoy ("Nur in ktoy"; "pytoy darfst du ruhen lassen. gib das ganze nach ktoy").

- `examples/` (asm, c, toyo) is copied from pytoy and is now the source of truth. `scripts/gen_example_sources.py` reads it; nothing in ktoy uses a pytoy checkout any more. Not copied: pytoy's `examples/broken` (unused) and the compiled `.toys` next to the C sources (pytoy build output). The toyo README described pytoy's command line and was dropped.
- Every example has a header line `# io: yes` / `# io: no` (`// io: ...` in C, inside the listing comments in `.toyo`), after the other header lines. Yes exactly for the 15 examples in `04-io`; a test checks the header against what each program does (an operand in 240..255, statically or within 5000 steps).
- Loading an example shows the I/O panel exactly when its header says yes, and it never opens by itself while the example runs. Other programs (files, File -> Empty, builds after an edit) keep the old rule: shown if an operand is in 240..255, else opened the first time the running program touches an I/O cell.
- Which compilers accept a C example is decided at runtime with ktoy's own compilers (`detectCompilers`: compiles and fits in 240 bytes), lazily when the Examples dialog first needs it; app start never compiles C. So `bigint_add` and `sort_array_inline` are listed under toycc only (minicc's output does not fit).
- The compiler goldens (`CExamples.kt`, `MiniCExamples.kt`) stay as frozen fixtures; the scripts that made them from pytoy are removed.
