#!/usr/bin/env bash
# Build, install and launch ktoy on a CONNECTED Android device (USB).
# Usage:
#   ./scripts/runAndroidDevice.sh              # the first connected physical device
#   ./scripts/runAndroidDevice.sh <serial>     # a specific device by adb serial
#   ./scripts/runAndroidDevice.sh --no-build   # install the existing APK, don't rebuild
#   ./scripts/runAndroidDevice.sh --log        # stream logcat (filtered) after launch
#
# Needs the Android SDK platform-tools (adb) and USB debugging enabled on the
# device. Machine-specific per-device scripts live in _private/scripts/.
set -e
cd "$(dirname "$0")/.."

ANDROID_SDK="${ANDROID_SDK:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_SDK/platform-tools/adb"
PACKAGE="org.bytefred.ktoy"
ACTIVITY="org.bytefred.ktoy.MainActivity"

[ -x "$ADB" ] || { echo "Error: adb not found at $ADB (set ANDROID_SDK)." >&2; exit 1; }

NO_BUILD=false; LOG=false; SERIAL=""
for arg in "$@"; do
    case "$arg" in
        --no-build) NO_BUILD=true ;;
        --log) LOG=true ;;
        *) SERIAL="$arg" ;;
    esac
done

# Pick the first connected PHYSICAL device (exclude emulators) if none given.
if [ -z "$SERIAL" ]; then
    SERIAL="$("$ADB" devices | awk '$2=="device" && $1!~/^emulator-/ {print $1; exit}')"
fi
[ -n "$SERIAL" ] || { echo "Error: no connected device. Plug one in + enable USB debugging." >&2; exit 1; }
echo "Device: $SERIAL"

if [ "$NO_BUILD" = true ]; then
    echo "Installing existing APK…"
    "$ADB" -s "$SERIAL" install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
else
    echo "Building + installing…"
    ANDROID_SERIAL="$SERIAL" ./gradlew :androidApp:installDebug
fi

echo "Launching $PACKAGE…"
"$ADB" -s "$SERIAL" shell am start -n "${PACKAGE}/${ACTIVITY}"
echo "Launched on $SERIAL."

if [ "$LOG" = true ]; then
    echo "Streaming logs (Ctrl+C to stop)…"
    "$ADB" -s "$SERIAL" logcat | grep -iE "ktoy|org.bytefred"
fi
