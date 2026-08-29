package com.silentalarm.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.silentalarm.app.SilentAlarmApp
import com.silentalarm.app.ui.edit.AlarmEditScreen
import com.silentalarm.app.ui.list.AlarmListScreen
import com.silentalarm.app.ui.theme.SilentAlarmTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Without this, the firing notification (and its Dismiss action) can't be shown - see the
    // setup banner in Phase 5 for the user-facing readiness check this backs up.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // App-start reschedule (SPEC.md #6 reschedule-trigger list).
        val graph = (application as SilentAlarmApp).graph
        lifecycleScope.launch { graph.repository.rescheduleAll() }

        setContent {
            SilentAlarmTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.List) }
                when (val current = screen) {
                    is Screen.List -> AlarmListScreen(
                        repository = graph.repository,
                        onAddAlarm = { screen = Screen.Edit(null) },
                        onEditAlarm = { id -> screen = Screen.Edit(id) },
                    )
                    is Screen.Edit -> AlarmEditScreen(
                        repository = graph.repository,
                        alarmId = current.alarmId,
                        onDone = { screen = Screen.List },
                    )
                }
            }
        }
    }
}
