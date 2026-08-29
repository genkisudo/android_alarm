# Silent Alarm

A vibration-only weekly alarm clock for Android. Native Kotlin.

Target device: Redmi Note 15, Android 15, HyperOS 2.

**Status: specification only. No code yet.**

- [docs/SPEC.md](docs/SPEC.md) — product, goals/non-goals, assumptions, screens, fire/dismiss behavior, weekly/timezone/reboot rules, permissions, HyperOS setup, data model, test cases, open questions.
- [docs/BUILD_PLAN.md](docs/BUILD_PLAN.md) — six build phases with acceptance checks, and the v1 done criteria.

## What it does

A list of weekly alarms. Each alarm is a time plus one or more weekdays. When one fires the phone vibrates — never a sound — until dismissed from the notification or the full-screen alarm screen. Dismissing leaves the alarm scheduled for its next weekday.

No snooze, labels, sound, themes, accounts, or cloud.
