package com.silentalarm.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.silentalarm.app.SilentAlarmApp
import com.silentalarm.app.ui.theme.SilentAlarmTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
