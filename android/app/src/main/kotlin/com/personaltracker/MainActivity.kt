package com.personaltracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.personaltracker.data.AuthManager
import com.personaltracker.ui.theme.PersonalTrackerTheme
import com.personaltracker.widget.QuickEntryWidget
import com.personaltracker.widget.WidgetRefreshWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AuthManager.init(applicationContext)

        if (AuthManager.isAuthenticated()) {
            WidgetRefreshWorker.schedule(applicationContext)
        }

        val openNewEntry = intent?.getStringExtra("action") == QuickEntryWidget.ACTION_NEW_ENTRY

        setContent {
            PersonalTrackerTheme {
                PersonalTrackerApp(openNewEntry = openNewEntry)
            }
        }
    }
}
