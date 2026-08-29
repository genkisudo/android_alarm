package com.silentalarm.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.silentalarm.app.ui.theme.SilentAlarmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SilentAlarmTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("Silent Alarm")
                }
            }
        }
    }
}
