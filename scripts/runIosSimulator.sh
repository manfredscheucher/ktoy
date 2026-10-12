#!/usr/bin/env bash
# Build and run ktoy on the iOS Simulator (macOS + Xcode only).
# The Compose app is a Kotlin/Native framework consumed by iosApp/; Xcode's build
# phase calls ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode to build it.
# Usage:
#   ./scripts/runIosSimulator.sh                   # a booted iPhone, else first available
#   ./scripts/runIosSimulator.sh "iPhone 16 Pro"   # a specific simulator by name
set -e
cd "$(dirname "$0")/.."

case "$(uname -s)" in
    Darwin) ;;
    *) echo "runIosSimulator.sh needs a macOS host with Xcode." >&2
       echo "Current system: $(uname -s)." >&2; exit 1 ;;
esac
command -v xcodebuild >/dev/null 2>&1 || { echo "Error: xcodebuild not found (install Xcode)." >&2; exit 1; }
[ -x ./gradlew ] || { echo "Error: ./gradlew not found — run from the ktoy checkout." >&2; exit 1; }

PROJECT="iosApp/iosApp.xcodeproj"
SCHEME="iosApp"
APP_NAME="ktoy"
DERIVED="iosApp/DerivedData"
[ -d "$PROJECT" ] || { echo "Error: $PROJECT not found." >&2; exit 1; }

SIM_NAME="${1:-}"
# Pick a simulator: the named one, else a booted iPhone, else the first available iPhone.
if [ -n "$SIM_NAME" ]; then
    SIM_UDID="$(xcrun simctl list devices available | grep -F "$SIM_NAME" | grep -oE '[0-9A-F-]{36}' | head -1)"
else
    SIM_UDID="$(xcrun simctl list devices | grep -i iphone | grep '(Booted)' | grep -oE '[0-9A-F-]{36}' | head -1)"
    [ -n "$SIM_UDID" ] || SIM_UDID="$(xcrun simctl list devices available | grep -i iphone | grep -oE '[0-9A-F-]{36}' | head -1)"
fi
[ -n "$SIM_UDID" ] || { echo "Error: no iPhone simulator found. Create one in Xcode > Settings > Platforms." >&2; exit 1; }
echo "Simulator: $SIM_UDID"

echo "Building (xcodebuild, iphonesimulator)..."
xcodebuild -project "$PROJECT" -scheme "$SCHEME" -configuration Debug \
    -sdk iphonesimulator -destination "id=$SIM_UDID" -derivedDataPath "$DERIVED" build

APP_PATH="$DERIVED/Build/Products/Debug-iphonesimulator/$APP_NAME.app"
[ -d "$APP_PATH" ] || { echo "Error: built app not found at $APP_PATH." >&2; exit 1; }

# Read the actual bundle id from the built app (avoids guessing TEAM_ID suffixes).
APP_ID="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$APP_PATH/Info.plist" 2>/dev/null)"
[ -n "$APP_ID" ] || { echo "Error: could not read CFBundleIdentifier from $APP_PATH." >&2; exit 1; }
echo "Bundle id: $APP_ID"

echo "Booting simulator (if needed)..."
xcrun simctl bootstatus "$SIM_UDID" -b >/dev/null 2>&1 || xcrun simctl boot "$SIM_UDID" || true
open -a Simulator || true

echo "Installing..."
xcrun simctl install "$SIM_UDID" "$APP_PATH"
echo "Launching $APP_ID..."
xcrun simctl launch "$SIM_UDID" "$APP_ID" || { sleep 3; xcrun simctl launch "$SIM_UDID" "$APP_ID"; }
echo "Launched '$APP_NAME' on the simulator."
