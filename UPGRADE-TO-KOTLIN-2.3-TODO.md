# Remaining: Compose MP 1.12.1 + AGP 9.1 (Kotlin 2.3.21 already done)

Kotlin was bumped to **2.3.21** + coroutines **1.11.0** (commit on main), and the
app now builds for Android, JVM-desktop and Web (js). What's left is the part of
the sibling-app matrix that forces an AGP upgrade:

## Still TODO
- **Compose Multiplatform 1.10.3 → 1.12.1.** Blocked: 1.12.1 pulls
  `androidx.compose.ui:ui-android:1.12.1`, which requires **AGP 9.1.0+**. We're on
  AGP **8.13.2**, so CMP stays at 1.10.3 for now. Do the CMP bump together with:
- **AGP 8.13.2 → 9.1.0+ and compileSdk 36 → 37.** Needs AGP 9.x downloaded and an
  Android 37 platform installed locally (neither present as of this writing).
- **kotlinBrowser 2025.9.11 → 2026.9.3.** Blocked on Kotlin 2.4: 2026.9.3 pulls
  `kotlin-stdlib-js 2.4.20` (ABI 2.4), unreadable by the 2.3.21 compiler. Only bump
  once the project moves to Kotlin 2.4.x.

## Reference (sibling apps already on the full matrix)
OYS commit c0f1829, MisterManager cdfd66a, kotlin-common f4bc8ab. NOTE: those were
not verified to build for Android with this matrix (no fresh APK, no local AGP 9.x);
the AGP/compileSdk step is the real unknown.

## Notes from the Kotlin-2.3 step
- Dropped the `ui-backhandler` CMP module (it needed AGP 9.1); replaced with an
  `AppBackHandler` expect/actual (Android real, jvm/web no-op).
- Dropped the unused `:core` (kotlin-common) submodule dependency: ktoy never used
  the Logger, and `:core`'s commonMain doesn't compile for js on Kotlin 2.3
  (`kotlin.time.Instant`). Re-add via settings.gradle.kts + composeApp deps if the
  Logger is ever needed — and give :core a `js()` target in the submodule.

Delete this file once CMP 1.12.1 + AGP 9.1 land.
