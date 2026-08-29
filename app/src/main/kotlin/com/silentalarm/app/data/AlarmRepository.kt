package com.silentalarm.app.data

import com.silentalarm.app.scheduling.AlarmScheduler

/**
 * The only path alarms are written through, so no UI action can persist an alarm without also
 * updating AlarmManager (see CLAUDE.md: "Only the next occurrence of each alarm is scheduled").
 */
class AlarmRepository(
    private val dao: AlarmDao,
    private val scheduler: AlarmScheduler,
) {
    fun observeAll() = dao.observeAll()

    suspend fun getById(id: Long): AlarmEntity? = dao.getById(id)

    /** id = 0 creates a new alarm; a non-zero id updates the existing one. */
    suspend fun save(hour: Int, minute: Int, daysMask: Int, enabled: Boolean = true, id: Long = 0): Long {
        require(daysMask != 0) { "An alarm needs at least one day (SPEC.md Assumption A11)." }
        val savedId = if (id == 0L) {
            dao.insert(AlarmEntity(hour = hour, minute = minute, daysMask = daysMask, enabled = enabled))
        } else {
            dao.update(AlarmEntity(id = id, hour = hour, minute = minute, daysMask = daysMask, enabled = enabled))
            id
        }
        scheduler.schedule(AlarmEntity(id = savedId, hour = hour, minute = minute, daysMask = daysMask, enabled = enabled))
        return savedId
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        val alarm = dao.getById(id) ?: return
        if (enabled) scheduler.schedule(alarm) else scheduler.cancel(id)
    }

    suspend fun delete(alarm: AlarmEntity) {
        dao.delete(alarm)
        scheduler.cancel(alarm.id)
    }

    /** Called on app start and from BootReceiver/TimeChangeReceiver (SPEC.md #6). */
    suspend fun rescheduleAll() {
        scheduler.rescheduleAll(dao.getAllOnce())
    }
}
