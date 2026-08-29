package com.silentalarm.app.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.silentalarm.app.data.AlarmEntity
import com.silentalarm.app.data.AlarmRepository
import com.silentalarm.app.ui.formatTime
import com.silentalarm.app.ui.weekdaySummary
import kotlinx.coroutines.launch

/** SPEC.md #4.1 - the app's start destination. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlarmListScreen(
    repository: AlarmRepository,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarms by repository.observeAll().collectAsState(initial = emptyList())
    var pendingDelete by remember { mutableStateOf<AlarmEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAlarm) {
                Icon(Icons.Filled.Add, contentDescription = "Add alarm")
            }
        },
    ) { padding ->
        if (alarms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No alarms yet. Tap + to add one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        timeText = formatTime(context, alarm.hour, alarm.minute),
                        onClick = { onEditAlarm(alarm.id) },
                        onLongClick = { pendingDelete = alarm },
                        onToggle = { enabled -> scope.launch { repository.setEnabled(alarm.id, enabled) } },
                    )
                }
            }
        }
    }

    pendingDelete?.let { alarm ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete alarm?") },
            text = { Text("This removes the ${formatTime(context, alarm.hour, alarm.minute)} alarm.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.delete(alarm) }
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmRow(
    alarm: AlarmEntity,
    timeText: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val contentColor = if (alarm.enabled) {
        LocalContentColor.current
    } else {
        LocalContentColor.current.copy(alpha = 0.4f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(text = timeText, style = MaterialTheme.typography.headlineSmall, color = contentColor)
            Text(text = weekdaySummary(alarm.daysMask), style = MaterialTheme.typography.bodyMedium, color = contentColor)
        }
        Switch(checked = alarm.enabled, onCheckedChange = onToggle)
    }
}
