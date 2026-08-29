# Silent Alarm — Build Plan

Companion to [SPEC.md](SPEC.md). Phases are ordered so that the risky parts (OEM power management, Direct Boot) are proven on the real device before any UI polish is spent. Each phase ends in a state that can be installed and checked; nothing is "done" on a claim, only on a check that was actually run.

**Test-case ids (T1–T36) refer to SPEC.md §10.**

---

## 11. Build phases with acceptance checks

### Phase 0 — Skeleton and guardrails

Scope:
- Single-module Gradle project, Kotlin, `minSdk 33` / `targetSdk 35` / `compileSdk 35`, version catalog, Compose BOM + Material 3, Room, kotlinx-coroutines. No DI framework, no WorkManager, no navigation library (A13).
- Empty `MainActivity` hosting a Compose scaffold.
- Hand-written singleton graph (`AppGraph`) exposing the database, repository, and scheduler.
- A repo-level check script that fails on any audio API reference (`MediaPlayer|Ringtone|RingtoneManager|SoundPool|AudioManager|ToneGenerator|AudioFocus`) — the mechanical enforcement of G3/N3.

Acceptance:
- [ ] `./gradlew assembleDebug lint` passes; APK installs on the Redmi Note 11 Pro and launches to an empty screen.
- [ ] The audio-freeness check runs and **fails** when a `MediaPlayer` reference is deliberately added, then passes when removed. (Prove the guard works before trusting it — this is T24's mechanism.)
- [ ] No `INTERNET` permission in the merged manifest (`./gradlew :app:processDebugMainManifest` output inspected).

---

### Phase 1 — Data + scheduling core (no UI)

Scope:
- Room entity, DAO, and database per SPEC §9, built on `context.createDeviceProtectedStorageContext()` (A8).
- `NextOccurrence` pure function per SPEC §6, taking an injected `Clock` and `ZoneId` so it is testable.
- `AlarmScheduler`: `schedule(alarm)`, `cancel(id)`, `rescheduleAll()` using `setAlarmClock` with one `PendingIntent` per alarm id, `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` (A10).
- A temporary debug entry point (button or `adb` broadcast) to create a hard-coded alarm, since there is no UI yet.

Acceptance:
- [ ] T1–T9 pass as JVM unit tests against fixed clocks, including both DST cases (T6, T7) using a real DST zone.
- [ ] `adb shell dumpsys alarm | grep <package>` shows exactly one entry per enabled alarm, and zero entries after cancelling.
- [ ] Creating, editing, and deleting via the debug entry point leaves no orphan `PendingIntent` (verified in `dumpsys alarm`).
- [ ] The database file is under `/data/user_de/0/<package>/` — confirm with `adb shell run-as`. This is the check that Phase 4 depends on.

---

### Phase 2 — Fire + dismiss

Deliberately before the UI: this is where the platform fights back, and it is cheaper to find that out now.

Scope:
- `AlarmReceiver` (direct-boot aware) with the still-valid guard from SPEC §5.
- `AlarmService`, foreground, type `specialUse` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE = "alarm"` (A9); holds the firing set; owns the vibration.
- Notification channel `alarm_fire_v1` — importance HIGH, sound explicitly `null`, channel vibration off, category `alarm`, public lock-screen visibility. Never mutated after creation; a change means a new versioned channel id.
- Vibration: `VibratorManager.defaultVibrator`, `createWaveform(longArrayOf(0, 1000, 1000), amplitudes at max, repeat = 0)`, `VibrationAttributes.createForUsage(USAGE_ALARM)` (A7).
- `AlarmActivity`: `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, single Dismiss button, not back-dismissible.
- Dismiss command shared by the notification action and the button; auto-stop timer at 120 s (A6).
- `POST_NOTIFICATIONS` runtime request on first launch.

Acceptance:
- [ ] T10, T11 pass: fires with the screen off and with the app swiped from Recents.
- [ ] T12 passes under forced Doze.
- [ ] T21, T22, T23 pass — vibrates in silent and vibrate mode, and does not touch playing media (no ducking, no pause).
- [ ] T24 passes: the static audit is clean and the APK contains no audio assets.
- [ ] T28, T29, T30 pass — simultaneous alarms behave per SPEC §5 with no stuck service. Confirm the service is actually gone afterwards with `adb shell dumpsys activity services <package>`.
- [ ] T35 passes: auto-stop fires at 120 s, clears the notification, and reschedules.
- [ ] T36 passes: rotation during firing does not stop the vibration.

---

### Phase 3 — List and edit screens

Scope:
- Alarm list per SPEC §4.1 — rows, switch, tap-to-edit, long-press-to-delete with confirmation, empty state, FAB. (Setup banner is Phase 5.)
- Add/edit screen per SPEC §4.2 — Material 3 time picker, seven day toggles, Save gated on ≥ 1 day, Delete in edit mode.
- Repository writes go through a single path that also reschedules, so no UI action can persist an alarm without updating `AlarmManager`.

Acceptance:
- [ ] T31, T32, T33, T34 pass.
- [ ] Every list action is reflected in `dumpsys alarm` within a second: toggling off removes the entry, toggling on re-adds it with the right instant.
- [ ] Firing-time interactions from SPEC §5 behave correctly: disabling, deleting, and editing a *currently firing* alarm each stop the vibration and do the right thing about rescheduling.
- [ ] 12-hour and 24-hour system formats both render correctly in the list and picker.

---

### Phase 4 — Reboot, Direct Boot, time and timezone

Scope:
- `BootReceiver`, `directBootAware = true`, for `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`.
- `TimeChangeReceiver` for `ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED`.
- `rescheduleAll()` on app start.
- Confirm every component that touches the database before unlock (`AlarmReceiver`, `AlarmService`, `BootReceiver`) is marked `directBootAware`.

Acceptance:
- [ ] T15 passes (reboot then unlock).
- [ ] **T16 passes — reboot and never unlock.** This is the phase's real acceptance test: the alarm must vibrate at the lock screen of a freshly booted, never-unlocked phone, and the notification's Dismiss must work there. If this fails, Direct Boot is not actually working regardless of what Phase 1's storage check said.
- [ ] T17 passes (powered off across the alarm — no late fire, next occurrence scheduled).
- [ ] T18 passes (`adb install -r` keeps alarms alive).
- [ ] T25, T26, T27 pass (timezone change, clock forward, clock backward).
- [ ] Reschedule is idempotent: firing both boot broadcasts back to back leaves exactly one `PendingIntent` per alarm.

---

### Phase 5 — MIUI readiness and soak

Scope:
- Setup banner per SPEC §4.1: runtime checks for notification permission and `canUseFullScreenIntent()`, plus a manual checklist mirroring SPEC §8 with deep links where MIUI exposes an intent and plain instructions where it does not. Every deep link is wrapped so a missing activity falls back to the app's system settings page rather than crashing.
- Written setup instructions (SPEC §8) in the repo README.
- No new features. This phase is mostly device time.

Acceptance:
- [ ] Every SPEC §8 item has either a working deep link or explicit on-screen text; no dead buttons on the target device.
- [ ] Denying notifications, then granting them, makes the banner appear and disappear correctly.
- [ ] T19 and T20 pass — including T20 as a *documented* suppression, verified rather than assumed.
- [ ] T14 passes as a documented limitation (force-stop kills alarms; reopening restores them).
- [ ] **T13 passes: five consecutive nights, weekday alarm, phone unplugged, used normally during the day, fires within the same minute every time.** Any single miss resets the count and is root-caused before v1 is called done.

---

## 12. v1 is done when…

All of the following are true, each verified on the Redmi Note 11 Pro running MIUI 14 — not on an emulator, not on another device:

1. **Weekly alarms work end to end.** Create, edit, enable/disable, and delete from one list screen and one add/edit screen. Nothing else exists in the app.
2. **T1–T36 all pass**, with T14 and T20 passing as documented limitations rather than fixes.
3. **It fires with the screen off, with the app killed, and after a reboot — including a reboot where the phone is never unlocked** (T10, T11, T16).
4. **It has vibrated correctly on five consecutive nights** in the overnight soak (T13), on a phone that was used normally and left unplugged.
5. **It has never made a sound.** The static audio audit is clean, the APK ships no audio assets, the notification channel's sound is explicitly null, and playing media is never interrupted (T23, T24).
6. **Dismiss works from both places** — notification action and on-screen button — and always leaves the alarm scheduled for its next weekday (T28, T31).
7. **The MIUI checklist is complete and honest**: every item in SPEC §8 is either deep-linked or spelled out, and the app tells the user when a check it *can* see is failing.
8. **The permission set is exactly SPEC §7** — nothing extra, and no `INTERNET` permission.
9. **The persisted model is exactly SPEC §9's five fields.** Any extra column means scope crept.
10. **Assumptions A1–A14 are still accurate**, or the ones that changed have been rewritten in SPEC.md to match what was actually built.

Anything not on this list is v2.
