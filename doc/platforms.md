# Platforms: build & run (Android / Desktop / Web / iOS)

ktoy is a Kotlin Multiplatform + Compose app. The CPU core, assembler, compiler,
and the whole simulator UI live in `commonMain`; each platform is a thin shell.
This note records how to build and run each target, and the platform-specific
bits (file I/O, version info, the menus).

## Targets

| Target  | Gradle                               | Entry point |
|---------|--------------------------------------|-------------|
| Android | `androidLibrary` (KMP) + `:androidApp` | `androidApp/.../MainActivity.kt` |
| Desktop | `jvm()`                              | `composeApp/src/jvmMain/.../main.kt` |
| Web     | `js { browser() }`                   | `composeApp/src/jsMain/.../main.kt` |
| iOS     | `iosArm64()`, `iosSimulatorArm64()`  | `iosApp/` (Xcode) → `MainViewController.kt` |

iOS uses a static `ComposeApp` framework consumed by the Xcode project in
`iosApp/`; Xcode's build phase calls `:composeApp:embedAndSignAppleFrameworkForXcode`.
`iosX64` (Intel-mac simulator) is omitted — all dev machines here are Apple
Silicon.

## Build

```bash
git submodule update --init --recursive        # kotlin-common (shared logger)

# compile each target (no run):
./gradlew :composeApp:compileAndroidMain                 # Android
./gradlew :composeApp:compileKotlinJvm                   # Desktop
./gradlew :composeApp:compileKotlinJs                    # Web
./gradlew :composeApp:compileKotlinIosSimulatorArm64     # iOS (slow, K/Native)

./gradlew :composeApp:assembleDebug        # Android debug APK
```

## Run

```bash
./scripts/runJVM.sh            # JVM desktop window  (:composeApp:run)
./scripts/runWeb.sh            # web dev-server on http://localhost:8604 + Chrome
./scripts/runAndroid.sh        # Android emulator (installDebug + launch)
./scripts/runAndroidDevice.sh  # connected Android device over USB
./scripts/runIosSimulator.sh   # iOS Simulator (macOS + Xcode only)
```

The Android scripts take `--no-build` (install the existing APK) and `--log`
(stream filtered logcat). Machine-specific per-device scripts (fixed serials)
live in `_private/scripts/`.

The web dev-server port is **8604** (block 8600–8619, see `~/github/.dev-ports.md`),
set in `composeApp/webpack.config.d/watch.js` and mirrored in `scripts/runWeb.sh`.

iOS needs a macOS host with Xcode. First time, scaffold the Xcode project from the
OpenYarnStash template:

```bash
./scripts/scaffold-iosapp.sh   # creates iosApp/ (refuses to clobber an existing one)
# then: open iosApp/iosApp.xcodeproj, set the signing team + bundle id (org.bytefred.ktoy)
```

## Tests

```bash
./gradlew :composeApp:jvmTest              # commonTest on the JVM (fast)
./gradlew :composeApp:testDebugUnitTest    # commonTest on Android
```

`commonTest` runs the same tests on every target. The snapshot table asserts ktoy
computes the exact same final accumulator + step count as pytoy for each bundled
example, so the two can't drift.

## The menus (File / View / Help)

A cross-platform Compose menu bar (`ui/MenuBar.kt`), ported from pytoy's menus:

- **File** — Examples… (the bundled example browser), Load…, Reload, Save…
  (with a format picker). See *File I/O* below.
- **View** — Line numbers (toggles the gutter in the C and assembly panels),
  I/O panel.
- **Help** — Instruction set & shortcuts (a reference built from `ToyCpu.INSTR`
  so it can't drift), About (version + commit from `GeneratedVersionInfo`).

## File I/O (`ui/FileIo.kt` — expect/actual)

A platform shim in the style of `AppBackHandler`. Load opens a picker and returns
the file's text; Save writes/downloads the chosen format.

| Platform | Load            | Save             | Reload | Notes |
|----------|-----------------|------------------|--------|-------|
| Desktop  | Swing `JFileChooser` | Swing save dialog | yes | real file path |
| Android  | SAF `OpenDocument`   | SAF `CreateDocument` | no | content:// is not a stable path |
| Web      | `<input type=file>` + FileReader | Blob download | no | one-shot blob |
| iOS      | `UIDocumentPicker`   | `UIActivityViewController` (share) | no | security-scoped URL |

Save offers only the formats that fit the loaded program (`SimViewModel.saveFormats`):

- `.toyo` — always; the **live** byte image (listing rebuilt from current memory),
  so hand edits and runtime stores are saved. Round-trips through `parseToyo`.
- `.toys` — only with assembly source.
- `.toyc` — only for a C program.

Reload (re-read the last file) exists only where there is a stable path (desktop);
the menu item is hidden otherwise.

## Version info (`GeneratedVersionInfo`)

Generated at build time by the `generateVersionInfo` Gradle task (ported from
OpenYarnStash). The version number comes from `gradle.properties` (`version=`);
the commit sha / dirty flag / commit date come from git. Written into
`commonMain`, so every target — including iOS and web, which have no runtime git —
shows it in Help → About. Bump the version in `gradle.properties`.
