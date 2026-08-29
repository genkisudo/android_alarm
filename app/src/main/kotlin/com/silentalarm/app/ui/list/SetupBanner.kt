package com.silentalarm.app.ui.list

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.silentalarm.app.setup.SetupChecks
import com.silentalarm.app.setup.SetupPrefs
import com.silentalarm.app.setup.SetupState

/**
 * SPEC.md #4.1's setup banner: a card inside the list screen, shown only when a readiness
 * check fails. Every action either opens a real settings screen or, per BUILD_PLAN.md Phase 5,
 * falls back to the app's own details page rather than crashing on a missing activity.
 */
@Composable
fun SetupBanner(state: SetupState, onAcknowledgeMiui: () -> Unit) {
    val context = LocalContext.current
    var showMiuiChecklist by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Setup needed for alarms to fire reliably", style = MaterialTheme.typography.titleMedium)

            if (!state.notificationsGranted) {
                SetupRow("Notifications are off - the alarm can still vibrate, but you won't get a Dismiss button in the notification.") {
                    openSettingsSafely(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
            if (!state.fullScreenIntentAllowed) {
                SetupRow("Full-screen alerts aren't allowed - the alarm screen may not show over the lock screen.") {
                    val intent = if (Build.VERSION.SDK_INT >= 34) {
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                    } else {
                        appDetailsIntent(context)
                    }
                    openSettingsSafely(context, intent)
                }
            }
            if (!state.miuiChecklistAcknowledged) {
                SetupRow("This phone's MIUI power management can silently drop alarms unless a few settings are changed by hand.") {
                    showMiuiChecklist = true
                }
            }
        }
    }

    if (showMiuiChecklist) {
        MiuiChecklistDialog(
            onDismiss = { showMiuiChecklist = false },
            onDone = {
                SetupPrefs(context).setMiuiChecklistAcknowledged(true)
                showMiuiChecklist = false
                onAcknowledgeMiui()
            },
        )
    }
}

@Composable
private fun SetupRow(message: String, onFix: () -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onFix) { Text("Fix") }
    }
}

/** SPEC.md #8, condensed to what a checklist dialog can show. */
private data class MiuiChecklistItem(val title: String, val detail: String, val action: ((Context) -> Intent)?)

private val MIUI_CHECKLIST = listOf(
    MiuiChecklistItem(
        "Autostart",
        "Security app -> App Manager -> Permissions -> Autostart -> Silent Alarm -> On.",
        ::appDetailsIntent,
    ),
    MiuiChecklistItem(
        "Battery saver: No restrictions",
        "Settings -> Apps -> Manage apps -> Silent Alarm -> Battery saver -> No restrictions.",
    ) { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
    MiuiChecklistItem(
        "Lock the app in Recents",
        "Open Recents, long-press the Silent Alarm card, tap the padlock.",
        null,
    ),
    MiuiChecklistItem(
        "Other permissions: pop-up windows + show on lock screen",
        "Settings -> Apps -> Manage apps -> Silent Alarm -> Other permissions -> both On.",
        ::appDetailsIntent,
    ),
    MiuiChecklistItem(
        "Do Not Disturb allows alarms",
        "Settings -> Sound & vibration -> Do Not Disturb -> Exceptions -> Alarms -> On.",
    ) { Intent(Settings.ACTION_ZEN_MODE_SETTINGS) },
    MiuiChecklistItem(
        "Vibration is on",
        "Settings -> Sound & vibration -> confirm vibration intensity isn't zero.",
    ) { Intent(Settings.ACTION_SOUND_SETTINGS) },
    MiuiChecklistItem(
        "Automatic date & time is on",
        "Settings -> Additional settings -> Date & time -> Automatic.",
    ) { Intent(Settings.ACTION_DATE_SETTINGS) },
    MiuiChecklistItem(
        "Never force-stop this app",
        "Force-stopping cancels every scheduled alarm until the app is reopened.",
        null,
    ),
)

@Composable
private fun MiuiChecklistDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MIUI setup checklist") },
        text = {
            LazyColumn {
                items(MIUI_CHECKLIST.size) { index ->
                    val item = MIUI_CHECKLIST[index]
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(item.detail, style = MaterialTheme.typography.bodySmall)
                        item.action?.let { makeIntent ->
                            TextButton(onClick = { openSettingsSafely(context, makeIntent(context)) }) {
                                Text("Open settings")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

/** Falls back to the app's own details page rather than crashing on a missing activity. */
private fun openSettingsSafely(context: Context, intent: Intent) {
    val target = if (intent.resolveActivity(context.packageManager) != null) intent else appDetailsIntent(context)
    context.startActivity(target)
}
