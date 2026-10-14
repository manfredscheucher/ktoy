#!/usr/bin/env bash
# Build, install and launch ktoy on the Android EMULATOR.
# Usage:
#   ./scripts/runAndroid.sh                 # first available AVD
#   ./scripts/runAndroid.sh Pixel_7         # a specific AVD by name
#   ./scripts/runAndroid.sh --no-build      # install the existing APK, don't rebuild
#   ./scripts/runAndroid.sh --log           # stream logcat (filtered) after launch
#
# Needs the Android SDK (platform-tools + emulator). Machine-specific device
# scripts (per-serial) live in _private/scripts/; this is the generic one.
set -e
cd "$(dirname "$0")/.."

ANDROID_SDK="${ANDROID_SDK:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_SDK/platform-tools/adb"
EMULATOR="$ANDROID_SDK/emulator/emulator"
PACKAGE="org.bytefred.ktoy"
ACTIVITY="org.bytefred.ktoy.MainActivity"
SERIAL="emulator-5554"

[ -x "$ADB" ] || { echo "Error: adb not found at $ADB (set ANDROID_SDK)." >&2; exit 1; }
[ -x "$EMULATOR" ] || { echo "Error: emulator not found at $EMULATOR." >&2; exit 1; }

NO_BUILD=false; LOG=false; AVD=""
for arg in "$@"; do
    case "$arg" in
        --no-build) NO_BUILD=true ;;
        --log) LOG=true ;;
        *) AVD="$arg" ;;
    esac
done

AVD="${AVD:-$("$EMULATOR" -list-avds | head -1)}"
[ -n "$AVD" ] || { echo "Error: no AVD found. Create one in Android Studio." >&2; exit 1; }
echo "AVD: $AVD"

# Boot the emulator if it isn't already running.
if ! "$ADB" devices | grep -q "^emulator-"; then
    echo "Starting emulator…"
    "$EMULATOR" -avd "$AVD" -no-snapshot-load >/dev/null 2>&1 &
fi
echo "Waiting for the emulator to boot…"
"$ADB" wait-for-device
while [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    sleep 2
done

if [ "$NO_BUILD" = true ]; then
    echo "Installing existing APK…"
    "$ADB" -s "$SERIAL" install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
else
    echo "Building + installing…"
    ANDROID_SERIAL="$SERIAL" ./gradlew :androidApp:installDebug
fi

echo "Launching $PACKAGE…"
"$ADB" -s "$SERIAL" shell am start -n "${PACKAGE}/${ACTIVITY}"
echo "Launched on the emulator."

if [ "$LOG" = true ]; then
    echo "Streaming logs (Ctrl+C to stop)…"
    "$ADB" -s "$SERIAL" logcat | grep -iE "ktoy|org.bytefred"
fi
