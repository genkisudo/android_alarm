package com.silentalarm.app.ui

/**
 * SPEC.md #4/A13: exactly two user-authored screens, hand-navigated with in-memory state
 * (no navigation library) since a single Activity hosting two composables is enough here.
 */
sealed class Screen {
    data object List : Screen()
    data class Edit(val alarmId: Long?) : Screen()
}
