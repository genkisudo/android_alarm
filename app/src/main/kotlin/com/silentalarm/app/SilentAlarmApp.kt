package com.silentalarm.app

import android.app.Application

class SilentAlarmApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
