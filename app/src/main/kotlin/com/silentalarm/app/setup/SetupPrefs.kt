package com.silentalarm.app.setup

import android.content.Context

/** Stores only whether the user has acknowledged the MIUI checklist (SPEC.md #8) - nothing else. */
class SetupPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("setup_prefs", Context.MODE_PRIVATE)

    fun isMiuiChecklistAcknowledged(): Boolean = prefs.getBoolean(KEY_ACK, false)

    fun setMiuiChecklistAcknowledged(acknowledged: Boolean) {
        prefs.edit().putBoolean(KEY_ACK, acknowledged).apply()
    }

    private companion object {
        const val KEY_ACK = "miui_checklist_acknowledged"
    }
}
