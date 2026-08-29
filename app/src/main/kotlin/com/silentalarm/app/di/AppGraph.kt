package com.silentalarm.app.di

import android.content.Context

/**
 * Hand-written singleton dependency graph (SPEC.md Assumption A13: no DI framework at this
 * app's size). Filled in as each build phase adds a real dependency - see AppGraph.kt history.
 */
class AppGraph(private val appContext: Context)
