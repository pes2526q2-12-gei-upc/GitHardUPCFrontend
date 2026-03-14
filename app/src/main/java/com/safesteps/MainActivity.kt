package com.safesteps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.safesteps.map.MapLibreScreen
import com.safesteps.ui.theme.SafeStepsTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        setContent {
            SafeStepsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MapLibreScreen()
                }
            }
        }
    }
}