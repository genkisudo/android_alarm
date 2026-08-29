package com.silentalarm.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.silentalarm.app.SilentAlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules every alarm after a reboot or app update (SPEC.md #6 reschedule-trigger list).
 *
 * directBootAware and listening for LOCKED_BOOT_COMPLETED (not just BOOT_COMPLETED) is what
 * makes rescheduling run *before* the user unlocks the phone after a reboot - the whole point
 * of Assumption A8 (device-protected storage) and test T16. Idempotent: re-creating each
 * alarm's PendingIntent by id is safe even if both boot broadcasts arrive.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> rescheduleAll(context)
        }
    }

    private fun rescheduleAll(context: Context) {
        val pendingResult = goAsync()
        val graph = (context.applicationContext as SilentAlarmApp).graph
        CoroutineScope(Dispatchers.IO).launch {
            try {
                graph.repository.rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
