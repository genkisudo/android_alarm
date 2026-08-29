#!/usr/bin/env bash
# Mechanical enforcement of SPEC.md Non-goal N3 / Goal G3: this app must never make a sound.
# Fails the build if any audio-playback API is referenced anywhere in app source, and fails if
# any audio asset is bundled. See BUILD_PLAN.md Phase 0 acceptance check and SPEC.md test T24.
set -euo pipefail

cd "$(dirname "$0")/.."

SRC_DIRS=("app/src")
FORBIDDEN_PATTERN='MediaPlayer|RingtoneManager|\bRingtone\b|SoundPool|AudioManager|AudioFocus|ToneGenerator'

found=0

for dir in "${SRC_DIRS[@]}"; do
    [ -d "$dir" ] || continue
    if matches=$(grep -RInE "$FORBIDDEN_PATTERN" "$dir" --include="*.kt" --include="*.java" --include="*.xml" 2>/dev/null); then
        echo "FORBIDDEN AUDIO API REFERENCE(S) FOUND:"
        echo "$matches"
        found=1
    fi
done

for dir in "${SRC_DIRS[@]}"; do
    [ -d "$dir" ] || continue
    if assets=$(find "$dir" -type f \( -iname "*.mp3" -o -iname "*.ogg" -o -iname "*.wav" -o -iname "*.m4a" -o -iname "*.flac" \) 2>/dev/null); then
        if [ -n "$assets" ]; then
            echo "FORBIDDEN AUDIO ASSET(S) FOUND:"
            echo "$assets"
            found=1
        fi
    fi
done

if [ "$found" -ne 0 ]; then
    echo "This app is vibration-only. Remove the above before committing." >&2
    exit 1
fi

echo "OK: no audio APIs or audio assets found in app source."
