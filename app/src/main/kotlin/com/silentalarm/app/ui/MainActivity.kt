package com.silentalarm.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.silentalarm.app.SilentAlarmApp
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("Silent Alarm")
                }
            }
        }
    }
}
