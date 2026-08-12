package com.wellnesscompanion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wellnesscompanion.app.notification.NotificationScheduler
import com.wellnesscompanion.app.ui.navigation.AppNavigation
import com.wellnesscompanion.app.ui.theme.WellnessTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationScheduler.scheduleAll(this)
        setContent {
            WellnessTheme {
                AppNavigation()
            }
        }
    }
}
