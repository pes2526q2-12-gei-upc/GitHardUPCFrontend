package com.safesteps

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.safesteps.ui.theme.SafeStepsTheme
import org.maplibre.android.MapLibre

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)

        enableEdgeToEdge()
        setContent {
            SafeStepsTheme {
                Scaffold(modifier = Modifier
                    .fillMaxSize()
                    .semantics {testTagsAsResourceId = true}
                ) { _ ->
                    SafeStepsApp(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
