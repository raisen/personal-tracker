package com.personaltracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.personaltracker.data.AuthManager
import com.personaltracker.ui.theme.PersonalTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AuthManager.init(applicationContext)

        setContent {
            PersonalTrackerTheme {
                PersonalTrackerApp()
            }
        }
    }
}
