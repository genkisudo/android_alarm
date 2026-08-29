package com.silentalarm.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** SPEC.md #9 - the entire persisted model. Five fields, nothing else. */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val daysMask: Int,
    val enabled: Boolean = true,
)
