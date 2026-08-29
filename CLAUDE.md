# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

This repository contains **specification and build-plan documents only — no application code yet**. There is no Gradle project, no build/lint/test commands to run. Before writing any code, read:

- `docs/SPEC.md` — the full product spec: goals/non-goals, assumptions, screens, fire/dismiss behavior, weekly/timezone/reboot rules, permissions, MIUI setup steps, data model, and 36 numbered test cases (T1–T36).
- `docs/BUILD_PLAN.md` — six build phases (0–5) with acceptance checks tied to those test cases, and the v1 "done" criteria.

When code is added, this file should be updated with real build/lint/test commands and any architecture that emerges from actual implementation choices — don't invent commands ahead of that.

## What the app is

Silent Alarm: a vibration-only weekly alarm clock for Android, native Kotlin (no Flutter/React Native). Target device is a Redmi Note 11 Pro on Android 13 / MIUI 14 — correctness is defined against that device, not broad compatibility.

A list of weekly alarms (time + weekdays). When one fires the phone vibrates — never a sound — until dismissed from the notification action or the on-screen button. Dismissing stops the vibration and leaves the alarm scheduled for its next occurrence. No snooze, labels, sound, themes, accounts, or cloud.

## Non-negotiable constraints (from SPEC.md)

These are the decisions that make the "must fire" requirements true and should not be casually revisited:

- **No audio APIs, ever.** No `MediaPlayer`, `Ringtone`, `RingtoneManager`, `SoundPool`, `AudioManager`, `AudioFocus`, or `ToneGenerator` anywhere in the source, no audio assets in the app, and the firing notification channel's sound is explicitly `null`. The build plan (Phase 0) calls for a repo-level grep check enforcing this mechanically, not just by convention.
- **No `INTERNET` permission.** The permission set is exactly the list in SPEC.md §7 — nothing added for convenience (no WorkManager, no analytics/crash reporting, no DI framework, no navigation library — see SPEC.md Assumption A13).
- **minSdk 33 / targetSdk 35**, using `USE_EXACT_ALARM` (install-time, no runtime prompt at this API level) rather than `SCHEDULE_EXACT_ALARM`.
- **The alarm database must live in device-protected storage** (`createDeviceProtectedStorageContext()`), and every component that touches it before first unlock (`AlarmReceiver`, `AlarmService`, `BootReceiver`) must be `directBootAware`. This is what makes alarms survive a reboot the user never unlocks after (SPEC.md Assumption A8, test T16) — it is the single most important correctness property in the app and the gating acceptance check for Build Plan Phase 4.
- **Only two user-facing screens**: the alarm list and the add/edit screen. The full-screen alarm-firing screen is system-triggered output, not a third navigable destination.
- **Data model is exactly five fields** (`id`, `hour`, `minute`, `daysMask`, `enabled`) — see SPEC.md §9. No labels, sound URIs, snooze state, or cached next-trigger time.
- **Only the next occurrence of each alarm is scheduled** in `AlarmManager` at a time (one `PendingIntent` per alarm id); rescheduling happens on every mutation, on dismiss, on boot, and on time/timezone change (SPEC.md §6, the full reschedule-trigger list).

## Build plan phase order

Build Plan phases are sequenced deliberately so platform risk is retired before UI polish: skeleton (0) → data/scheduling core with no UI (1) → fire/dismiss (2) → list/edit screens (3) → reboot/Direct Boot/timezone (4) → MIUI readiness + multi-night soak test (5). Don't reorder this — e.g. don't build the UI before the alarm actually fires reliably in the background, since that's where the real risk is.

## MIUI/Xiaomi specifics

The target device has aggressive OEM power management that no in-app code can fully override (autostart, battery restrictions, background pop-up permission, lock-screen display, DND alarm exception). SPEC.md §8 lists the exact settings paths the user must configure by hand; the app's job is to detect what it can (notification permission, full-screen-intent permission) and guide the user to the rest via a setup checklist banner on the list screen — not to attempt to bypass OEM restrictions programmatically.
