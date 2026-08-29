package com.silentalarm.app.fire

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fire path entry point (SPEC.md #5, step 2). Stub for now - the goAsync() guarded check
 * (alarm still exists / still enabled / today still in its day mask) and starting AlarmService
 * are added in the Phase 2 commit ("Fire + dismiss"). Declared directBootAware in the manifest
 * because it must run before the first unlock after a reboot (SPEC.md Assumption A8).
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // TODO(Phase 2): guarded check, then start AlarmService with the alarm id.
    }
}
