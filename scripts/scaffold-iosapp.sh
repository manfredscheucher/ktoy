#!/usr/bin/env bash
# Scaffold ktoy's iosApp/ (Xcode project + Swift entry) from the OpenYarnStash
# template, adapting names to ktoy. Idempotent-ish: refuses to clobber an
# existing iosApp/ so a hand-tweaked project isn't lost. Run once; after that the
# Xcode project is committed and maintained in Xcode.
#
# The Compose UI is a Kotlin/Native framework (ComposeApp); Xcode's build phase
# calls ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode to build it.
# Needs a macOS machine with Xcode to actually open/build (not verifiable here).
set -e
cd "$(dirname "$0")/.."

OYS="${OYS:-$HOME/github/openyarnstash}"
DEST="iosApp"

if [ -d "$DEST" ]; then
    echo "iosApp/ already exists — not overwriting. Delete it first to re-scaffold."
    exit 0
fi
if [ ! -d "$OYS/iosApp" ]; then
    echo "OpenYarnStash template not found at $OYS/iosApp (set OYS=/path/to/openyarnstash)."
    exit 1
fi

echo "Copying iosApp skeleton from $OYS …"
mkdir -p "$DEST/iosApp" "$DEST/iosApp.xcodeproj/xcshareddata/xcschemes"

# Swift sources (identical across apps bar the struct name — ktoy keeps iOSApp).
cp "$OYS/iosApp/iosApp/iOSApp.swift"     "$DEST/iosApp/iOSApp.swift"
cp "$OYS/iosApp/iosApp/ContentView.swift" "$DEST/iosApp/ContentView.swift"

# Minimal Info.plist (ktoy needs no camera/photo permissions).
cat > "$DEST/iosApp/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CADisableMinimumFrameDurationOnPhone</key>
	<true/>
</dict>
</plist>
PLIST

# Xcode project + scheme, with OYS names rewritten to ktoy.
sed -e 's/OpenYarnStash/ktoy/g' -e 's/openyarnstash/ktoy/g' \
    "$OYS/iosApp/iosApp.xcodeproj/project.pbxproj" > "$DEST/iosApp.xcodeproj/project.pbxproj"
if [ -f "$OYS/iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme" ]; then
    sed -e 's/OpenYarnStash/ktoy/g' -e 's/openyarnstash/ktoy/g' \
        "$OYS/iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme" \
        > "$DEST/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme"
fi
# workspace wrapper (points Xcode at the project)
mkdir -p "$DEST/iosApp.xcodeproj/project.xcworkspace"
if [ -f "$OYS/iosApp/iosApp.xcodeproj/project.xcworkspace/contents.xcworkspacedata" ]; then
    cp "$OYS/iosApp/iosApp.xcodeproj/project.xcworkspace/contents.xcworkspacedata" \
       "$DEST/iosApp.xcodeproj/project.xcworkspace/contents.xcworkspacedata"
fi

echo "Done. NEXT (needs macOS + Xcode — not verifiable headlessly):"
echo "  1. open iosApp/iosApp.xcodeproj in Xcode"
echo "  2. set the signing team + bundle id (org.bytefred.ktoy)"
echo "  3. build/run via ./scripts/runIosSimulator.sh (or Xcode)"
