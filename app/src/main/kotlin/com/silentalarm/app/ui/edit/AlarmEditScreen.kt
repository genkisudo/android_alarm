package com.silentalarm.app.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.silentalarm.app.data.AlarmRepository
import com.silentalarm.scheduling.Weekday
import kotlinx.coroutines.launch
import java.time.LocalTime

private val DAY_TOGGLES = listOf(
    "M" to Weekday.MONDAY,
    "T" to Weekday.TUESDAY,
    "W" to Weekday.WEDNESDAY,
    "T" to Weekday.THURSDAY,
    "F" to Weekday.FRIDAY,
    "S" to Weekday.SATURDAY,
    "S" to Weekday.SUNDAY,
)

/** SPEC.md #4.2. New-alarm defaults: current time rounded up to the next 5 minutes, no days. */
private fun defaultTime(): Pair<Int, Int> {
    val now = LocalTime.now()
    val roundedMinute = ((now.minute / 5) + 1) * 5
    return if (roundedMinute >= 60) (now.hour + 1) % 24 to 0 else now.hour to roundedMinute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    repository: AlarmRepository,
    alarmId: Long?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (defaultHour, defaultMinute) = remember { defaultTime() }

    var daysMask by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = defaultHour,
        initialMinute = defaultMinute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )

    LaunchedEffect(alarmId) {
        if (alarmId != null) {
            repository.getById(alarmId)?.let { alarm ->
                timePickerState.hour = alarm.hour
                timePickerState.minute = alarm.minute
                daysMask = alarm.daysMask
                enabled = alarm.enabled
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (alarmId == null) "New alarm" else "Edit alarm") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (alarmId != null) {
                        TextButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            TimePicker(state = timePickerState)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for ((label, bit) in DAY_TOGGLES) {
                    val selected = daysMask and bit != 0
                    FilterChip(
                        selected = selected,
                        onClick = { daysMask = if (selected) daysMask and bit.inv() else daysMask or bit },
                        label = { Text(label) },
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        repository.save(
                            hour = timePickerState.hour,
                            minute = timePickerState.minute,
                            daysMask = daysMask,
                            enabled = enabled,
                            id = alarmId ?: 0,
                        )
                        onDone()
                    }
                },
                enabled = daysMask != 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }

    if (showDeleteConfirm && alarmId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete alarm?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.getById(alarmId)?.let { repository.delete(it) }
                        onDone()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
