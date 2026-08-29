package com.silentalarm.app.fire

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.silentalarm.app.SilentAlarmApp
import com.silentalarm.app.scheduling.AlarmScheduler
import com.silentalarm.scheduling.Weekday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fire path entry point (SPEC.md #5, step 2). directBootAware in the manifest because it must
 * run before the first unlock after a reboot (SPEC.md Assumption A8, test T16).
 *
 * goAsync() lets the guarded database check finish on a background dispatcher before Android is
 * free to kill the receiver - a plain onReceive() body must return within a few seconds.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = AlarmScheduler.alarmIdFrom(intent)
        if (alarmId < 0) return

        val pendingResult = goAsync()
        val graph = (context.applicationContext as SilentAlarmApp).graph

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = graph.repository.getById(alarmId)
                if (alarm == null || !alarm.enabled) {
                    // Deleted, or disabled by a race between scheduling and firing: nothing to
                    // fire, and nothing to reschedule (deletion/disabling already cancelled it).
                    return@launch
                }

                val today = LocalDate.now(ZoneId.systemDefault()).dayOfWeek
                if (!Weekday.contains(alarm.daysMask, today)) {
                    // The day mask no longer matches today (e.g. edited between schedule and
                    // fire). Reschedule to the real next occurrence and stop - nothing fires
                    // (SPEC.md #5, step 2).
                    graph.scheduler.schedule(alarm)
                    return@launch
                }

                val serviceIntent = Intent(context, AlarmService::class.java)
                    .setAction(AlarmService.ACTION_FIRE)
                    .putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
                context.startForegroundService(serviceIntent)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
