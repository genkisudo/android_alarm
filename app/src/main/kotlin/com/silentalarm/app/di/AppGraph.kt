package com.silentalarm.app.di

import android.content.Context
import com.silentalarm.app.data.AlarmDatabase
import com.silentalarm.app.data.AlarmRepository
import com.silentalarm.app.scheduling.AlarmScheduler

/**
 * Hand-written singleton dependency graph (SPEC.md Assumption A13: no DI framework at this
 * app's size).
 */
class AppGraph(appContext: Context) {
    private val database = AlarmDatabase.create(appContext)
    val scheduler = AlarmScheduler(appContext)
    val repository = AlarmRepository(database.alarmDao(), scheduler)
}
