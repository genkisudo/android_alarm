package com.silentalarm.app.fire

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.silentalarm.app.ui.theme.SilentAlarmTheme
import java.text.DateFormat
import java.util.Date

/**
 * The system-triggered firing screen (SPEC.md #4.3) - not a screen the user navigates to.
 * Launched by the notification's full-screen intent; shows over the lock screen and turns the
 * screen on via the manifest's showWhenLocked/turnScreenOn attributes.
 *
 * Not swipe- or back-dismissible: the only way off this screen is the Dismiss button.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally does nothing: no back-button dismissal (SPEC.md #4.3).
            }
        })

        setContent {
            SilentAlarmTheme {
                AlarmFiringScreen(onDismiss = ::dismiss)
            }
        }
    }

    private fun dismiss() {
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_DISMISS))
        finish()
    }
}

@Composable
private fun AlarmFiringScreen(onDismiss: () -> Unit) {
    // The current wall-clock time is, by construction, the alarm's fire time - no need to look
    // up which alarm(s) triggered this screen.
    val timeText = remember { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(text = timeText, style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text("Dismiss")
            }
        }
    }
}
