package com.safesteps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

private const val NotificationPermissionRequestCode = 1001

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        initializeEmergencyMessaging(applicationContext)
        requestNotificationPermissionIfNeeded()

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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NotificationPermissionRequestCode
        )
    }
}
