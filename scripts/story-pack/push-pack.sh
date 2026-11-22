#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SERIAL:?ANDROID_SERIAL auf das ausdrücklich gewählte Testgerät setzen}"
if [[ $# -ne 1 ]]; then
    echo "Verwendung: ANDROID_SERIAL=<serial> $0 <lokaler-pack-ordner>" >&2
    exit 2
fi

pack_dir="$1"
if [[ ! -f "$pack_dir/index.jsonl" || ! -d "$pack_dir/audio" ]]; then
    echo "Ungültiger Story-Pack-Ordner (erwarte index.jsonl und audio/): $pack_dir" >&2
    exit 2
fi

adb -s "$ANDROID_SERIAL" get-state >/dev/null

# Nur files/story-pack aktualisieren; no_backup/Keystore/shared_prefs bleiben unberührt.
tar -C "$pack_dir" -cf - index.jsonl audio \
    | adb -s "$ANDROID_SERIAL" shell "run-as de.stegmann.kameravorleser sh -c 'rm -rf files/story-pack && mkdir -p files/story-pack && tar -xf - -C files/story-pack'"

adb -s "$ANDROID_SERIAL" shell "run-as de.stegmann.kameravorleser sh -c 'echo \"Geräteprüfung files/story-pack:\" && wc -l files/story-pack/index.jsonl && ls files/story-pack/audio | wc -l'"
