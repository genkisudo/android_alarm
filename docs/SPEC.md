# Silent Alarm — Specification

Target device: Redmi Note 15, Android 15 (API 35), HyperOS 2.
Stack: native Kotlin, single app module, Jetpack Compose + Room. No Flutter, no React Native.

---

## 1. Product in 5 lines

1. A weekly alarm clock that never makes a sound — it only vibrates.
2. You keep a list of alarms; each one is a time plus the weekdays it repeats on.
3. Alarms fire with the screen off, with the app killed, and after a reboot.
4. When one fires, the phone vibrates until you dismiss it from the notification or the full-screen alarm screen.
5. Dismissing stops the vibration and leaves the alarm scheduled for its next weekday.

---

## 2. Goals / non-goals

### Goals
- G1. Weekly repeating alarms: time + one or more weekdays.
- G2. Reliable firing with screen off, app swiped away / process killed, and across reboots.
- G3. Vibration-only output. No audio path exists anywhere in the app.
- G4. Dismiss from the notification action **or** an on-screen button; both do the same thing.
- G5. Enable / disable / delete alarms from one list.
- G6. Survive Xiaomi/HyperOS power management, with in-app guidance to the settings the user must change by hand.
- G7. Exactly two user-authored screens: list and add/edit. (The alarm-firing screen is system-triggered output, not a third place the user navigates to.)

### Non-goals
- N1. Snooze.
- N2. Labels, names, notes, or icons per alarm.
- N3. Any sound, ringtone, TTS, or media playback — including "silent" audio tracks used as keep-alive hacks.
- N4. One-off (non-repeating) alarms, date-based alarms, holidays, skip-next.
- N5. Themes, settings screen, accounts, sync, cloud, backup/restore, analytics, crash reporting.
- N6. Widgets, tiles, watch companion, Assistant/voice integration.
- N7. Gradual/escalating vibration, custom vibration patterns per alarm.
- N8. Play Store distribution, localization beyond the default locale, tablet/foldable layouts.
- N9. Broad device compatibility. Correctness is defined on the target device; other devices are best-effort.

---

## 3. Assumptions

Recorded so the smallest design can be justified. Each one is cheap to reverse later.

- A1. **minSdk 33, targetSdk 35.** At API 33+ `USE_EXACT_ALARM` is an install-time permission for alarm-clock apps, so there is no runtime exact-alarm prompt and no `SCHEDULE_EXACT_ALARM` settings dance. `java.time`, `VibratorManager`, and `VibrationAttributes` are all available with no compat branches. Cost: no support below Android 13.
- A2. **Personal/sideloaded build**, not Play-distributed. This allows `FOREGROUND_SERVICE_SPECIAL_USE` without a Play Console justification. If it ever ships on Play, either file the special-use declaration or switch to `shortService` (see A9).
- A3. **Single user, single device, no multi-user/work-profile handling.**
- A4. **Alarms are wall-clock local.** "07:00 Monday" means 07:00 in whatever timezone the phone is in at that moment. Alarms are not anchored to a fixed zone.
- A5. **No catch-up for missed alarms.** If the phone was off/unreachable at fire time, that occurrence is lost; the next occurrence is scheduled. See §6.
- A6. **Vibration auto-stops after 120 seconds** if nobody dismisses. The occurrence is then treated as dismissed and the next one is scheduled. Prevents an indefinite vibration draining the battery in a bag.
- A7. **Fixed vibration pattern for all alarms:** 1000 ms on / 1000 ms off, repeating, maximum amplitude, `VibrationAttributes.USAGE_ALARM`. Not configurable.
- A8. **The alarm database lives in device-protected storage.** It holds only times and weekday bits — nothing sensitive — so putting it in DE storage lets the scheduler run before the first unlock after a reboot (Direct Boot). This is what makes "fires after reboot" true even if the user never unlocks.
- A9. **Firing is held by a foreground service** of type `specialUse` (subtype `alarm`), started from the alarm broadcast. Exact alarms grant a temporary exemption from background foreground-service-start restrictions, which is why this is legal on Android 15.
- A10. **Only the next occurrence of each alarm is scheduled** in `AlarmManager` at any time — one `PendingIntent` per alarm, keyed by alarm id. Rescheduling happens on dismiss, on edit, on boot, and on time/timezone change.
- A11. **At least one weekday is required.** An alarm with zero days cannot be saved.
- A12. **Duplicate alarms are allowed.** Two alarms at the same time on the same day are a valid (if pointless) state; §5 defines what happens when they fire together.
- A13. **No dependency injection framework, no WorkManager, no navigation library.** A hand-written singleton graph and a single Activity hosting two composables is enough at this size.
- A14. **Force-stop is accepted as fatal.** If the user force-stops the app from system settings, Android cancels all its alarms and no broadcast (including boot) is delivered until the app is launched again. Nothing in-app can prevent this; it is documented, not engineered around.

---

## 4. Screens and controls

### 4.1 Alarm list (start destination)

- **Setup banner** (top, conditional): shown only when a readiness check fails — notifications denied, full-screen intent not permitted, or the user has not yet acknowledged the HyperOS checklist. Tapping it opens the relevant system settings screen (or the in-banner checklist for the HyperOS items). Dismissible with "Done"; re-appears if a system-checkable condition regresses. This is a card inside the list screen, not a screen.
- **Alarm rows**, sorted by time of day ascending, then by id:
  - Time, large, in the user's 12/24-hour system format.
  - Weekday summary line: `Mon Tue Wed Thu Fri` style, or `Every day` / `Weekdays` / `Weekends` when the set matches.
  - Trailing **switch** — enable/disable. Takes effect immediately; no save step.
  - Row content is dimmed when disabled.
  - **Tap row** → add/edit screen for that alarm.
  - **Long-press row** → confirmation dialog → **Delete**.
- **FAB "+"** → add/edit screen in "new" mode.
- **Empty state**: one line of text plus the FAB.

No search, no sort control, no multi-select, no overflow menu.

### 4.2 Add / edit alarm

- **Time picker** (Material 3 `TimePicker`, dial or input, honouring system 12/24h).
- **Seven day toggles** in a single row, `M T W T F S S`, starting Monday. Toggle = selected/unselected.
- **Save** — enabled only when ≥ 1 day is selected. Persists, reschedules, returns to list.
- **Cancel / back** — discards changes.
- **Delete** — visible only in edit mode; confirms, then deletes and returns.

New-alarm defaults: current time rounded up to the next 5 minutes, no days selected, enabled = true.

### 4.3 Alarm firing screen (system-triggered)

Launched by the firing notification's full-screen intent; shows over the lock screen, turns the screen on.

- Time of the alarm that is firing, large.
- One control: **Dismiss** (large button, full-width, requires a deliberate tap — no swipe, no accidental edge gesture).
- No snooze, no back-button dismissal, not swipe-dismissible.

### 4.4 Firing notification

- Channel `alarm_fire_v1`, importance HIGH, **sound explicitly null**, channel vibration disabled (the service owns vibration), category `alarm`, lock-screen visibility public.
- Ongoing (not swipe-dismissible), with a full-screen intent to §4.3.
- Single action: **Dismiss**.

---

## 5. Alarm fire + dismiss behavior

### Fire path
1. `AlarmManager.setAlarmClock(...)` fires a broadcast `PendingIntent` carrying the alarm id.
2. `AlarmReceiver` (direct-boot aware) does a `goAsync()` guarded check: the alarm still exists, is still enabled, and today is still in its day mask. If any check fails, it reschedules and stops — nothing fires.
3. The receiver starts `AlarmService` as a foreground service (type `specialUse`) with the alarm id.
4. `AlarmService` immediately posts the firing notification (§4.4) and starts the vibration waveform with `VibrationAttributes.USAGE_ALARM`, repeating from index 0.
5. The full-screen intent brings up §4.3 when the screen is off or locked; when the phone is unlocked and in use, the system shows it as a heads-up notification instead. Both carry the same Dismiss.
6. Nothing is played through the audio subsystem at any point. The app links no `MediaPlayer`, `Ringtone`, `RingtoneManager`, `SoundPool`, or `AudioManager` API.

### Dismiss path
Triggered identically by the notification action and the on-screen button (both route to the same service command):
1. Cancel vibration (`Vibrator.cancel()`).
2. Remove the alarm from the service's active set.
3. Compute and schedule that alarm's **next** occurrence (§6). The alarm stays enabled.
4. If the active set is now empty: stop the foreground service and remove the notification; finish the firing activity if it is showing.

### Multiple alarms firing at once
The service keeps a set of currently-firing alarm ids. A second alarm firing while the first is active joins the set; the vibration is **one** waveform regardless of how many are in the set (two `Vibrator` calls would just replace each other anyway). The single **Dismiss** clears the whole set and reschedules every alarm in it. This is the smallest behavior that never leaves a stuck alarm.

### Auto-stop
After 120 s (A6) with no dismiss, the service performs the dismiss path itself for every active alarm.

### Interactions while an alarm is firing
- **Disable the firing alarm** from the list → treated as a dismiss for that alarm; it is not rescheduled while disabled.
- **Delete the firing alarm** → treated as a dismiss; no reschedule.
- **Edit the firing alarm** → treated as a dismiss, then the new definition is scheduled.
- **Reboot while firing** → vibration is gone; the occurrence is lost; the next occurrence is scheduled on boot.

---

## 6. Weekly / timezone / missed-alarm / reboot rules

### Next-occurrence rule
Given an enabled alarm with `hour`, `minute`, non-zero `daysMask`, and `now` in the system zone:

```
for offset in 0..7:
    date = now.toLocalDate().plusDays(offset)
    if date.dayOfWeek not in daysMask: continue
    candidate = LocalDateTime(date, LocalTime(hour, minute)).atZone(systemZone)
    if candidate > now: return candidate
```

The 0..7 window (eight days) guarantees a hit for any non-zero mask even when a DST shift moves a candidate across `now`. Comparison is strictly greater than `now`, so an alarm whose time is exactly "now" goes to next week rather than firing twice.

### DST
`LocalDateTime.atZone()` resolution is the rule, unchanged:
- **Spring forward** (the local time does not exist): the alarm fires at the shifted-forward instant, i.e. immediately after the gap. It fires once.
- **Fall back** (the local time occurs twice): the **earlier** offset wins. It fires once.

### Timezone change
Alarms are wall-clock local (A4). On `ACTION_TIMEZONE_CHANGED` every enabled alarm is cancelled and re-scheduled against the new zone, so a 07:00 alarm stays 07:00 after landing.

### Time change / manual clock set
`ACTION_TIME_CHANGED` does the same full reschedule. Moving the clock backwards past a just-fired alarm does **not** re-fire it — the alarm is rescheduled to the next strictly-future occurrence, which may be later the same day.

### Missed alarms
No catch-up (A5). Any occurrence whose instant is already in the past when the scheduler runs is skipped; the next strictly-future occurrence is scheduled. Reasons an occurrence can be missed: the phone was powered off, the app was force-stopped, or the user revoked a required permission. No "you missed an alarm" notification is shown.

### Reboot
- `BootReceiver` is `directBootAware` and listens for **`LOCKED_BOOT_COMPLETED`** and **`BOOT_COMPLETED`**, plus `MY_PACKAGE_REPLACED` (app update).
- Because the database is in device-protected storage (A8), the reschedule runs at `LOCKED_BOOT_COMPLETED` — before the first unlock. A reboot at 03:00 does not break a 07:00 alarm even if nobody touches the phone.
- Pre-unlock, the firing service, receiver, and notification all work; the full-screen activity may be held back by the keyguard until unlock. Vibration and the notification Dismiss action still work, which is enough.
- The reschedule is idempotent: it cancels each alarm's `PendingIntent` by id and re-creates it, so double delivery of boot broadcasts is harmless.

### Reschedule triggers (the complete list)
App start · alarm created / edited / deleted / toggled · dismiss (including auto-stop) · `LOCKED_BOOT_COMPLETED` · `BOOT_COMPLETED` · `MY_PACKAGE_REPLACED` · `ACTION_TIME_CHANGED` · `ACTION_TIMEZONE_CHANGED`.

---

## 7. Permissions and why

| Permission | Type | Why |
|---|---|---|
| `VIBRATE` | install-time | The entire output of the app. |
| `USE_EXACT_ALARM` | install-time (API 33+) | Lets `setAlarmClock` schedule to the minute with no user prompt. Legitimate here because the app's core function *is* an alarm clock. This is what makes alarms exempt from Doze deferral. |
| `RECEIVE_BOOT_COMPLETED` | install-time | Reschedule after a reboot. `AlarmManager` state does not survive one. |
| `POST_NOTIFICATIONS` | **runtime (API 33+)** | The firing notification and its Dismiss action. Requested on first launch; if denied, the setup banner (§4.1) explains that the alarm can still vibrate but there will be no notification-based dismiss, and routes to settings. |
| `FOREGROUND_SERVICE` | install-time | Hold the vibration while the app is otherwise in the background. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | install-time (API 34+) | Required type declaration for the alarm-holding service, with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE = "alarm"`. |
| `USE_FULL_SCREEN_INTENT` | install-time, auto-granted for alarm apps on API 34+ | Show the firing screen over the lock screen with the screen off. Verified at runtime via `NotificationManager.canUseFullScreenIntent()`; if false, the setup banner routes to `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`. |

**Deliberately not requested:**
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — `setAlarmClock` is already Doze-exempt, and the restriction that actually matters on this device is Xiaomi's own, which no manifest permission unlocks. The setup flow sends the user to the system list instead (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`).
- `SYSTEM_ALERT_WINDOW` — not needed; the full-screen intent covers showing the alarm screen. (The MIUI "pop-up windows in background" toggle in §8 is a HyperOS setting, not this permission.)
- `WAKE_LOCK` — the foreground service plus `FLAG_TURN_SCREEN_ON` / `setShowWhenLocked` on the firing activity are sufficient; no manual wake lock.
- `SCHEDULE_EXACT_ALARM` — superseded by `USE_EXACT_ALARM` at minSdk 33.
- Internet, storage, location, contacts — nothing in the app touches them. The app declares **no** `INTERNET` permission, which is also the cheapest possible proof that there is no cloud.

---

## 8. Xiaomi / HyperOS 2 setup the user must do

These are OEM restrictions that no app-side code can override. The setup banner (§4.1) walks through them as a checklist with deep links where HyperOS exposes one; the rest are manual. Paths are for HyperOS 2 on a Redmi Note 15 and may be worded slightly differently after an OTA.

1. **Autostart — ON.**
   `Settings → Apps → Manage apps → Silent Alarm → Autostart`.
   Without this, HyperOS may drop `BOOT_COMPLETED` and background broadcasts. This is the single most common reason alarms stop firing on Xiaomi.
2. **Battery saver → "No restrictions".**
   `Settings → Apps → Manage apps → Silent Alarm → Battery saver → No restrictions`.
   Not "Battery saver", not "Restricted".
3. **Lock the app in Recents.**
   Open Recents, swipe down on the Silent Alarm card (or long-press) → padlock.
   Stops HyperOS memory cleanup from killing the process on a "boost".
4. **Other permissions → "Display pop-up windows while running in background" — ON.**
   `Settings → Apps → Manage apps → Silent Alarm → Other permissions`.
   This is what lets the full-screen alarm activity appear from the background on MIUI/HyperOS.
5. **Other permissions → "Show on Lock screen" — ON.**
   So the alarm screen can appear over the keyguard.
6. **Notifications — allowed, floating enabled, shown on lock screen.**
   `Settings → Notifications → App notifications → Silent Alarm` → allow, then open the *Alarm* channel and enable "Floating notifications" and "Lock screen notifications".
7. **Do Not Disturb must allow alarms.**
   `Settings → Sound & vibration → Do Not Disturb → Exceptions/Allow → Alarms — ON`.
   HyperOS allows alarms by default; verify, because the vibration is classified as `USAGE_ALARM` and is filtered by exactly this switch. Optionally add Silent Alarm to the DND app exceptions so the notification is not hidden.
8. **Vibration is actually enabled.**
   `Settings → Sound & vibration` → confirm "Vibrate in silent mode" is on and haptic/vibration intensity is not at zero. On a silent-mode-only phone, alarm-usage vibration is what carries the whole product.
9. **Automatic date, time and timezone — ON.**
   `Settings → Additional settings → Date & time`. Manual clock drift will move alarms.
10. **Do not enable Ultra battery saver overnight.**
    HyperOS's ultra/extreme saver suspends third-party apps wholesale and will drop alarms. Standard "Battery saver" is fine given step 2.
11. **Never "Force stop" the app** from app info, and do not add it to any cleaner's kill list. Force-stop cancels all scheduled alarms until the app is opened again (A14).
12. *(Last resort only, if alarms still miss after all of the above.)* Disabling "MIUI optimization" in Developer options is a known workaround for aggressive process management, but it changes system-wide behavior and should be treated as a diagnostic step, not part of normal setup.

---

## 9. Data fields

### Entity `alarms` (Room, single table, device-protected storage)

| Field | Type | Rules |
|---|---|---|
| `id` | `Long`, primary key, autogenerate | Also used as the `AlarmManager` request code and as the intent extra when firing. Stable for the life of the alarm. |
| `hour` | `Int` | 0–23. Local wall-clock hour. |
| `minute` | `Int` | 0–59. |
| `daysMask` | `Int` | Bitmask, bit 0 = Monday … bit 6 = Sunday. Valid range 1–127; 0 is rejected at save (A11). |
| `enabled` | `Boolean` | Default true. A disabled alarm has no scheduled `PendingIntent`. |

That is the whole persisted model. Deliberately absent: label, sound uri, snooze state, vibration pattern, created/updated timestamps, next-trigger cache (always derived from §6 so it can never go stale), and any notion of a one-off alarm.

### Derived / transient (not persisted)
- `nextTriggerAt: ZonedDateTime?` — computed on demand for display and scheduling.
- Firing set — the ids currently vibrating, held in `AlarmService` only. Lost on process death, which is correct: if the process dies, nothing is vibrating.

### Schema versioning
Version 1, no migrations expected. If the schema ever changes, `fallbackToDestructiveMigration` is acceptable — losing a handful of alarm rows is not a data-loss event worth writing migrations for at this size. (Recorded as a deliberate choice, not an oversight.)

---

## 10. Test cases

Every case below is a manual on-device test on the target Redmi Note 15 unless marked *(unit)*. Automated coverage is limited to the next-occurrence function, which is where the real logic lives.

### Scheduling logic *(unit tests, fixed clock + fixed zone)*
| # | Case | Expected |
|---|---|---|
| T1 | Alarm Mon 07:00, now Mon 06:59 | Fires today 07:00 |
| T2 | Alarm Mon 07:00, now Mon 07:00:00 exactly | Next Monday, not today |
| T3 | Alarm Mon 07:00, now Mon 07:01 | Next Monday |
| T4 | Every-day alarm, now 23:59 Sunday | Tomorrow (Monday) |
| T5 | Weekend-only alarm, now Wednesday | Saturday |
| T6 | Alarm at 02:30 on a spring-forward night (02:00→03:00) | Fires once, at the shifted instant after the gap |
| T7 | Alarm at 02:30 on a fall-back night (02:00 repeats) | Fires once, at the earlier offset |
| T8 | `daysMask = 0` | Rejected at save; never schedulable |
| T9 | Disabled alarm | Produces no trigger |

### Background / process death
| # | Case | Steps | Expected |
|---|---|---|---|
| T10 | Screen off, app in background | Set alarm +2 min, lock the phone, put it down | Vibrates on time; full-screen alarm screen appears over the lock screen; Dismiss stops it |
| T11 | App killed (swiped from Recents) | Set alarm +2 min, swipe app away, lock | Fires on time and vibrates |
| T12 | Deep Doze | Set alarm +30 min, `adb shell dumpsys deviceidle force-idle`, screen off, untouched | Fires on time (exact-alarm-clock exemption) |
| T13 | Overnight soak | Set a 07:00 weekday alarm, use the phone normally, leave it overnight unplugged | Fires within the same minute, 5 nights running |
| T14 | Force-stop | Force-stop from app info, wait for a scheduled alarm | Does **not** fire — documented limitation (A14); reopening the app restores all schedules |

### Reboot
| # | Case | Steps | Expected |
|---|---|---|---|
| T15 | Reboot then unlock | Set alarm +5 min, reboot, unlock, wait | Fires on time |
| T16 | Reboot, **never unlock** | Set alarm +5 min, reboot, leave at the lock screen | Fires on time and vibrates (Direct Boot path, A8); notification Dismiss works from the lock screen |
| T17 | Powered off across the alarm | Alarm at T, power off before T, power on 10 min after T | Does not fire late; next week's occurrence is scheduled (A5) |
| T18 | App update | Set alarm, `adb install -r` a new build | Alarm still fires (`MY_PACKAGE_REPLACED` reschedule) |

### DND / silent / sound
| # | Case | Expected |
|---|---|---|
| T19 | DND on, "Allow alarms" on | Vibrates normally |
| T20 | DND on, "Allow alarms" **off** | Vibration is suppressed by the OS; setup checklist item 7 covers it — verified as a *known and documented* outcome, not a bug |
| T21 | Ringer in Silent mode | Vibrates (alarm usage) |
| T22 | Ringer in Vibrate mode | Vibrates |
| T23 | Volume at 0, media playing | Vibrates; media playback is not interrupted, ducked, or paused — proof that no audio focus is requested |
| T24 | Audio-freeness audit *(static)* | Source contains no reference to `MediaPlayer`, `Ringtone`, `RingtoneManager`, `SoundPool`, `AudioManager`, `AudioFocus`, `ToneGenerator`; the notification channel's sound is explicitly `null`; the APK contains no audio assets |

### Timezone / clock
| # | Case | Steps | Expected |
|---|---|---|---|
| T25 | Timezone change | 07:00 weekday alarm, switch phone from UTC+3 to UTC+1 | Still fires at 07:00 local in the new zone |
| T26 | Clock moved forward past an alarm | Alarm at 09:00, manually set clock to 09:30 | Alarm does not fire retroactively; next occurrence scheduled |
| T27 | Clock moved backward before a just-fired alarm | Fire an alarm at 09:00, dismiss, set clock back to 08:55 | Fires again at 09:00 (correct — it is a future occurrence now) and does not double-fire |

### Two alarms at the same time
| # | Case | Expected |
|---|---|---|
| T28 | Two enabled alarms, same time, same day | Both fire; one notification; one vibration waveform; a single Dismiss stops it and reschedules **both** for next week; no orphan notification or stuck service remains |
| T29 | Second alarm fires while the first is still vibrating (1 min apart, first not dismissed) | Vibration continues uninterrupted; Dismiss clears both |
| T30 | Delete one of two simultaneously-firing alarms mid-vibration | Vibration continues for the other; deleted alarm is not rescheduled |

### List / edit behavior
| # | Case | Expected |
|---|---|---|
| T31 | Toggle an alarm off, then on | Off cancels the pending alarm; on reschedules to the correct next occurrence |
| T32 | Edit a scheduled alarm's time | Old `PendingIntent` cancelled, new one scheduled; only one fires |
| T33 | Delete an alarm | Row gone, `PendingIntent` cancelled, nothing fires at the old time |
| T34 | Save with no days selected | Save is disabled |
| T35 | Auto-stop | Let an alarm vibrate without dismissing | Stops at 120 s, notification cleared, next occurrence scheduled (A6) |
| T36 | Rotation / process death during firing | Rotate the phone while the alarm screen is up | Vibration continues, Dismiss still works |

---

## Open questions

Only the ones that change behavior. Each has a default in place, so none of them blocks the build.

- **Q1. Late firing after a reboot.** Current rule is no catch-up (A5): an alarm missed while the phone was off is simply lost. Alternative: fire immediately on boot if the missed occurrence is within a grace window (e.g. 15 minutes). This changes what happens after an overnight OTA reboot. *Default if unanswered: no catch-up.*
- **Q2. Simultaneous alarms and a single Dismiss.** Current rule is one Dismiss clears every alarm currently vibrating (§5). Alternative: one notification and one Dismiss per alarm. *Default if unanswered: single Dismiss clears all.*
- **Q3. Auto-stop duration.** 120 s (A6) is a guess balancing "long enough to wake you" against "not draining the battery in a bag". A heavy sleeper may want 5 minutes; someone who dismisses instantly may want 30 s. *Default if unanswered: 120 s.*
