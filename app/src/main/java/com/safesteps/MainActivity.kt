package com.safesteps

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.safesteps.notifications.initializeEmergencyMessaging
import com.safesteps.ui.theme.SafeStepsTheme
import com.safesteps.ui.notifications.ScreenNotificationHost
import org.maplibre.android.MapLibre

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        initializeEmergencyMessaging(this)

        enableEdgeToEdge()
        setContent {
            SafeStepsTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                ) { _ ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        SafeStepsApp(modifier = Modifier.fillMaxSize())
                        ScreenNotificationHost(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
