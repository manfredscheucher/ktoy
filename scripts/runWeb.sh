#!/usr/bin/env bash
# Run the web (Kotlin/JS) build in a browser via the Gradle webpack dev-server
# (hot-reload). Opens Chrome in a phone-sized window, like the Android/JVM runners.
#
#   ./scripts/runWeb.sh               # serve on :8604, open normal Chrome
#   NO_CHROME=1 ./scripts/runWeb.sh   # serve only, open the URL yourself
#
# testWeb.sh calls this with FRESH_PROFILE=1 to start from a clean localStorage
# (isolated Chrome --user-data-dir).
#
# Port: 8604 (set in composeApp/webpack.config.d/watch.js; see ~/github/.dev-ports.md).
# Change it in BOTH that file and the PORT= line below if ever needed.
set -euo pipefail
cd "$(dirname "$0")/.."

PORT=8604
URL="http://localhost:$PORT/"
CHROME="${CHROME_PATH:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
TASK=":composeApp:jsBrowserDevelopmentRun"
LOG="/tmp/ktoy-runweb.log"

echo "Starting Gradle dev-server ($TASK) …  logs: $LOG   (Ctrl-C to stop)"
./gradlew "$TASK" >"$LOG" 2>&1 &
GRADLE_PID=$!
PROFILE_DIR=""
cleanup() { kill "$GRADLE_PID" 2>/dev/null || true; [ -n "$PROFILE_DIR" ] && rm -rf "$PROFILE_DIR"; }
trap cleanup EXIT

# Wait for the dev-server to accept connections (cold Gradle can take a while).
echo -n "waiting for $URL "
for _ in $(seq 1 240); do
    if curl -sf -o /dev/null "$URL"; then echo " up"; break; fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
        echo; echo "Gradle exited early — see $LOG" >&2; tail -20 "$LOG" >&2; exit 1
    fi
    echo -n .; sleep 0.5
done

if [ -z "${NO_CHROME:-}" ]; then
    WINDOW_SIZE="${WINDOW_SIZE:-450,900}"
    CHROME_ARGS=(--new-window --window-size="$WINDOW_SIZE")
    if [ -n "${FRESH_PROFILE:-}" ]; then
        # Isolated profile => empty localStorage/IndexedDB, no effect on your
        # normal Chrome. Removed on exit.
        PROFILE_DIR="$(mktemp -d /tmp/ktoy-web-profile.XXXXXX)"
        CHROME_ARGS+=(--user-data-dir="$PROFILE_DIR" --no-first-run --no-default-browser-check)
        echo "fresh Chrome profile: $PROFILE_DIR"
    fi
    if [ -x "$CHROME" ]; then
        "$CHROME" "${CHROME_ARGS[@]}" "$URL" >/dev/null 2>&1 &
    elif command -v google-chrome >/dev/null 2>&1; then
        google-chrome "${CHROME_ARGS[@]}" "$URL" >/dev/null 2>&1 &
    else
        echo "Chrome not found — open manually: $URL" >&2
    fi
fi

wait "$GRADLE_PID"
