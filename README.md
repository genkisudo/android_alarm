# Silent Alarm

A vibration-only weekly alarm clock for Android. Native Kotlin.

Target device: Redmi Note 11 Pro, Android 13, MIUI 14.

**Status: all six build-plan phases implemented. Not yet compiled or run on a device.**

- [docs/SPEC.md](docs/SPEC.md) — product, goals/non-goals, assumptions, screens, fire/dismiss behavior, weekly/timezone/reboot rules, permissions, MIUI setup, data model, test cases, open questions.
- [docs/BUILD_PLAN.md](docs/BUILD_PLAN.md) — six build phases with acceptance checks, and the v1 done criteria.

## What it does

A list of weekly alarms. Each alarm is a time plus one or more weekdays. When one fires the phone vibrates — never a sound — until dismissed from the notification or the full-screen alarm screen. Dismissing leaves the alarm scheduled for its next weekday.

No snooze, labels, sound, themes, accounts, or cloud.

## Building

Two Gradle modules:

- `scheduling/` — plain Kotlin/JVM, no Android dependency. `./gradlew :scheduling:test` runs the next-occurrence unit tests (SPEC.md test cases T1–T8).
- `app/` — the Android app (minSdk 33, target/compileSdk 35). `./gradlew :app:assembleDebug` builds the APK; `./gradlew :app:lint` runs Android Lint. Needs a real Android SDK — see CLAUDE.md's "Current state" for a sandbox that lacks one.

`scripts/check_no_audio_apis.sh` fails the build if any audio-playback API or audio asset is added to `app/` — this app is vibration-only, permanently.

None of the on-device test cases (T10–T36 in SPEC.md §10) have been run yet; they need the target device or an emulator.
