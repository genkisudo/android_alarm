package com.silentalarm.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.silentalarm.app.data.AlarmEntity
import com.silentalarm.app.fire.AlarmReceiver
import com.silentalarm.app.ui.MainActivity
import com.silentalarm.scheduling.nextOccurrence
import java.time.ZoneId
import java.time.ZonedDateTime

private const val EXTRA_ALARM_ID = "com.silentalarm.app.extra.ALARM_ID"

/**
 * Owns the AlarmManager side of SPEC.md #6 / Assumption A10: only the next occurrence of each
 * alarm is ever scheduled, one PendingIntent per alarm id (keyed by id as the request code),
 * re-created - not appended to - on every mutation, dismiss, boot, and time/timezone change.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(alarm: AlarmEntity, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())) {
        if (!alarm.enabled) {
            cancel(alarm.id)
            return
        }
        val trigger = nextOccurrence(alarm.hour, alarm.minute, alarm.daysMask, now) ?: return

        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // setAlarmClock (not setExactAndAllowWhileIdle) is what makes this exempt from Doze
        // deferral and shows the alarm-clock icon in the status bar (SPEC.md Assumption A9).
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(trigger.toInstant().toEpochMilli(), showIntent),
            pendingIntentFor(alarm.id),
        )
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(pendingIntentFor(alarmId))
    }

    fun rescheduleAll(alarms: List<AlarmEntity>, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())) {
        for (alarm in alarms) schedule(alarm, now)
    }

    private fun pendingIntentFor(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        fun alarmIdFrom(intent: Intent): Long = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
    }
}
