package com.silentalarm.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.silentalarm.app.SilentAlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules every alarm on a timezone change or a manual/automatic clock change (SPEC.md #6:
 * alarms are wall-clock local, so a 07:00 alarm must still fire at 07:00 after either kind of
 * change - tests T25-T27).
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
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
