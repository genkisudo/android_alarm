package com.silentalarm.app.setup

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The setup banner's readiness state (SPEC.md #4.1): the two things the app can actually
 * detect, plus whether the user has acknowledged the manual MIUI checklist it cannot detect.
 */
data class SetupState(
    val notificationsGranted: Boolean,
    val fullScreenIntentAllowed: Boolean,
    val miuiChecklistAcknowledged: Boolean,
) {
    val needsAttention: Boolean
        get() = !notificationsGranted || !fullScreenIntentAllowed || !miuiChecklistAcknowledged
}

object SetupChecks {
    fun evaluate(context: Context): SetupState {
        val notificationsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        // NotificationManager.canUseFullScreenIntent() only exists from API 34; below that the
        // permission is auto-granted for apps holding USE_FULL_SCREEN_INTENT (SPEC.md #7).
        val fullScreenIntentAllowed = if (Build.VERSION.SDK_INT >= 34) {
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            true
        }

        return SetupState(
            notificationsGranted = notificationsGranted,
            fullScreenIntentAllowed = fullScreenIntentAllowed,
            miuiChecklistAcknowledged = SetupPrefs(context).isMiuiChecklistAcknowledged(),
        )
    }
}
