package com.silentalarm.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AlarmEntity::class], version = 1, exportSchema = false)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        /**
         * Built on the device-protected storage context, not the default (credential-encrypted)
         * one, so the database is readable before the user unlocks the phone after a reboot
         * (SPEC.md Assumption A8, test T16). Every caller - the app process and every
         * direct-boot-aware receiver/service - must go through this same factory so they see
         * the same file.
         */
        fun create(context: Context): AlarmDatabase {
            val deviceProtectedContext = context.createDeviceProtectedStorageContext()
            return Room.databaseBuilder(deviceProtectedContext, AlarmDatabase::class.java, "alarms.db")
                // SPEC.md #9: a schema change losing a handful of alarm rows isn't a data-loss
                // event worth writing migrations for at this app's size - a deliberate choice.
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
