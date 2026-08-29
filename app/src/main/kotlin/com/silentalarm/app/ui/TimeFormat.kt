package com.silentalarm.app.ui

import android.content.Context
import java.util.Calendar

/** Formats hour/minute using the device's 12/24-hour system setting (SPEC.md #4.1/#4.2). */
fun formatTime(context: Context, hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
}
