#!/usr/bin/env bash
set -euo pipefail

# Gradle connectedDebugAndroidTest kann die Ziel-App deinstallieren. Auf dem
# persönlichen Testgerät ausschließlich Updates und einen direkten Runner nutzen.
: "${ANDROID_SERIAL:?ANDROID_SERIAL auf das ausdrücklich gewählte Testgerät setzen}"
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_dir"

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb -s "$ANDROID_SERIAL" get-state
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

runner_args=(-w -r)
if [[ $# -gt 0 ]]; then
    runner_args+=(-e class "$1")
fi
test_log="$(mktemp -t pokopia-instrumentation.XXXXXX)"
trap 'rm -f "$test_log"' EXIT
adb -s "$ANDROID_SERIAL" shell am instrument "${runner_args[@]}" \
    de.stegmann.kameravorleser.test/androidx.test.runner.AndroidJUnitRunner | tee "$test_log"
if ! /usr/bin/grep -Eq '^OK \([1-9][0-9]* tests?\)' "$test_log"; then
    echo "Instrumentation meldet keinen erfolgreichen Testabschluss." >&2
    exit 1
fi
if /usr/bin/grep -q 'INSTRUMENTATION_STATUS_CODE: -3' "$test_log"; then
    echo "Hinweis: Der Runner hat Tests übersprungen; Statuscodes im Protokoll prüfen." >&2
fi
